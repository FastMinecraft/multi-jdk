plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    id("dev.fastmc.maven-repo").version("1.0.0")
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
version = "1.1-SNAPSHOT"

java {
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    val kotlinVersion: String by project
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += listOf(
                "-Xbackend-threads=0"
            )
        }
    }
}