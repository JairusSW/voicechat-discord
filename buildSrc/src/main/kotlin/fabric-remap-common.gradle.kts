plugins {
    java
    id("plugin-minotaur")
    id("plugin-shadow")
    id("net.fabricmc.fabric-loom-remap") // version in buildSrc/build.gradle.kts
}

val parent = project.parent!!
val platformName = parent.name
val minecraftVersion = project.name
val fabricMetadata = Properties.fabricVersions[minecraftVersion]!!
val supportedVersions = fabricVersionRanges[minecraftVersion] ?: listOf(minecraftVersion)

val archivesBaseName = "${Properties.archivesBaseName}-${platformName}"
val projectVersion = "${minecraftVersion}-${Properties.pluginVersion}"
val modrinthVersionName = "Simple Voice Chat Discord Bridge $projectVersion"
val modrinthVersionNumber = "${platformName}-${projectVersion}"

project.version = projectVersion
project.group = Properties.mavenGroup

val setupServer = tasks.register<Exec>("setupServer") {
    commandLine = listOf("./setup_server.sh", platformName, minecraftVersion)
    workingDir = project.rootDir

    dependsOn(tasks.build)
}

tasks.runServer {
    dependsOn(setupServer)
}

sourceSets {
    main {
        // Include template fabric code
        // Note that this isn't actually used in compileJava. This just allows autocomplete to be used when editing templates
        java.srcDirs(layout.projectDirectory.file("../src/template/java"))

        // Include common fabric resources
        resources.srcDirs(layout.projectDirectory.file("../src/main/resources"))
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(Properties.javaVersion))
}

loom {
    runConfigs.configureEach {
        // Without this, none of the run configurations will be generated because this project is not the root project
        isIdeConfigGenerated = true
    }
}

val generateVersionSource = tasks.register<Exec>("generateVersionSource") {
    commandLine = listOf("./venv/bin/python3", "./generate_version_source.py", platformName, minecraftVersion)
    workingDir = project.rootDir
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()

    options.release.set(Properties.javaVersion)

    source = fileTree(layout.buildDirectory.file("generatedSrc"))
    dependsOn(generateVersionSource)
}

tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()

    val properties = mapOf(
        "version" to projectVersion,
        "minecraftVersions" to supportedVersions.joinToString("\", \""),
        "voicechatApiVersion" to Properties.voicechatApiVersion,
        "javaVersion" to Properties.javaVersion.toString(),
        "fabricApiId" to "fabric",
    )
    inputs.properties(properties)

    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadow"))
    relocate("org.bspfsystems.yamlconfiguration", "dev.amsam0.voicechatdiscord.shadow.yamlconfiguration")
    relocate("org.yaml.snakeyaml", "dev.amsam0.voicechatdiscord.shadow.snakeyaml")
    exclude("org/slf4j/**") // added by yamlconfiguration

    archiveBaseName.set(archivesBaseName)
    archiveClassifier.set("")
    archiveVersion.set(projectVersion)

    destinationDirectory.set(project.objects.directoryProperty().fileValue(layout.buildDirectory.file("shadow").get().asFile))

    from(file("${rootDir}/LICENSE")) {
        rename { "${it}_${Properties.archivesBaseName}" }
    }
}

tasks.remapJar {
    archiveBaseName.set(archivesBaseName)
    archiveClassifier.set("")
    archiveVersion.set(projectVersion)

    inputFile.set(tasks.shadowJar.get().archiveFile)
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${Properties.fabricLoaderVersion}")
    // For running a server, we need the whole API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricMetadata.fabricApiVersion}")
//    setOf(
//        "fabric-api-base",
//        "fabric-command-api-v2",
//        "fabric-lifecycle-events-v1",
//        "fabric-networking-api-v1"
//    ).forEach {
//        modImplementation(fabricApi.module(it, fabricMetadata.fabricApiVersion))
//    }

    // This includes fabric-api - things will break because it has a newer version than what we use
    modImplementation("me.lucko:fabric-permissions-api:${fabricMetadata.permissionsApiVersion}") { exclude(group = "net.fabricmc.fabric-api") }
    include("me.lucko:fabric-permissions-api:${fabricMetadata.permissionsApiVersion}") { exclude(group = "net.fabricmc.fabric-api") }

    compileOnly("de.maxhenkel.voicechat:voicechat-api:${Properties.voicechatApiVersion}")

    implementation("org.bspfsystems:yamlconfiguration:${Properties.yamlConfigurationVersion}")
    shadow("org.bspfsystems:yamlconfiguration:${Properties.yamlConfigurationVersion}")

    implementation(project(":core"))
    shadow(project(":core"))
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/releases") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven { url = uri("https://maven.maxhenkel.de/repository/public") }
    mavenLocal()
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set(Properties.modrinthProjectId)
    versionName.set(modrinthVersionName)
    versionNumber.set(modrinthVersionNumber)
    changelog.set("**Please note:** this version supports the following Minecraft versions: ${supportedVersions.joinToString(", ")}")
    uploadFile.set(tasks.remapJar)
    gameVersions.set(supportedVersions)
    versionType.set(Properties.modrinthVersionType)
    debugMode.set(System.getenv("MODRINTH_DEBUG") != null)
    dependencies {
        required.project("simple-voice-chat")
        required.project("fabric-api")
    }
}
