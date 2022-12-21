import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.22"
    `java-gradle-plugin`
    `maven-publish`
}

gradlePlugin {
    plugins {
        create("multi-jdk") {
            id = "dev.fastmc.multi-jdk"
            displayName = "multi-jdk"
            description = "Compile for multiple JDKs"
            implementationClass = "dev.fastmc.multijdk.MultiJdkPlugin"
        }
    }
}

group = "dev.fastmc"
version = "1.0-SNAPSHOT"

java {
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.22")
}