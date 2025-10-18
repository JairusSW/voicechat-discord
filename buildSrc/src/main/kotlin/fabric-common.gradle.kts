plugins {
    java
    // versions in buildSrc/build.gradle.kts
    id("com.modrinth.minotaur")
    id("com.github.johnrengelman.shadow")
    id("fabric-loom")
}

val parent = project.parent!!
val platformName = parent.name
val minecraftVersion = project.name
val fabricMetadata = Properties.fabricVersions[minecraftVersion]!!

val archivesBaseName = "${Properties.archivesBaseName}-${platformName}"
val projectVersion = "${minecraftVersion}-${Properties.pluginVersion}"
val modrinthVersionName = "${platformName}-${projectVersion}"

project.version = projectVersion
project.group = Properties.mavenGroup

sourceSets {
    main {
        // Include common fabric code
        java.srcDirs(layout.projectDirectory.file("../src/common/java"))
        resources.srcDirs(layout.projectDirectory.file("../src/common/resources"))
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

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()

    options.release.set(Properties.javaVersion)
}

tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()

    val properties = mapOf(
        "version" to projectVersion,
        "minecraftVersion" to minecraftVersion,
        "voicechatApiVersion" to Properties.voicechatApiVersion,
        "javaVersion" to Properties.javaVersion.toString(),
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
    relocate("com.github.zafarkhaja.semver", "dev.amsam0.voicechatdiscord.shadow.semver")
    relocate("com.google.gson", "dev.amsam0.voicechatdiscord.shadow.gson")

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
    mappings("net.fabricmc:yarn:${fabricMetadata.yarnMappingsVersion}:v2")
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

    implementation("com.github.zafarkhaja:java-semver:${Properties.javaSemverVersion}")
    shadow("com.github.zafarkhaja:java-semver:${Properties.javaSemverVersion}")

    implementation("com.google.code.gson:gson:${Properties.gsonVersion}")
    shadow("com.google.code.gson:gson:${Properties.gsonVersion}")

    implementation(project(":core"))
    shadow(project(":core"))

    // Runtime dependencies for testing only
    modRuntimeOnly("maven.modrinth:simple-voice-chat:fabric-${minecraftVersion}-2.6.4")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
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
    versionNumber.set(modrinthVersionName)
    uploadFile.set(tasks.remapJar)
    gameVersions.set(listOf(minecraftVersion))
    versionType.set(Properties.modrinthVersionType)
    debugMode.set(System.getenv("MODRINTH_DEBUG") != null)
    dependencies {
        required.project("simple-voice-chat")
        required.project("fabric-api")
    }
}
