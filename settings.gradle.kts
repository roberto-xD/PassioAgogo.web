rootProject.name = "PassionAgogo"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {

        val kotlinGeneration = extra["kotlin.generation"] as String
        val kotlinVersion = extra["kotlin.version.$kotlinGeneration"] as String
        val agpVersion = extra["agp.version"] as String
        val composeVersion = extra["compose.wasm.version.$kotlinGeneration"] as String

        kotlin("jvm").version(kotlinVersion)
        kotlin("multiplatform").version(kotlinVersion)
        kotlin("plugin.serialization").version(kotlinVersion)
        kotlin("android").version(kotlinVersion)
        id("com.android.base").version(agpVersion)
        id("com.android.application").version(agpVersion)
        id("com.android.library").version(agpVersion)
        id("org.jetbrains.compose").version(composeVersion)
//        id("io.insert-koin").version("3.4.1")
//        id("io.ktor").version("2.3.11")

        kotlin("plugin.spring") version "1.8.22"
        id("org.springframework.boot") version "3.1.4"
        id("io.spring.dependency-management") version "1.1.3"
    }

}

include(":composeApp")