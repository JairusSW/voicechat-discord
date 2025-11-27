plugins {
    java
    id("shared-plugin-minotaur")
    id("shared-plugin-shadow")
    id("net.neoforged.gradle.userdev") // version in buildSrc/build.gradle.kts
}

val parent = project.parent!!
val platformName = parent.name
val minecraftVersion = project.name
val neoforgeVersion = Properties.neoforgeVersions[minecraftVersion]!!

val archivesBaseName = "${Properties.archivesBaseName}-${platformName}"
val projectVersion = "${minecraftVersion}-${Properties.pluginVersion}"
val modrinthVersionName = "Simple Voice Chat Discord Bridge $projectVersion"
val modrinthVersionNumber = "${platformName}-${projectVersion}"

project.version = projectVersion
project.group = Properties.mavenGroup

val setupServer = tasks.register<Exec>("setupServer") {
    commandLine = listOf("./setup_server.sh", "neoforge", minecraftVersion)
    workingDir = project.rootDir

    dependsOn(tasks.build)
}

afterEvaluate {
    tasks.getByName("runServer").dependsOn(setupServer)
}

sourceSets {
    main {
        // Include common neoforge code
        java.srcDirs(layout.projectDirectory.file("../src/common/java"))
        resources.srcDirs(layout.projectDirectory.file("../src/common/resources"))
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(Properties.javaVersion))
}

runs {
    configureEach {
        modSources.add(sourceSets.main.get())
        modSources.add(project.name, project(":core").sourceSets.main.get())
    }

    create("server") {
        argument("--nogui")
    }
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()

    options.release.set(Properties.javaVersion)
}

tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()

    val properties = mapOf(
        "modVersion" to projectVersion,
        "minecraftVersion" to minecraftVersion,
        "neoforgeVersion" to neoforgeVersion,
        "voicechatApiVersion" to Properties.voicechatApiVersion,
    )
    inputs.properties(properties)

    filesMatching("META-INF/neoforge.mods.toml") {
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

    from(file("${rootDir}/LICENSE")) {
        rename { "${it}_${Properties.archivesBaseName}" }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

dependencies {
    implementation("net.neoforged:neoforge:${neoforgeVersion}")

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
    changelog.set("")
    uploadFile.set(tasks.jar)
    gameVersions.set(listOf(minecraftVersion))
    versionType.set(Properties.modrinthVersionType)
    debugMode.set(System.getenv("MODRINTH_DEBUG") != null)
    dependencies {
        required.project("simple-voice-chat")
    }
}
