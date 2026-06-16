plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://maven.neoforged.net/releases") }
}

val loomVersion = "1.17-SNAPSHOT" // https://fabricmc.net/develop
val minotaurVersion = "2.+" // https://plugins.gradle.org/plugin/com.modrinth.minotaur
val shadowVersion = "9.4.2" // https://plugins.gradle.org/plugin/com.gradleup.shadow
val neoGradleVersion = "7.1.37" // https://projects.neoforged.net/neoforged/neogradle
val paperweightVersion = "2.0.0-beta.21" // https://github.com/PaperMC/paperweight-test-plugin/blob/master/build.gradle.kts

dependencies {
    implementation("net.fabricmc.fabric-loom-remap:net.fabricmc.fabric-loom-remap.gradle.plugin:${loomVersion}")
    implementation("net.fabricmc.fabric-loom:net.fabricmc.fabric-loom.gradle.plugin:${loomVersion}")
    implementation("com.modrinth.minotaur:com.modrinth.minotaur.gradle.plugin:${minotaurVersion}")
    implementation("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:${shadowVersion}")
    implementation("net.neoforged.gradle.userdev:net.neoforged.gradle.userdev.gradle.plugin:${neoGradleVersion}")
    implementation("io.papermc.paperweight.userdev:io.papermc.paperweight.userdev.gradle.plugin:${paperweightVersion}")
}
