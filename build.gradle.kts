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
    kotlin("plugin.spring") version "1.8.22"
    id("org.springframework.boot") version "3.1.4"
    id("io.spring.dependency-management") version "1.1.3"
}

allprojects {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo1.maven.org/maven2/")
        maven("https://mvnrepository.com/artifact/")
        maven ("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}

