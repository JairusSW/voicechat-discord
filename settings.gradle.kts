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

    "fabric:1.21.10",
    "neoforge:1.21.10",
)
