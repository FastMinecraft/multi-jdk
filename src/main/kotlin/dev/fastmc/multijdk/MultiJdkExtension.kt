package dev.fastmc.multijdk

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject

@Suppress("DuplicatedCode", "unused")
abstract class MultiJdkExtension @Inject constructor(private val component: AdhocComponentWithVariants) {
    @get:Inject
    protected abstract val project: Project

    private val sourceSets0 = mutableMapOf<JavaLanguageVersion, SourceSet>()

    lateinit var base: SourceSet; private set
    val sourceSets: Map<JavaLanguageVersion, SourceSet> get() = sourceSets0

    fun baseJavaVersion(version: JavaLanguageVersion) {
        base = getOrRegister(version).apply {
            project.afterEvaluate {
                it.sourceSets.getByName("main").compileClasspath += this.compileClasspath
            }
        }
    }

    fun newJavaVersion(version: JavaLanguageVersion) {
        getOrRegister(version)
    }

    private fun getOrRegister(version: JavaLanguageVersion): SourceSet {
        var sourceSet = sourceSets0[version]
        if (sourceSet == null) {
            sourceSet = project.sourceSets.create(version.javaName).configureForJavaVersion(version)
            sourceSets0[version] = sourceSet

            project.java.registerFeature(version.javaName) { f ->
                project.pluginManager.withPlugin("maven-publish") {
                    project.publishing.publications.withType(MavenPublication::class.java).configureEach {
                        it.suppressPomMetadataWarningsFor("${version.javaName}ApiElements")
                        it.suppressPomMetadataWarningsFor("${version.javaName}ApiElementsClasses")
                        it.suppressPomMetadataWarningsFor("${version.javaName}RuntimeElements")
                    }
                }
                f.capability(
                    project.group.toString(),
                    project.name,
                    project.version.toString()
                )
                f.usingSourceSet(sourceSet)
            }

            configureComponent(version)
        }

        return sourceSet
    }

    private fun configureComponent(version: JavaLanguageVersion) {
        val apiElements = project.configurations.getByName("${version.javaName}ApiElements").attributes { sub ->
            project.configurations.getByName("apiElements").attributes.let { root ->
                root.keySet().forEach {
                    if (it.name == "org.gradle.jvm.version") return@forEach
                    @Suppress("UNCHECKED_CAST")
                    sub.attribute(it as Attribute<Any>, root.getAttribute(it)!!)
                }
            }
        }
        component.addVariantsFromConfiguration(apiElements) {
            it.mapToMavenScope("compile")
        }
        val runtimeElement =
            project.configurations.getByName("${version.javaName}RuntimeElements").attributes { sub ->
                project.configurations.getByName("runtimeElements").attributes.let { root ->
                    root.keySet().forEach {
                        if (it.name == "org.gradle.jvm.version") return@forEach
                        @Suppress("UNCHECKED_CAST")
                        sub.attribute(it as Attribute<Any>, root.getAttribute(it)!!)
                    }
                }
            }
        component.addVariantsFromConfiguration(runtimeElement) {
            it.mapToMavenScope("runtime")
        }
    }

    private fun SourceSet.configureForJavaVersion(version: JavaLanguageVersion): SourceSet {
        project.afterEvaluate {
            val jarTask = project.tasks.named(jarTaskName, Jar::class.java) {
                it.archiveClassifier.set(version.javaName)
            }
            project.artifacts {
                it.add("archives", jarTask)
            }
        }

        val mainSourceSet = project.sourceSets.getByName("main")

        if (this != mainSourceSet) {
            project.afterEvaluate {
                fun extendConfigurationFrom(configurationNameFunction: (SourceSet) -> String) {
                    val root = it.configurations.findByName(configurationNameFunction(mainSourceSet))
                    val sub = it.configurations.findByName(configurationNameFunction(this))
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
            }

            resources.srcDirs(mainSourceSet.resources.srcDirs)
            java.srcDirs(mainSourceSet.java.srcDirs)
            kotlin.srcDirs(mainSourceSet.kotlin.srcDirs)
        }

        configureJavaCompile(version, mainSourceSet)
        configureKotlinCompile(version, mainSourceSet)

        return this
    }

    private fun SourceSet.configureJavaCompile(
        version: JavaLanguageVersion,
        mainSourceSet: SourceSet
    ) {
        project.tasks.named(compileJavaTaskName, JavaCompile::class.java) { javaCompile ->
            javaCompile.javaCompiler.set(project.javaToolchains.compilerFor { it.languageVersion.set(version) })
            javaCompile.options.let { sub ->
                if (this != mainSourceSet) {
                    project.tasks.named(mainSourceSet.compileJavaTaskName, JavaCompile::class.java)
                        .get().options.let { root ->
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
                javaCompile.sourceCompatibility = version.fullJavaVersion
                javaCompile.targetCompatibility = version.fullJavaVersion
            }
        }
    }

    private fun SourceSet.configureKotlinCompile(
        version: JavaLanguageVersion,
        mainSourceSet: SourceSet
    ) {
        project.tasks.named(
            getCompileTaskName("kotlin"),
            KotlinCompile::class.java
        ) { kotlinCompile ->
            kotlinCompile.kotlinJavaToolchain.toolchain.use(project.javaToolchains.launcherFor { it.languageVersion.set(version) })
            kotlinCompile.kotlinOptions.let { sub ->
                if (this != mainSourceSet) {
                    project.tasks.named(
                        mainSourceSet.getCompileTaskName("kotlin"),
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
                sub.jvmTarget = version.fullJavaVersion
            }
        }
    }

    private val SourceSet.kotlin
        get() = this.extensions.getByName("kotlin") as SourceDirectorySet

    private val Project.java
        get() = this.extensions.getByType(JavaPluginExtension::class.java)

    private val Project.sourceSets
        get() = this.extensions.getByType(SourceSetContainer::class.java)

    private val Project.javaToolchains
        get() = this.extensions.getByType(JavaToolchainService::class.java)

    private val Project.publishing
        get() = this.extensions.getByType(PublishingExtension::class.java)

    private fun Project.javaToolchains(configure: Action<JavaToolchainService>) {
        this.extensions.configure("javaToolchains", configure)
    }

    private val JavaLanguageVersion.fullJavaVersion: String
        get() = if (this <= JavaLanguageVersion.of(8)) "1.$this" else this.toString()

    private val JavaLanguageVersion.javaName: String
        get() = "java${this.asInt()}"
}