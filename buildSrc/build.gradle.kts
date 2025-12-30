plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://maven.neoforged.net/releases") }
}

dependencies {
    implementation("fabric-loom:fabric-loom.gradle.plugin:1.14-SNAPSHOT") // https://fabricmc.net/develop
    implementation("com.modrinth.minotaur:com.modrinth.minotaur.gradle.plugin:2.+")
    implementation("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:9.3.0")
    implementation("net.neoforged.gradle.userdev:net.neoforged.gradle.userdev.gradle.plugin:7.1.16")
}
