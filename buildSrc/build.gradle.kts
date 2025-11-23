plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
}

dependencies {
    implementation("fabric-loom:fabric-loom.gradle.plugin:1.13-SNAPSHOT") // https://fabricmc.net/develop
    implementation("com.modrinth.minotaur:com.modrinth.minotaur.gradle.plugin:2.+")
    implementation("com.github.johnrengelman.shadow:com.github.johnrengelman.shadow.gradle.plugin:8.1.1")
}
