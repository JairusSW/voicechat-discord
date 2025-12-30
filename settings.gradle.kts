pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "voicechat-discord"
include(
    "core",
    "paper",
    "fabric",
    "neoforge",

    "fabric:1.21.11",
    "neoforge:1.21.11",
)
