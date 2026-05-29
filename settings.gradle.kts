pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.3"
}

stonecutter {
    create(rootProject) {
        versions("1.21.1", "1.21.4", "1.21.11")
        vcsVersion = "1.21.4"
    }
}

rootProject.name = "lorefinder"
