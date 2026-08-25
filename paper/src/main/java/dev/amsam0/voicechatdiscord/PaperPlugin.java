package dev.amsam0.voicechatdiscord;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.amsam0.voicechatdiscord.post_1_20_6.Post_1_20_6_CommandHelper;
import dev.amsam0.voicechatdiscord.pre_1_20_6.Pre_1_20_6_CommandHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import static dev.amsam0.voicechatdiscord.Constants.PLUGIN_ID;
import static dev.amsam0.voicechatdiscord.Core.*;
import static dev.amsam0.voicechatdiscord.PaperConstants.VOICECHAT_MIN_VERSION;

public final class PaperPlugin extends JavaPlugin {
    public static final Logger LOGGER = LogManager.getLogger(PLUGIN_ID);
    public static PaperPlugin INSTANCE;
    public static CommandHelper commandHelper;

    private final EventListener eventListener = new EventListener();
    private PaperVoicechatPlugin voicechatPlugin;
    private LobbyOrchestrator lobbyOrchestrator;
    private IdentityRegistry identityRegistry;
    private GenevaRoles genevaRoles;
    private DiscordOnboarding discordOnboarding;

    public static PaperPlugin get() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        INSTANCE = this;

        if (platform == null) {
            platform = new PaperPlatform();
        }

        // Check SVC version because Bukkit's plugin.yml doesn't support version requirements
        Plugin svcPlugin = Bukkit.getServer().getPluginManager().getPlugin("voicechat");
        if (svcPlugin != null) {
            @SuppressWarnings("deprecation")
            String version = svcPlugin.getDescription().getVersion();
            platform.debug("SVC version: " + version);
            String[] splitVersion = version.split("-");
            if (splitVersion.length > 1) {
                // Beta builds are fine since they will have the new APIs we depend on.
                // If we don't remove the ending part, it will say SVC isn't new enough
                // We're on Paper, we want to get rid of the ending part (pre1)
                version = splitVersion[0];
                platform.debug("SVC version after normalizing: " + version);
            }

            try {
                if (version == null || Version.parseChecked(version).isLowerThan(VOICECHAT_MIN_VERSION)) {
                    String message = "Simple Voice Chat Discord Bridge requires Simple Voice Chat version " + VOICECHAT_MIN_VERSION + " or later.";
                    if (version != null) {
                        message += " You have version " + version + ".";
                    }
                    platform.error(message);
                    throw new RuntimeException(message);
                }
            } catch (NumberFormatException e) {
                platform.error("Failed to parse SVC version", e);
                platform.warn("Assuming SVC is " + VOICECHAT_MIN_VERSION + " or later. If not, things will break.");
            }
        } else {
            platform.error("Simple Voice Chat plugin is null");
        }

        // Check Minecraft version to determine proper CommandHelper to use
        String originalVersion = getServer().getMinecraftVersion();
        platform.info("Original Minecraft version: " + originalVersion);
        try {
            Version parsedVersion = Version.parseChecked(originalVersion);
            platform.info("Parsed Minecraft version: " + parsedVersion);

            var wantedCommandHelper = new Version(1, 20, 6);
            if (parsedVersion.isHigherThanOrEquivalentTo(wantedCommandHelper)) {
                platform.info("Server is >=1.20.6");
                commandHelper = new Post_1_20_6_CommandHelper();
            } else {
                platform.info("Server is <1.20.6");
                commandHelper = new Pre_1_20_6_CommandHelper();
            }
        } catch (NumberFormatException e) {
            platform.error("Unable to parse server version", e);

            if (originalVersion.equals("1.8") || originalVersion.startsWith("1.8.") ||
                    originalVersion.equals("1.9") || originalVersion.startsWith("1.9.") ||
                    originalVersion.equals("1.10") || originalVersion.startsWith("1.10.") ||
                    originalVersion.equals("1.11") || originalVersion.startsWith("1.11.") ||
                    originalVersion.equals("1.12") || originalVersion.startsWith("1.12.") ||
                    originalVersion.equals("1.13") || originalVersion.startsWith("1.13.") ||
                    originalVersion.equals("1.14") || originalVersion.startsWith("1.14.") ||
                    originalVersion.equals("1.15") || originalVersion.startsWith("1.15.") ||
                    originalVersion.equals("1.16") || originalVersion.startsWith("1.16.") ||
                    originalVersion.equals("1.17") || originalVersion.startsWith("1.17.") ||
                    originalVersion.equals("1.18") || originalVersion.startsWith("1.18.") ||
                    originalVersion.equals("1.19") || originalVersion.startsWith("1.19.") ||

                    originalVersion.equals("1.20") ||
                    originalVersion.equals("1.20.0") ||
                    originalVersion.equals("1.20.1") ||
                    originalVersion.equals("1.20.2") ||
                    originalVersion.equals("1.20.3") ||
                    originalVersion.equals("1.20.4") ||
                    originalVersion.equals("1.20.5")
            ) {
                platform.info("Server is most likely <1.20.6");
                commandHelper = new Pre_1_20_6_CommandHelper();
            } else {
                platform.info("Server is most likely >=1.20.6");
                commandHelper = new Post_1_20_6_CommandHelper();
            }
        }

        // Register voicechat service
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            voicechatPlugin = new PaperVoicechatPlugin();
            service.registerPlugin(voicechatPlugin);
            platform.info("Successfully registered voicechat discord plugin");
        } else {
            platform.error("Failed to register voicechat discord plugin");
            throw new RuntimeException("Failed to register voicechat discord plugin");
        }

        // Enable core
        enable();

        identityRegistry = new IdentityRegistry(this);
        Bukkit.getPluginManager().registerEvents(identityRegistry, this);
        getCommand("link").setExecutor(identityRegistry);
        getCommand("whois").setExecutor(identityRegistry);

        lobbyOrchestrator = LobbyOrchestrator.start(this, identityRegistry);

        genevaRoles = new GenevaRoles(this, identityRegistry);
        Bukkit.getPluginManager().registerEvents(genevaRoles, this);
        getCommand("team").setExecutor(genevaRoles);
        getCommand("role").setExecutor(genevaRoles);
        getCommand("ping").setExecutor(genevaRoles);

        discordOnboarding = DiscordOnboarding.start(this, identityRegistry);

        // Register events
        Bukkit.getPluginManager().registerEvents(eventListener, this);

        // Register commands
        commandHelper.registerCommands();
    }

    @Override
    public void onDisable() {
        if (discordOnboarding != null) {
            discordOnboarding.close();
        }
        if (lobbyOrchestrator != null) {
            lobbyOrchestrator.close();
        }
        disable();

        if (voicechatPlugin != null) {
            getServer().getServicesManager().unregister(voicechatPlugin);
            platform.info("Successfully unregistered voicechat discord plugin");
        }

        EntityTracker.stopCleanupThread();
    }
}
