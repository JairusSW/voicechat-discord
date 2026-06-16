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

    "fabric:26.2",
    "neoforge:26.2",
)
