pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        maven("https://maven.quiltmc.org/repository/release/") { name = "Quilt" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
    }

    // The NeoForge targets' build plugin, pinned here because their buildscript is
    // applied per node and cannot carry a version of its own. The Forge targets' plugins
    // are pinned the same way: ForgeGradle 7, Forge's own Jar-in-Jar, and Renamer, which
    // turns a jar back to the obfuscated names that Forge runs 1.20.1 under.
    plugins {
        id("net.neoforged.moddev") version "2.0.148"
        id("net.minecraftforge.gradle") version "7.0.40"
        id("net.minecraftforge.jarjar") version "0.2.3"
        id("net.minecraftforge.renamer") version "1.1.7"
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
        // The game versions the mod is built for, from the newest down: a version is added
        // here when the sources compile on it, together with a table in
        // stonecutter.properties.toml. Each is a node on every loader below; the name is
        // the one the TOML table has, the second the game version it is built on.
        val targets = listOf(
            "1.20.1" to "1.20.1",
            "1.20.2" to "1.20.2",
            "1.20.4" to "1.20.4",
            "1.20.6" to "1.20.6",
            "1.21.1" to "1.21.1",
            "1.21.3" to "1.21.3",
            "1.21.4" to "1.21.4",
            "1.21.5" to "1.21.5",
            "1.21.8" to "1.21.8",
            "1.21.10" to "1.21.10",
            "1.21.11" to "1.21.11",
            "26.1.x" to "26.1.2",
            "26.2.x" to "26.2",
            "26.3.x" to "26.3",
        )

        // Fabric targets: named after the game version alone.
        for ((name, minecraft) in targets) version(name, minecraft)

        // Quilt targets: the same game versions, the same sources, another build script.
        // The -quilt suffix is what stonecutter.gradle.kts reads the loader from.
        for ((name, minecraft) in targets) version("$name-quilt", minecraft).buildscript("build.quilt.gradle.kts")

        // NeoForge targets, built by ModDevGradle: the -neoforge suffix is read the same way.
        // None before 1.20.6: 1.20.1's NeoForge is Forge 47 under another name, and 1.20.2
        // and 1.20.4 had one with the older metadata format.
        val noNeoForge = setOf("1.20.1", "1.20.2", "1.20.4")
        for ((name, minecraft) in targets.filter { it.first !in noNeoForge }) {
            version("$name-neoforge", minecraft).buildscript("build.neoforge.gradle.kts")
        }

        // Forge targets, built by ForgeGradle. "-neoforge" does not end in "-forge", so the
        // two suffixes never answer to each other's name.
        for ((name, minecraft) in targets) version("$name-forge", minecraft).buildscript("build.forge.gradle.kts")

        vcsVersion = "1.21.11"
    }
}

rootProject.name = "living-horizon"
