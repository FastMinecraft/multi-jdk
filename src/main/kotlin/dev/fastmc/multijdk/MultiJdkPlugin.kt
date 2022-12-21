package dev.fastmc.multijdk

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class MultiJdkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("multiJdk", MultiJdkExtension::class.java)
    }
}