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
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.22")
}