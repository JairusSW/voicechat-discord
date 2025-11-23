package dev.amsam0.voicechatdiscord;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.amsam0.voicechatdiscord.post_1_20_6.Post_1_20_6_CommandHelper;
import dev.amsam0.voicechatdiscord.pre_1_20_6.Pre_1_20_6_CommandHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import static dev.amsam0.voicechatdiscord.Constants.PLUGIN_ID;
import static dev.amsam0.voicechatdiscord.Core.*;

public final class PaperPlugin extends JavaPlugin {
    public static final Logger LOGGER = LogManager.getLogger(PLUGIN_ID);
    public static PaperPlugin INSTANCE;
    public static CommandHelper commandHelper;

    private final EventListener eventListener = new EventListener();
    private PaperVoicechatPlugin voicechatPlugin;

    public static PaperPlugin get() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        INSTANCE = this;

        if (platform == null) {
            platform = new PaperPlatform();
        }

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

        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            voicechatPlugin = new PaperVoicechatPlugin();
            service.registerPlugin(voicechatPlugin);
            platform.info("Successfully registered voicechat discord plugin");
        } else {
            platform.error("Failed to register voicechat discord plugin");
            throw new RuntimeException("Failed to register voicechat discord plugin");
        }

        enable();

        Bukkit.getPluginManager().registerEvents(eventListener, this);

        commandHelper.registerCommands();
    }

    @Override
    public void onDisable() {
        disable();

        if (voicechatPlugin != null) {
            getServer().getServicesManager().unregister(voicechatPlugin);
            platform.info("Successfully unregistered voicechat discord plugin");
        }

        EntityTracker.stopCleanupThread();
    }
}
