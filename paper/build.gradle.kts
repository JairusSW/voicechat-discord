plugins {
    java
    id("shared-plugins")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
}

val platformName = project.name

val archivesBaseName = "${Properties.archivesBaseName}-${platformName}"
val modrinthVersionName = "Simple Voice Chat Discord Bridge ${Properties.pluginVersion}"
val modrinthVersionNumber = "${platformName}-${Properties.pluginVersion}"

project.version = Properties.pluginVersion
project.group = Properties.mavenGroup

Properties.paperSupportedMinecraftVersions.forEach { ver ->
    val verForTask = ver.replace(".", "_")

    val setupServer = tasks.register<Exec>("setupServer_${verForTask}") {
        commandLine = listOf("./setup_server.sh", "paper", ver)
        workingDir = project.rootDir

        dependsOn(tasks.build)
    }

    tasks.register<JavaExec>("runServer_${verForTask}") {
        classpath = files("run/$ver/server.jar")
        maxHeapSize = "1G"
        systemProperties["Paper.IgnoreJavaVersion"] = "true"
        args = listOf("--nogui")
        workingDir = project.projectDir.resolve("run/$ver")
        standardInput = System.`in` // necessary for console to work

        dependsOn(setupServer)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(Properties.javaVersion))
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()

    options.release.set(Properties.javaVersion)
}

tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()

    val properties = mapOf(
        "version" to Properties.pluginVersion,
        "paperApiVersion" to Properties.paperApiVersion,
    )
    inputs.properties(properties)

    filesMatching("plugin.yml") {
        expand(properties)
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadow"))
    relocate("org.bspfsystems.yamlconfiguration", "dev.amsam0.voicechatdiscord.shadow.yamlconfiguration")
    relocate("org.yaml.snakeyaml", "dev.amsam0.voicechatdiscord.shadow.snakeyaml")

    archiveBaseName.set(archivesBaseName)
    archiveClassifier.set("")
    archiveVersion.set("${Properties.pluginVersion}-shadow")

    from(file("${rootDir}/LICENSE")) {
        rename { "${it}_${Properties.archivesBaseName}" }
    }
}

tasks.jar {
    archiveBaseName.set(archivesBaseName)
    archiveClassifier.set("")
    archiveVersion.set("${Properties.pluginVersion}-raw")
}

tasks.reobfJar {
    // No idea why we didn't need to do this when we used Groovy, but this is necessary to have the correct jar filename (otherwise it will be paper-{VERSION}.jar)
    outputJar.set(layout.buildDirectory.file("libs/${archivesBaseName}-${Properties.pluginVersion}.jar"))

    dependsOn(tasks.jar.get())
}

tasks.assemble {
    dependsOn(tasks.reobfJar.get())
}

tasks.build {
    dependsOn(tasks.shadowJar.get())
}

dependencies {
    paperweight.paperDevBundle(Properties.paperDevBundleVersion)

    compileOnly("de.maxhenkel.voicechat:voicechat-api:${Properties.voicechatApiVersion}")

    shadow("org.bspfsystems:yamlconfiguration:${Properties.yamlConfigurationVersion}")

    shadow(project(":core"))
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://maven.maxhenkel.de/repository/public") }
    maven { url = uri("https://jitpack.io") }
    mavenLocal()
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set(Properties.modrinthProjectId)
    versionName.set(modrinthVersionName)
    versionNumber.set(modrinthVersionNumber)
    changelog.set("")
    uploadFile.set(tasks.reobfJar.get().outputJar.get())
    gameVersions.set(Properties.paperSupportedMinecraftVersions)
    loaders.set(listOf("paper", "purpur"))
    versionType.set(Properties.modrinthVersionType)
    detectLoaders.set(false)
    debugMode.set(System.getenv("MODRINTH_DEBUG") != null)
    dependencies {
        required.project("simple-voice-chat")
    }
}
