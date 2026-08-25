package dev.amsam0.voicechatdiscord;

import de.maxhenkel.voicechat.api.ServerPlayer;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Category;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.VoiceChannel;
import github.scarsz.discordsrv.dependencies.jda.api.Permission;
import github.scarsz.discordsrv.dependencies.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.EnumSet;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.amsam0.voicechatdiscord.Core.api;
import static dev.amsam0.voicechatdiscord.Core.getAvailableBot;
import static dev.amsam0.voicechatdiscord.Core.platform;

/** Automatically turns the DiscordSRV voice lobby into SVC bridge sessions. */
public final class LobbyOrchestrator extends ListenerAdapter implements AutoCloseable, Listener {
    private volatile JDA jda;
    private volatile boolean closed;
    private final long lobbyId;
    private final long categoryId;
    private final String channelPrefix;
    private final IdentityRegistry identities;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private LobbyOrchestrator(long lobbyId, long categoryId, String channelPrefix, IdentityRegistry identities) {
        this.lobbyId = lobbyId;
        this.categoryId = categoryId;
        this.channelPrefix = channelPrefix;
        this.identities = identities;
    }

    public static @Nullable LobbyOrchestrator start(PaperPlugin plugin, IdentityRegistry identities) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        if (!config.getBoolean("automatic_lobby.enabled", false)) {
            return null;
        }

        Plugin discordSrvPlugin = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discordSrvPlugin == null || !discordSrvPlugin.isEnabled()) {
            platform.error("automatic_lobby is enabled, but DiscordSRV is unavailable");
            return null;
        }

        long lobbyId = config.getLong("automatic_lobby.lobby_id", 0L);
        long categoryId = config.getLong("automatic_lobby.category_id", 0L);
        if (lobbyId == 0L || categoryId == 0L) {
            platform.error("automatic_lobby requires numeric lobby_id and category_id values");
            return null;
        }

        LobbyOrchestrator orchestrator = new LobbyOrchestrator(
                lobbyId, categoryId,
                config.getString("automatic_lobby.channel_prefix", "prox-"), identities
        );
        Thread initializer = new Thread(orchestrator::waitForDiscordSrv, "voicechat-discord: Lobby Initializer");
        initializer.setDaemon(true);
        initializer.start();
        Bukkit.getPluginManager().registerEvents(orchestrator, plugin);
        return orchestrator;
    }

    private void waitForDiscordSrv() {
        for (int attempt = 0; attempt < 120 && !closed; attempt++) {
            if (DiscordSRV.isReady && DiscordSRV.getPlugin().getJda() != null) {
                jda = DiscordSRV.getPlugin().getJda();
                jda.addEventListener(this);
                platform.info("Automatic Discord lobby orchestration enabled");
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (!closed) platform.error("Timed out waiting for DiscordSRV to initialize automatic lobby orchestration");
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot()) return;

        VoiceChannel joined = event.getChannelJoined();
        VoiceChannel left = event.getChannelLeft();
        String discordId = event.getMember().getId();

        if (joined != null && joined.getIdLong() == lobbyId) {
            new Thread(() -> openSession(event.getMember()), "voicechat-discord: Lobby Join").start();
        } else if (left != null && sessions.containsKey(discordId)) {
            Session session = sessions.get(discordId);
            if (session != null && (joined == null || joined.getIdLong() != session.channel.getIdLong())) {
                new Thread(() -> closeSession(discordId), "voicechat-discord: Lobby Leave").start();
            }
        }
    }

    private void openSession(Member member) {
        String discordId = member.getId();
        if (sessions.containsKey(discordId)) return;

        Player bukkitPlayer = identities.onlinePlayerForDiscord(discordId);
        if (bukkitPlayer == null) {
            UUID primary = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(discordId);
            if (primary != null) bukkitPlayer = Bukkit.getPlayer(primary);
        }
        if (bukkitPlayer == null) {
            setServerMuted(member, true);
            platform.warn("Discord user " + member.getEffectiveName() + " has no linked Minecraft account online");
            return;
        }

        setServerMuted(member, false);

        Category category = member.getGuild().getCategoryById(categoryId);
        if (category == null) {
            platform.error("Configured automatic lobby category does not exist");
            return;
        }

        synchronized (Core.bots) {
            DiscordBot bot = getAvailableBot();
            if (bot == null) {
                platform.warn("No Discord audio bot is available for " + member.getEffectiveName());
                return;
            }

            VoiceChannel channel = category.createVoiceChannel(channelPrefix + safeName(bukkitPlayer.getName()))
                    .setUserlimit(2)
                    .complete();
            sessions.put(discordId, new Session(bot, channel, bukkitPlayer.getUniqueId()));

            try {
                channel.getManager().putMemberPermissionOverride(
                        bot.discordUserId(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK, Permission.VOICE_USE_VAD),
                        Collections.emptySet()).complete();
                bot.setVoiceChannel(channel.getIdLong());
                ServerPlayer serverPlayer = api.fromServerPlayer(bukkitPlayer);
                bot.logInAndStart(serverPlayer);
                if (!bot.isStarted()) throw new IllegalStateException("Audio bot failed to start");
                member.getGuild().moveVoiceMember(member, channel).complete();
                platform.info("Automatically started bridge session for " + bukkitPlayer.getName());
            } catch (Throwable error) {
                platform.error("Failed to automatically start bridge for " + bukkitPlayer.getName(), error);
                closeSession(discordId);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().playerId.equals(event.getPlayer().getUniqueId())) {
                new Thread(() -> closeSession(entry.getKey()), "voicechat-discord: Player Leave").start();
                break;
            }
        }
    }

    private void closeSession(String discordId) {
        Session session = sessions.remove(discordId);
        if (session == null) return;
        try {
            session.bot.stop();
        } finally {
            try {
                session.channel.delete().complete();
            } catch (Throwable error) {
                platform.error("Failed to delete temporary bridge channel", error);
            }
        }
    }

    private static String safeName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
    }

    private void setServerMuted(Member member, boolean muted) {
        try {
            member.getGuild().mute(member, muted).complete();
            platform.info((muted ? "Server-muted " : "Server-unmuted ") + member.getEffectiveName()
                    + " based on Minecraft online status");
        } catch (Throwable error) {
            platform.error("Failed to " + (muted ? "mute " : "unmute ") + member.getEffectiveName(), error);
        }
    }

    @Override
    public void close() {
        closed = true;
        if (jda != null) jda.removeEventListener(this);
        for (String discordId : sessions.keySet().toArray(String[]::new)) {
            closeSession(discordId);
        }
    }

    private record Session(DiscordBot bot, VoiceChannel channel, UUID playerId) {}
}
