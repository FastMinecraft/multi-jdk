package dev.fastmc.multijdk

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject

@Suppress("DuplicatedCode", "unused")
class MultiJdkPlugin @Inject constructor(
    private val softwareComponentFactory: SoftwareComponentFactory
) : Plugin<Project> {
    override fun apply(rootProject: Project) {
        val multiJdkComponent = softwareComponentFactory.adhoc("multi-jdk")
        rootProject.components.add(multiJdkComponent)

        rootProject.pluginManager.withPlugin("java") {
            rootProject.subprojects { subproject ->
                subproject.apply {
                    it.plugin("java")
                }

                subproject.extensions.findByType(JavaPluginExtension::class.java)
                    ?.toolchain?.languageVersion?.set(subproject.javaVersion)
            }
        }

        rootProject.pluginManager.withPlugin("kotlin") {
            rootProject.subprojects { subproject ->
                subproject.apply {
                    it.plugin("kotlin")
                }
            }
        }

        rootProject.afterEvaluate {
            rootProject.pluginManager.withPlugin("java") {
                rootProject.tasks.withType(JavaCompile::class.java) { task ->
                    task.enabled = false
                }
            }
            rootProject.pluginManager.withPlugin("kotlin") {
                rootProject.tasks.withType(KotlinCompile::class.java) { task ->
                    task.enabled = false
                }
            }

            rootProject.subprojects { subproject ->
                subproject.group = rootProject.group
                subproject.version = rootProject.version
                subproject.repositories.addAll(rootProject.repositories)

                rootProject.sourceSets.forEach { rootSourceSet ->
                    rootProject.tasks.named(rootSourceSet.classesTaskName).configure {
                        it.enabled = false
                    }

                    subproject.sourceSets.maybeCreate(rootSourceSet.name).let { sourceSet ->
                        fun extendConfigurationFrom(configurationNameFunction: (SourceSet) -> String) {
                            val root = rootProject.configurations.findByName(configurationNameFunction(rootSourceSet))
                            val sub = subproject.configurations.findByName(configurationNameFunction(sourceSet))
                            if (root != null && sub != null) {
                                sub.dependencies.addAll(root.dependencies)
                            }
                        }

                        extendConfigurationFrom(SourceSet::getCompileOnlyConfigurationName)
                        extendConfigurationFrom(SourceSet::getCompileOnlyApiConfigurationName)
                        extendConfigurationFrom(SourceSet::getCompileClasspathConfigurationName)
                        extendConfigurationFrom(SourceSet::getAnnotationProcessorConfigurationName)
                        extendConfigurationFrom(SourceSet::getApiConfigurationName)
                        extendConfigurationFrom(SourceSet::getImplementationConfigurationName)
                        extendConfigurationFrom(SourceSet::getApiElementsConfigurationName)
                        extendConfigurationFrom(SourceSet::getRuntimeOnlyConfigurationName)
                        extendConfigurationFrom(SourceSet::getRuntimeClasspathConfigurationName)
                        extendConfigurationFrom(SourceSet::getRuntimeElementsConfigurationName)
                        extendConfigurationFrom(SourceSet::getJavadocElementsConfigurationName)
                        extendConfigurationFrom(SourceSet::getSourcesElementsConfigurationName)

                        sourceSet.compileClasspath += rootSourceSet.output

                        configureProcessResources(subproject, sourceSet, rootProject, rootSourceSet)
                        configureJavaCompile(subproject, sourceSet, rootProject, rootSourceSet)
                        configureKotlinCompile(rootProject, rootSourceSet, subproject, sourceSet)
                    }
                }

                configureJarTasks(subproject, rootProject)

                val apiElements = copyConfiguration(rootProject, subproject, "apiElements")
                multiJdkComponent.addVariantsFromConfiguration(apiElements) {
                    it.mapToMavenScope("compile")
                }
                val runtimeElements = copyConfiguration(rootProject, subproject, "runtimeElements")
                multiJdkComponent.addVariantsFromConfiguration(runtimeElements) {
                    it.mapToMavenScope("runtime")
                }
            }

            multiJdkComponent.addVariantsFromConfiguration(rootProject.configurations.getByName("sourcesElements")) {
                it.mapToMavenScope("runtime")
            }
        }
    }

    private fun copyConfiguration(
        rootProject: Project,
        subproject: Project,
        name: String,
        classifier: String = if (subproject.javaVersion.asInt() == 8) "" else "Java${subproject.javaVersion.asInt()}",
    ): Configuration {
        val root = rootProject.configurations.maybeCreate("$name$classifier")
        val sub = subproject.configurations.getByName(name)
        root.artifacts.clear()
        root.artifacts.addAll(sub.artifacts)
        root.extendsFrom(sub)
        sub.attributes.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST")
            root.attributes.attribute(key as Attribute<Any>, sub.attributes.getAttribute(key)!!)
        }
        root.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, subproject.javaVersion.asInt())
        return root
    }

    private fun configureProcessResources(
        subproject: Project,
        sourceSet: SourceSet,
        rootProject: Project,
        rootSourceSet: SourceSet
    ) {
        subproject.tasks.named(
            sourceSet.processResourcesTaskName,
            Copy::class.java
        ).configure {
            it.from(rootProject.tasks.named(rootSourceSet.processResourcesTaskName))
        }
    }

    private fun configureJavaCompile(
        subproject: Project,
        sourceSet: SourceSet,
        rootProject: Project,
        rootSourceSet: SourceSet
    ) {
        subproject.pluginManager.withPlugin("java") {
            subproject.extensions.findByType(JavaPluginExtension::class.java)
                ?.toolchain?.languageVersion?.set(subproject.javaVersion)

            subproject.tasks.named(
                sourceSet.compileJavaTaskName,
                JavaCompile::class.java
            ).configure {
                it.options.let { sub ->
                    rootProject.tasks.named(
                        rootSourceSet.compileJavaTaskName,
                        JavaCompile::class.java
                    ).get().options.let { root ->
                        sub.encoding = root.encoding
                        sub.compilerArgs = root.compilerArgs
                        sub.isIncremental = root.isIncremental
                        sub.isDeprecation = root.isDeprecation
                        sub.isWarnings = root.isWarnings
                        sub.isDebug = root.isDebug
                        sub.isListFiles = root.isListFiles
                        sub.isFailOnError = root.isFailOnError
                    }
                }
                it.source(rootSourceSet.java)
            }
        }
    }

    private fun configureKotlinCompile(
        rootProject: Project,
        rootSourceSet: SourceSet,
        subproject: Project,
        sourceSet: SourceSet
    ) {
        subproject.pluginManager.withPlugin("kotlin") {
            subproject.tasks.named(
                sourceSet.getCompileTaskName("kotlin"),
                KotlinCompile::class.java
            ).configure {
                it.kotlinOptions.let { sub ->
                    rootProject.tasks.named(
                        rootSourceSet.getCompileTaskName("kotlin"),
                        KotlinCompile::class.java
                    ).get().kotlinOptions.let { root ->
                        sub.allWarningsAsErrors = root.allWarningsAsErrors
                        sub.apiVersion = root.apiVersion
                        sub.freeCompilerArgs = root.freeCompilerArgs
                        sub.javaParameters = root.javaParameters
                        sub.languageVersion = root.languageVersion
                        sub.moduleName = root.moduleName
                        sub.noJdk = root.noJdk
                        sub.suppressWarnings = root.suppressWarnings
                        sub.useK2 = root.useK2
                        sub.useOldBackend = root.useOldBackend
                        sub.verbose = root.verbose
                    }
                }
                it.source(rootSourceSet.kotlin)
                it.kotlinOptions.jvmTarget = subproject.javaVersion.fullJavaVersion
            }
        }
    }

    private fun configureJarTasks(subproject: Project, rootProject: Project) {
        subproject.tasks.withType(Jar::class.java) { task ->
            task.archiveBaseName.set(rootProject.name)
            task.archiveClassifier.set(subproject.name)
        }
    }

    private val SourceSet.kotlin
        get() = this.extensions.getByName("kotlin") as SourceDirectorySet

    private val Project.sourceSets: SourceSetContainer
        get() = this.extensions.getByType(SourceSetContainer::class.java)

    private val javaNameRegex = "java(\\d+)".toRegex()

    private val Project.javaVersion: JavaLanguageVersion
        get() {
            var result: JavaLanguageVersion? = null

            javaNameRegex.find(name)?.let {
                result = JavaLanguageVersion.of(it.groupValues[1].toInt())
            }

            return result ?: JavaLanguageVersion.of(8)
        }

    private val JavaLanguageVersion.fullJavaVersion: String
        get() = if (this <= JavaLanguageVersion.of(8)) "1.$this" else this.toString()
}