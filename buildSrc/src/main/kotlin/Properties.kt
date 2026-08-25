@Suppress("ConstPropertyName", "MemberVisibilityCanBePrivate")
object Properties {
    const val javaVersion = 25

    /* Project */
    const val pluginVersion = "3.2.0-geneva.17" // Make sure to sync with setup_server.sh
    const val mavenGroup = "dev.amsam0.voicechatdiscord"
    const val archivesBaseName = "voicechat-discord"
    const val modrinthProjectId = "S1jG5YV5"
    const val modrinthVersionType = "release" // Can be release, beta, or alpha

    /* Paper */
    const val paperApiVersion = "1.20"
    val paperSupportedMinecraftVersions = listOf(
        // We follow https://modrepo.de/minecraft/voicechat/wiki/supported_versions
        "1.20.5",
        "1.20.6",
        "1.21",
        "1.21.1",
        "1.21.2",
        "1.21.3",
        "1.21.4",
        "1.21.5",
        "1.21.6",
        "1.21.7",
        "1.21.8",
        "1.21.9",
        "1.21.10",
        "1.21.11",
        "26.1",
        "26.1.1",
        "26.1.2",
        "26.2",
    )
    // https://repo.papermc.io/ui/packages/gav:%2F%2Fio.papermc.paper:dev-bundle?name=dev-bundle&type=packages
    val paperDevBundleVersion = "${paperSupportedMinecraftVersions.last()}.build.+"

    /* Fabric (https://fabricmc.net/develop) */
    data class FabricMetadata(val fabricApiVersion: String, val permissionsApiVersion: String)
    const val fabricLoaderVersion = "0.19.3" // Make sure to sync with setup_server.sh
    val fabricVersions = mapOf(
        // We follow https://modrepo.de/minecraft/voicechat/wiki/supported_versions
        // See https://github.com/lucko/fabric-permissions-api/releases for permissions API
        // Make sure to update GitHub workflows
        "1.16.5"  to FabricMetadata(fabricApiVersion = "0.42.0+1.16",     permissionsApiVersion = "0.3.3"),
        "1.18.2"  to FabricMetadata(fabricApiVersion = "0.77.0+1.18.2",   permissionsApiVersion = "0.3.3"),
        "1.19.2"  to FabricMetadata(fabricApiVersion = "0.77.0+1.19.2",   permissionsApiVersion = "0.3.3"),
        "1.20"    to FabricMetadata(fabricApiVersion = "0.83.0+1.20",     permissionsApiVersion = "0.3.3"),
        "1.20.1"  to FabricMetadata(fabricApiVersion = "0.92.6+1.20.1",   permissionsApiVersion = "0.3.3"),
        "1.20.2"  to FabricMetadata(fabricApiVersion = "0.91.6+1.20.2",   permissionsApiVersion = "0.3.3"),
        "1.20.3"  to FabricMetadata(fabricApiVersion = "0.91.1+1.20.3",   permissionsApiVersion = "0.3.3"),
        "1.20.4"  to FabricMetadata(fabricApiVersion = "0.97.3+1.20.4",   permissionsApiVersion = "0.3.3"),
        "1.20.5"  to FabricMetadata(fabricApiVersion = "0.97.8+1.20.5",   permissionsApiVersion = "0.3.3"),
        "1.20.6"  to FabricMetadata(fabricApiVersion = "0.100.8+1.20.6",  permissionsApiVersion = "0.3.3"),
        "1.21"    to FabricMetadata(fabricApiVersion = "0.102.0+1.21",    permissionsApiVersion = "0.3.3"),
        "1.21.1"  to FabricMetadata(fabricApiVersion = "0.116.4+1.21.1",  permissionsApiVersion = "0.3.3"),
        "1.21.2"  to FabricMetadata(fabricApiVersion = "0.106.1+1.21.2",  permissionsApiVersion = "0.3.3"),
        "1.21.3"  to FabricMetadata(fabricApiVersion = "0.114.1+1.21.3",  permissionsApiVersion = "0.3.3"),
        "1.21.4"  to FabricMetadata(fabricApiVersion = "0.119.3+1.21.4",  permissionsApiVersion = "0.3.3"),
        "1.21.5"  to FabricMetadata(fabricApiVersion = "0.128.1+1.21.5",  permissionsApiVersion = "0.3.3"),
        "1.21.6"  to FabricMetadata(fabricApiVersion = "0.128.2+1.21.6",  permissionsApiVersion = "0.4.1"),
        "1.21.7"  to FabricMetadata(fabricApiVersion = "0.129.0+1.21.7",  permissionsApiVersion = "0.4.1"),
        "1.21.8"  to FabricMetadata(fabricApiVersion = "0.136.1+1.21.8",  permissionsApiVersion = "0.4.1"),
        "1.21.9"  to FabricMetadata(fabricApiVersion = "0.134.1+1.21.9",  permissionsApiVersion = "0.5.0"),
        "1.21.10" to FabricMetadata(fabricApiVersion = "0.138.4+1.21.10", permissionsApiVersion = "0.5.0"),
        "1.21.11" to FabricMetadata(fabricApiVersion = "0.141.4+1.21.11", permissionsApiVersion = "0.6.1"),
        "26.1"    to FabricMetadata(fabricApiVersion = "0.145.1+26.1",    permissionsApiVersion = "0.7.0"),
        "26.1.1"  to FabricMetadata(fabricApiVersion = "0.145.4+26.1.1",  permissionsApiVersion = "0.7.0"),
        "26.1.2"  to FabricMetadata(fabricApiVersion = "0.152.1+26.1.2",  permissionsApiVersion = "0.7.0"),
        "26.2"    to FabricMetadata(fabricApiVersion = "0.152.1+26.2",    permissionsApiVersion = "0.7.0"),
    )

    /* NeoForge (https://neoforged.net, https://parchmentmc.org/docs/getting-started) */
    val neoforgeVersions = mapOf(
        // We follow https://modrepo.de/minecraft/voicechat/wiki/supported_versions
        // Make sure to update GitHub workflows
        "1.20.5"  to "20.5.21-beta",
        "1.20.6"  to "20.6.139",
        "1.21"    to "21.0.167",
        "1.21.1"  to "21.1.215",
        "1.21.2"  to "21.2.1-beta",
        "1.21.3"  to "21.3.94",
        "1.21.4"  to "21.4.155",
        "1.21.5"  to "21.5.95",
        "1.21.6"  to "21.6.20-beta",
        "1.21.7"  to "21.7.25-beta",
        "1.21.8"  to "21.8.51",
        "1.21.9"  to "21.9.16-beta",
        "1.21.10" to "21.10.56-beta",
        "1.21.11" to "21.11.17-beta",
        "26.1"    to "26.1.0.19-beta",
        "26.1.1"  to "26.1.1.15-beta",
        "26.1.2"  to "26.1.2.76",
        "26.2"    to "26.2.0.0-beta",
    )

    /* Dependencies */
    const val voicechatApiVersion = "2.4.11"
    const val yamlConfigurationVersion = "2.0.2"
}
