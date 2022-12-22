package dev.fastmc.multijdk

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import javax.inject.Inject

@Suppress("unused")
abstract class MultiJdkPlugin : Plugin<Project> {
    @get:Inject
    abstract val softwareComponentFactory: SoftwareComponentFactory

    override fun apply(project: Project) {
        val component = softwareComponentFactory.adhoc("multi-jdk")
        project.components.add(component)
        project.afterEvaluate {
            project.configurations.findByName("sourcesElements")?.let { sourcesElements ->
                component.addVariantsFromConfiguration(sourcesElements) {
                    it.mapToMavenScope("runtime")
                }
            }
        }
        project.tasks.named("compileJava").configure {
            it.enabled = false
        }
        project.tasks.named("compileKotlin").configure {
            it.enabled = false
        }
        project.tasks.named("jar").configure {
            it.enabled = false
        }
        project.extensions.create("multiJdk", MultiJdkExtension::class.java, component)
    }
}