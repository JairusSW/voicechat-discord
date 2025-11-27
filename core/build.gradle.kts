plugins {
    java
    id("shared-plugin-minotaur")
}

project.version = Properties.pluginVersion
project.group = Properties.mavenGroup

tasks.register<Exec>("buildAndCopyNatives") {
    commandLine = listOf("./build_and_copy_natives.sh")
}

tasks.register<Exec>("copyNatives") {
    commandLine = listOf("./copy_natives.sh")
}

tasks.register<Exec>("copyNativesFromLatestRelease") {
    commandLine = listOf("./copy_natives_from_latest_release.sh")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(Properties.javaVersion))
}

tasks.jar {
    archiveBaseName.set(Properties.archivesBaseName + "-" + project.name)
}

val processSources = tasks.register<Copy>("processSources") {
    filteringCharset = Charsets.UTF_8.name()

    val properties = mapOf(
        "version" to Properties.pluginVersion,
    )
    inputs.properties(properties)

    from("src/main/java") {
        include("**/Constants.java")

        expand(properties)
    }
    into("build/processedSrc")
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()

    options.release.set(Properties.javaVersion)

    options.headerOutputDirectory = layout.buildDirectory.dir("headers")

    val javaSources = sourceSets["main"].allJava.filter {
        it.name != "Constants.java"
    }.asFileTree

    source = javaSources + fileTree(processSources.get().destinationDir)
    dependsOn(processSources)
}

dependencies {
    compileOnly("de.maxhenkel.voicechat:voicechat-api:${Properties.voicechatApiVersion}")

    compileOnly("org.bspfsystems:yamlconfiguration:${Properties.yamlConfigurationVersion}")
    compileOnly("com.mojang:brigadier:1.0.18")
    compileOnly("org.jetbrains:annotations:26.0.2")
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
    maven { url = uri("https://maven.maxhenkel.de/repository/public") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://libraries.minecraft.net") }
    mavenLocal()
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set(Properties.modrinthProjectId)
    syncBodyFrom.set(rootProject.file("README.md").inputStream().bufferedReader().use { it.readText() })
    detectLoaders.set(false)
    debugMode.set(System.getenv("MODRINTH_DEBUG") != null)
}
