plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "com.embedize"
version = "2.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.onarandombox.com/content/groups/public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.7.3")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.14")
    compileOnly("me.clip:placeholderapi:2.12.3")

    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

tasks.test {
    useJUnitPlatform()
}

val structureDatapackDir = layout.buildDirectory.dir(
    when {
        file("build/embedize-structure-packs/index.json").isFile -> "embedize-structure-packs"
        // Windows lock fallback when the default tree cannot be cleared/rebuilt.
        file("build/esp-rebuild/index.json").isFile -> "esp-rebuild"
        else -> "embedize-structure-packs"
    }
)

tasks.register("buildStructureDatapack") {
    description = "Build one structure datapack per .ref/datapacks source (+ bridge)"
    group = "build"
    // Script clears/rewrites the whole tree; Windows AV + Gradle output hashing races.
    doNotTrackState("structure datapack tree is fully rebuilt each run")
    outputs.dir(structureDatapackDir)
    inputs.files(
        fileTree(".ref/datapacks"),
        file("scripts/build_structure_datapack.py"),
    ).optional()

    doLast {
        val outRoot = structureDatapackDir.get().asFile
        val script = file("scripts/build_structure_datapack.py")
        require(script.isFile) { "Missing ${script.path}" }
        providers.exec {
            commandLine("python", script.absolutePath, outRoot.absolutePath)
        }.result.get().assertNormalExitValue()
        require(File(outRoot, "index.json").isFile && File(outRoot, "catalog.json").isFile) {
            "structure datapack build failed — expected index.json and catalog.json"
        }
        val bridge = File(outRoot, "00_embedize_bridge/pack.mcmeta")
        require(bridge.isFile) { "structure datapack build failed — missing bridge pack.mcmeta" }
        val realNbt = outRoot.walkTopDown().count { it.isFile && it.extension.equals("nbt", true) }
        require(realNbt > 0) {
            "No .nbt structures found. Place packs under .ref/datapacks/"
        }
        logger.lifecycle("buildStructureDatapack: $realNbt templates -> ${outRoot.absolutePath}")
    }
}

// Keep old task name as an alias so docs/muscle-memory still work.
tasks.register("extractStructureLibrary") {
    dependsOn("buildStructureDatapack")
    description = "Alias for buildStructureDatapack"
    group = "build"
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(props)
    }
}

tasks.jar {
    description = "Embedize plugin jar including bundled structure datapacks"
    archiveBaseName.set("Embedize")
    dependsOn("buildStructureDatapack")
    from(structureDatapackDir) {
        into("embedize-structure-packs")
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Alias for muscle-memory / old scripts — does not write a second jar.
tasks.register("fullJar") {
    description = "Alias of jar (structure packs are always included; no -full classifier)"
    group = "build"
    dependsOn(tasks.jar)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.runServer {
    minecraftVersion("1.21.11")
    // Single artifact: tasks.jar includes structure packs.
    dependsOn(tasks.jar)
    pluginJars.setFrom(tasks.jar.flatMap { it.archiveFile })
    doFirst {
        val runPlugins = layout.projectDirectory.dir("run/plugins").asFile
        if (runPlugins.isDirectory) {
            runPlugins.listFiles()
                ?.filter { it.isFile && it.name.startsWith("Embedize-") && it.name.endsWith(".jar") }
                ?.forEach {
                    logger.lifecycle("runServer: removing run/plugins/${it.name}")
                    it.delete()
                }
        }
        logger.lifecycle("runServer pluginJars=${pluginJars.files.map { it.name }}")
    }
}
