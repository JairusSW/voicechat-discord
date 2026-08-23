package dev.amsam0.voicechatdiscord;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static dev.amsam0.voicechatdiscord.Constants.CONFIG_HEADER;

/**
 * Core code between Paper and Fabric.
 */
public final class Core {
    public static VoicechatServerApi api; // Initiated by VoicechatPlugin
    public static Platform platform; // Initiated upon startup by platform

    public static ArrayList<DiscordBot> bots = new ArrayList<>();
    public static int debugLevel = 0;

    private static native void initializeNatives();

    private static native void setDebugLevel(int debugLevel);

    private static native void shutdownNatives();

    public static void enable() {
        platform.info("Enabling " + Constants.PLUGIN_ID + " " + Constants.VERSION);

        try {
            LibraryLoader.load("voicechat_discord");
            initializeNatives();
        } catch (Throwable e) {
            platform.error("Failed to load natives: " + e);
            throw new RuntimeException(e);
        }

        loadConfig();

        platform.setOnPlayerLeaveHandler(Core::onPlayerLeave);

        platform.info("Enabled " + Constants.PLUGIN_ID + " " + Constants.VERSION);
    }

    public static void disable() {
        platform.info("Disabling " + Constants.PLUGIN_ID + " " + Constants.VERSION);

        int toShutdown = bots.size();
        platform.info("Shutting down " + toShutdown + " bot" + (toShutdown != 1 ? "s" : ""));

        clearBots();

        platform.info("Successfully shutdown " + toShutdown + " bot" + (toShutdown != 1 ? "s" : ""));

        try {
            shutdownNatives();
            platform.info("Successfully shutdown native runtime");
        } catch (Throwable e) {
            platform.error("Failed to shutdown native runtime", e);
        }

        platform.info("Disabled " + Constants.PLUGIN_ID + " " + Constants.VERSION);
    }

    @SuppressWarnings({"DataFlowIssue", "unchecked", "ResultOfMethodCallIgnored"})
    public static void loadConfig() {
        File configFile = new File(platform.getConfigPath());

        if (!configFile.getParentFile().exists())
            configFile.getParentFile().mkdirs();

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException e) {
            platform.debug("IOException when loading config", e);
        } catch (InvalidConfigurationException e) {
            platform.error("Failed to load config file");
            throw new RuntimeException(e);
        }

        LinkedHashMap<String, String> defaultBot = new LinkedHashMap<>();
        defaultBot.put("token", "DISCORD_BOT_TOKEN_HERE");
        defaultBot.put("vc_id", "VOICE_CHANNEL_ID_HERE");
        config.addDefault("bots", List.of(defaultBot));

        config.addDefault("alert_ops_of_updates", true);

        config.addDefault("debug_level", 0);

        config.addDefault("automatic_lobby.enabled", false);
        config.addDefault("automatic_lobby.lobby_id", 0L);
        config.addDefault("automatic_lobby.category_id", 0L);
        config.addDefault("automatic_lobby.channel_prefix", "prox-");

        config.getOptions().setCopyDefaults(true);
        config.getOptions().setHeader(CONFIG_HEADER);
        try {
            config.save(configFile);
        } catch (IOException e) {
            platform.error("Failed to save config file: " + e);
            throw new RuntimeException(e);
        }

        bots.clear();

        for (LinkedHashMap<String, Object> bot : (List<LinkedHashMap<String, Object>>) config.getList("bots")) {
            if (bot.get("token") == null) {
                platform.error(
                        "Failed to load a bot, missing token property.");
                continue;
            }

            if (bot.get("vc_id") == null) {
                platform.error(
                        "Failed to load a bot, missing vc_id property.");
                continue;
            }

            try {
                bots.add(new DiscordBot((String) bot.get("token"), (Long) bot.get("vc_id")));
            } catch (ClassCastException e) {
                platform.error("Failed to load a bot. Please make sure that the token property is a string and the vc_id property is a number.");
            }
        }

        platform.info("Using " + bots.size() + " bot" + (bots.size() != 1 ? "s" : ""));

        try {
            debugLevel = (int) config.get("debug_level");
            if (debugLevel > 0) platform.info("Debug level has been set to " + debugLevel);
            setDebugLevel(debugLevel);
        } catch (ClassCastException e) {
            platform.error("Please make sure the debug_level option is a valid integer");
        }
    }

    public static void clearBots() {
        bots.forEach(discordBot -> {
            discordBot.stop();
            discordBot.free();
        });
        bots.clear();
    }

    private static void onPlayerLeave(UUID playerUuid) {
        DiscordBot bot = getBotForPlayer(playerUuid);
        if (bot != null) {
            platform.info("Stopping bot for player " + playerUuid);
            bot.stop();
        }
    }

    public static @Nullable DiscordBot getBotForPlayer(UUID playerUuid) {
        return getBotForPlayer(playerUuid, false);
    }

    public static @Nullable DiscordBot getBotForPlayer(UUID playerUuid, boolean fallbackToAvailableBot) {
        for (DiscordBot bot : bots) {
            if (bot.player() != null && bot.player().getUuid() == playerUuid)
                return bot;
        }
        if (fallbackToAvailableBot)
            return getAvailableBot();
        return null;
    }

    public static @Nullable DiscordBot getAvailableBot() {
        for (DiscordBot bot : bots) {
            if (bot.player() == null)
                return bot;
        }
        return null;
    }
}
