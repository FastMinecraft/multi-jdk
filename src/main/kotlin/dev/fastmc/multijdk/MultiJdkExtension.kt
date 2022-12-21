package dev.fastmc.multijdk

import org.gradle.api.Action
import org.gradle.api.Project
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
abstract class MultiJdkExtension {
    @get:Inject
    protected abstract val project: Project

    fun defaultJavaVersion(version: JavaLanguageVersion) {
        project.sourceSets.getByName("main").configureForJavaVersion(version)
    }

    fun newJavaVersion(version: JavaLanguageVersion) {
        val javaVersionName = "java${version.asInt()}"
        project.java.registerFeature(javaVersionName) { feature ->
            project.pluginManager.withPlugin("maven-publish") {
                project.publishing.publications.withType(MavenPublication::class.java).configureEach {
                    it.suppressPomMetadataWarningsFor("${javaVersionName}ApiElements")
                    it.suppressPomMetadataWarningsFor("${javaVersionName}RuntimeElements")
                }
            }
            feature.capability(
                project.group.toString(),
                "${project.name}-$javaVersionName",
                project.version.toString()
            )
            feature.usingSourceSet(project.sourceSets.create(javaVersionName).configureForJavaVersion(version))
        }
    }

    private fun SourceSet.configureForJavaVersion(javaVersion: JavaLanguageVersion): SourceSet {
        val fullJavaVersion =
            if (javaVersion.asInt() <= 8) "1.${javaVersion.asInt()}" else javaVersion.asInt().toString()
        project.afterEvaluate {
            val jarTask = project.tasks.named(jarTaskName, Jar::class.java) {
                it.archiveClassifier.set("java${javaVersion.asInt()}")
            }
            project.artifacts {
                it.add("archives", jarTask)
            }
        }

        val mainSourceSet = project.sourceSets.getByName("main")

        if (this != mainSourceSet) {
            compileClasspath += mainSourceSet.compileClasspath

            resources.srcDirs(mainSourceSet.resources.srcDirs)
            java.srcDirs(mainSourceSet.java.srcDirs)
            kotlin.srcDirs(mainSourceSet.kotlin.srcDirs)
        }

        project.tasks.named(compileJavaTaskName, JavaCompile::class.java) { javaCompile ->
            javaCompile.javaCompiler.set(project.javaToolchains.compilerFor { it.languageVersion.set(javaVersion) })
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
                javaCompile.sourceCompatibility = fullJavaVersion
                javaCompile.targetCompatibility = fullJavaVersion
            }
        }

        project.tasks.named(
            getCompileTaskName("kotlin"),
            KotlinCompile::class.java
        ) { kotlinCompile ->
            kotlinCompile.kotlinJavaToolchain.toolchain.use(project.javaToolchains.launcherFor { it.languageVersion.set(javaVersion) })
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
                sub.jvmTarget = fullJavaVersion
            }
        }

        return this
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
}