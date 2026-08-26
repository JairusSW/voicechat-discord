package dev.amsam0.voicechatdiscord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Category;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.VoiceChannel;
import github.scarsz.discordsrv.dependencies.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.amsam0.voicechatdiscord.Core.platform;

/** Turns a Discord lobby into ordinary, temporary numbered voice rooms. */
public final class TemporaryVoiceLobby extends ListenerAdapter implements AutoCloseable {
    private final long lobbyId;
    private final long categoryId;
    private final int userLimit;
    private final String channelPrefix;
    private final Map<Long, VoiceChannel> temporaryChannels = new ConcurrentHashMap<>();
    private volatile JDA jda;
    private volatile boolean closed;

    private TemporaryVoiceLobby(long lobbyId, long categoryId, String channelPrefix, int userLimit) {
        this.lobbyId = lobbyId;
        this.categoryId = categoryId;
        this.channelPrefix = channelPrefix;
        this.userLimit = userLimit;
    }

    public static @Nullable TemporaryVoiceLobby start(PaperPlugin plugin) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        if (!config.getBoolean("temporary_voice_lobby.enabled", false)) return null;
        Plugin discordSrvPlugin = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discordSrvPlugin == null || !discordSrvPlugin.isEnabled()) {
            platform.error("temporary_voice_lobby is enabled, but DiscordSRV is unavailable");
            return null;
        }
        long lobbyId = config.getLong("temporary_voice_lobby.lobby_id", 0L);
        long categoryId = config.getLong("temporary_voice_lobby.category_id", 0L);
        if (lobbyId == 0L || categoryId == 0L) {
            platform.error("temporary_voice_lobby requires numeric lobby_id and category_id values");
            return null;
        }
        TemporaryVoiceLobby lobby = new TemporaryVoiceLobby(
                lobbyId, categoryId,
                config.getString("temporary_voice_lobby.channel_prefix", "🔊 voice "),
                Math.max(0, config.getInt("temporary_voice_lobby.user_limit", 2)));
        Thread initializer = new Thread(lobby::waitForDiscordSrv, "voicechat-discord: Temporary Voice Initializer");
        initializer.setDaemon(true);
        initializer.start();
        return lobby;
    }

    private void waitForDiscordSrv() {
        for (int attempt = 0; attempt < 120 && !closed; attempt++) {
            if (DiscordSRV.isReady && DiscordSRV.getPlugin().getJda() != null) {
                jda = DiscordSRV.getPlugin().getJda();
                jda.addEventListener(this);
                adoptTemporaryChannels();
                platform.info("Temporary Discord voice lobby enabled");
                return;
            }
            try { Thread.sleep(500); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
        }
        if (!closed) platform.error("Timed out waiting for DiscordSRV to initialize temporary voice lobby");
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot()) return;
        VoiceChannel joined = event.getChannelJoined();
        VoiceChannel left = event.getChannelLeft();
        if (joined != null && joined.getIdLong() == lobbyId) {
            new Thread(() -> openRoom(event.getMember()), "voicechat-discord: Temporary Voice Join").start();
        }
        if (left != null && temporaryChannels.containsKey(left.getIdLong())) {
            new Thread(() -> deleteIfEmpty(left), "voicechat-discord: Temporary Voice Cleanup").start();
        }
    }

    private void openRoom(Member member) {
        Category category = member.getGuild().getCategoryById(categoryId);
        if (category == null) {
            platform.error("Configured temporary voice category does not exist");
            return;
        }
        synchronized (temporaryChannels) {
            VoiceChannel channel = category.createVoiceChannel(nextChannelName(category))
                    .setUserlimit(userLimit).complete();
            temporaryChannels.put(channel.getIdLong(), channel);
            try {
                member.getGuild().moveVoiceMember(member, channel).complete();
                platform.info("Created temporary Discord voice room for " + member.getEffectiveName());
            } catch (Throwable error) {
                platform.error("Failed to create temporary voice room for " + member.getEffectiveName(), error);
                temporaryChannels.remove(channel.getIdLong());
                channel.delete().queue(null, ignored -> {});
            }
        }
    }

    private String nextChannelName(Category category) {
        Set<String> names = new HashSet<>();
        category.getVoiceChannels().forEach(channel -> names.add(channel.getName()));
        int index = 0;
        while (names.contains(channelPrefix + index)) index++;
        return channelPrefix + index;
    }

    private void adoptTemporaryChannels() {
        if (jda == null) return;
        Category category = jda.getCategoryById(categoryId);
        if (category == null) {
            platform.error("Configured temporary voice category does not exist");
            return;
        }
        for (VoiceChannel channel : category.getVoiceChannels()) {
            if (!channel.getName().startsWith(channelPrefix)) continue;
            if (channel.getMembers().isEmpty()) channel.delete().queue();
            else temporaryChannels.put(channel.getIdLong(), channel);
        }
    }

    private void deleteIfEmpty(VoiceChannel channel) {
        if (!channel.getMembers().isEmpty()) return;
        temporaryChannels.remove(channel.getIdLong());
        channel.delete().queue(null, error -> platform.error("Failed to delete temporary voice room", error));
    }

    @Override
    public void close() {
        closed = true;
        if (jda != null) jda.removeEventListener(this);
        temporaryChannels.clear();
    }
}
