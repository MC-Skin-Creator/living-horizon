pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks the Loom variant matching each Minecraft version (remap below 26, plain from 26).
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Only 1.21.11 for now. Adding a version: a node here, a table in
        // stonecutter.properties.toml, and requiredJava in build.gradle.kts.
        versions("1.21.11")
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "far-far-player"
