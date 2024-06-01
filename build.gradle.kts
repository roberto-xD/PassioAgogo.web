import org.jetbrains.compose.ComposeExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    kotlin("android") apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    afterEvaluate {
        extensions.findByType(ComposeExtension::class.java)?.apply {
            val kotlinGeneration = project.property("kotlin.generation")
            val composeCompilerVersion = project.property("compose.compiler.version.$kotlinGeneration") as String
            kotlinCompilerPlugin.set(composeCompilerVersion)
            val kotlinVersion = project.property("kotlin.version.$kotlinGeneration") as String
            kotlinCompilerPluginArgs.add("suppressKotlinVersionCompatibilityCheck=$kotlinVersion")
        }
    }
}