// What every target works out the same way, whichever loader builds it, so that the four
// build scripts only say what is theirs. Applied by each of them after it has set
// `extra["lhGame"]` to the game version it builds: the Java level, the data pack's
// pack.mcmeta, the access widener and access transformer, and the list of mixins.
//
// Everything is written into the build folder at configuration time; the scripts and the
// tests read it from there.
val game = extra["lhGame"] as String
val unobfuscated = extra["lhUnobfuscated"] as Boolean

// "26.1.2" against "26.1": numeric, part by part, a missing part being zero.
fun atLeast(version: String): Boolean {
    val a = game.split(".").map { it.toInt() }
    val b = version.split(".").map { it.toInt() }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return true
}
fun atMost(version: String): Boolean = !atLeast(version) || game == version

// Mojang's requirement per game version, not a preference of ours.
extra["lhJava"] = when {
    atLeast("26.1") -> JavaVersion.VERSION_25
    atLeast("1.20.5") -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

// The companion data pack's pack.mcmeta, which names the data pack format of one game
// version, from the target's `mod.datapack_format`. The pack's own folder keeps the file of
// the version it is developed on. From data pack format 88 (1.21.9) the file gives a range,
// min_format and max_format; before, a single pack_format.
run {
    val format: Int = property("mod.datapack_format").toString().toInt()
    val description = "Living Horizon: shares every player's position with the clients that run the mod"
    val mcmeta: File = layout.buildDirectory.file("generated/datapack/pack.mcmeta").get().asFile
    mcmeta.parentFile.mkdirs()
    mcmeta.writeText(
        if (format >= 88) {
            "{\n  \"pack\": {\n    \"description\": \"$description\",\n    \"min_format\": $format,\n    \"max_format\": $format\n  }\n}\n"
        } else {
            "{\n  \"pack\": {\n    \"description\": \"$description\",\n    \"pack_format\": $format\n  }\n}\n"
        }
    )
    extra["lhMcmeta"] = mcmeta

    // From 26.3 a predicate names its condition under "type", where it said "condition".
    // The pack's own folder keeps the older form; this version's copy of each predicate
    // is written next to pack.mcmeta, and replaces the pack's in the zip and the tests.
    val overrides = mutableListOf<String>()
    if (atLeast("26.3")) {
        val pack = rootProject.file("datapack")
        pack.walkTopDown().filter { it.isFile && it.extension == "json" && it.parentFile.name == "predicate" }.forEach { file ->
            val path = file.relativeTo(pack).invariantSeparatorsPath
            val copy = mcmeta.parentFile.resolve(path)
            copy.parentFile.mkdirs()
            copy.writeText(file.readText().replace(Regex("\"condition\"(\\s*):"), "\"type\"$1:"))
            overrides += path
        }
    }

    // Mob kinds the pack names that this game version does not have yet: a selector or a
    // tag naming an unknown entity type fails to load. This version's copy of the kind
    // function and of the tag leaves them out; the kinds keep their numbers.
    val pack = rootProject.file("datapack")
    val tagPath = "data/livinghorizon/tags/entity_type/remembered.json"
    val kindPath = "data/livinghorizon/function/mob_kind.mcfunction"
    val types = Regex("\"(minecraft:[a-z_]+)\"").findAll(pack.resolve(tagPath).readText()).map { it.groupValues[1] }.toList()
    fun since(type: String): String? = when {
        type == "minecraft:happy_ghast" -> "1.21.6"
        type.startsWith("minecraft:pale_oak_") -> "1.21.4"
        // One entity type per wood from 1.21.2; before, a single boat with a variant.
        type.endsWith("_boat") || type.endsWith("_raft") -> "1.21.2"
        type == "minecraft:armadillo" -> "1.20.5"
        else -> null
    }
    val missing = types.filter { type -> since(type)?.let { !atLeast(it) } ?: false }
    if (missing.isNotEmpty()) {
        val tag = mcmeta.parentFile.resolve(tagPath)
        tag.parentFile.mkdirs()
        tag.writeText("{\n  \"values\": [\n" + types.filter { it !in missing }.joinToString(",\n") { "    \"$it\"" } + "\n  ]\n}\n")
        val kinds = mcmeta.parentFile.resolve(kindPath)
        kinds.parentFile.mkdirs()
        kinds.writeText(pack.resolve(kindPath).readLines().filter { line -> missing.none { "type=$it]" in line } }
            .joinToString("\n") + "\n")
        overrides += tagPath
        overrides += kindPath
    }
    extra["lhDatapackOverrides"] = overrides
    tasks.withType<Test>().configureEach {
        inputs.dir(mcmeta.parentFile)
        systemProperty("livinghorizon.mcmeta", mcmeta.absolutePath)
        systemProperty("livinghorizon.datapackOverrides", mcmeta.parentFile.absolutePath)
        systemProperty("livinghorizon.missingTypes", missing.joinToString(","))
    }
}

// The blocks of the access widener's source file that exist on this game version: a block
// whose first line is "# @since X" and/or "# @until X" is kept only on the versions it names,
// both ends included. Blocks are separated by a blank line.
val widenerBlocks: List<String> = run {
    val since = Regex("""^# @since (\S+)""")
    val until = Regex("""^# @until (\S+)""")
    rootProject.file("src/main/resources/livinghorizon.accesswidener").readText()
        .split(Regex("\n\\s*\n")).filter { block ->
            val lines = block.lines()
            val from = lines.firstNotNullOfOrNull { since.find(it)?.groupValues?.get(1) }
            val to = lines.firstNotNullOfOrNull { until.find(it)?.groupValues?.get(1) }
            (from == null || atLeast(from)) && (to == null || atMost(to))
        }
}

// The access widener Loom reads (Fabric and Quilt): its namespace is named, or official
// where the game is not obfuscated.
run {
    val file: File = layout.buildDirectory.file("generated/accesswidener/livinghorizon.accesswidener").get().asFile
    file.parentFile.mkdirs()
    file.writeText(widenerBlocks.joinToString("\n\n")
        .replace("\${namespace}", if (unobfuscated) "official" else "named").trimEnd() + "\n")
    extra["lhAccessWidener"] = file
}

// The access transformer NeoForge and Forge read: their format for the same thing, in the
// names of the game they compile against, which are Mojang's.
run {
    val file: File = layout.buildDirectory.file("generated/accesstransformer/META-INF/accesstransformer.cfg").get().asFile
    val lines = widenerBlocks.flatMap { it.lines() }.mapNotNull { line ->
        val part = line.trim().split(" ")
        fun name(internal: String) = internal.replace('/', '.')
        when {
            part.size == 3 && part[0] == "accessible" && part[1] == "class" -> "public ${name(part[2])}"
            part.size == 5 && part[0] == "accessible" && part[1] == "field" -> "public ${name(part[2])} ${part[3]}"
            part.size == 5 && part[0] == "accessible" && part[1] == "method" -> "public ${name(part[2])} ${part[3]}${part[4]}"
            else -> null
        }
    }
    file.parentFile.mkdirs()
    file.writeText(lines.joinToString("\n") + "\n")
    extra["lhAccessTransformer"] = file
}

// The mixins this game version has something to hook into, written into the mixin config.
extra["lhMixins"] = buildList {
    // The F3 screen's entries a mod can add came in 1.21.9.
    if (atLeast("1.21.9")) {
        add("DebugScreenEntriesMixin")
        add("DebugScreenEntryListMixin")
    }
    add("EntityMixin")
    add("EntityRenderDispatcherMixin")
    // The far plane: the game renderer's before 26.1, the camera's from there.
    add(if (atLeast("26.1")) "CameraMixin" else "GameRendererMixin")
    add("GuiMixin")
    // The feature renderers, and the GUI pass the impostors are baked in, came in 1.21.9.
    if (atLeast("1.21.9")) {
        add("FeatureRenderDispatcherMixin")
        add("GuiRendererMixin")
        add("ModelFeatureRendererMixin")
    }
    add("LevelRendererMixin")
    // Before 1.21.2 a figure sits only when it rides: the render states carry the pose after.
    if (!atLeast("1.21.2")) add("LivingEntityRendererMixin")
    // The world's extraction left the level renderer for a class of its own in 26.2.
    if (atLeast("26.2")) add("LevelExtractorMixin")
    add("ModelPartMixin")
    // Before 1.20.5 the mod's screens have a list of their own, which places its rows itself.
    if (atLeast("1.20.5")) add("OptionsListEntryMixin")
    add("TextureManagerMixin")
    add("VoxyRenderSystemMixin")
}.sorted().joinToString(",\n    ") { "\"$it\"" }
