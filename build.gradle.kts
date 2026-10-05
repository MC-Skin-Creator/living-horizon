plugins {
    id("dev.kikugie.loom-back-compat")
}

// mod version and Minecraft version stay separate: farfarplayer-0.1.0+mc1.21.11.jar
version = "${property("mod.version")}+mc${sc.current.version}"
base.archivesName = property("mod.id") as String

repositories {
    maven("https://maven.terraformersmc.com/releases") {
        name = "TerraformersMC"
        content { includeGroup("com.terraformersmc") }
    }
}

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

dependencies {
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, sc.properties["deps.fabric_api"]))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    fapi(
        "fabric-api-base",
        "fabric-lifecycle-events-v1",
        "fabric-key-binding-api-v1",
        "fabric-command-api-v2",
    )
    // Only its API, for the settings button; nothing of it ships, nothing requires it.
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}") { isTransitive = false }

    testImplementation(platform("org.junit:junit-bom:${property("deps.junit")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    // Opens the locator bar's waypoint classes, which the client never reads by itself.
    accessWidenerPath = rootProject.file("src/main/resources/farfarplayer.accesswidener")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
}

tasks {
    test {
        useJUnitPlatform()
        // DatapackTest compiles the data pack, which lives outside the source set.
        inputs.dir(rootProject.file("datapack"))
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }

    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
            register("loader", "mod.loader_min")
            put("java", requiredJava.majorVersion)
        }
        inputs.property("java", requiredJava.majorVersion)
        filesMatching(listOf("fabric.mod.json", "farfarplayer.mixins.json")) { expand(props) }
    }

    // The companion data pack, zipped next to the jar. Its pack format belongs to one
    // Minecraft version, like the jar.
    val datapack = register<Zip>("datapackZip") {
        group = "build"
        description = "Zips the companion data pack into build/libs/{mod version}/"
        from(rootProject.file("datapack"))
        archiveFileName = "farfarplayer-datapack-${project.property("mod.version")}+mc${sc.current.version}.zip"
        destinationDirectory = rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"
        dependsOn(datapack)
        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
