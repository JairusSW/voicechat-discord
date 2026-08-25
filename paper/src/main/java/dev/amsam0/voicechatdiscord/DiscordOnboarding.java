package dev.amsam0.voicechatdiscord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.AccountLinkedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.events.guild.member.GuildMemberJoinEvent;
import github.scarsz.discordsrv.dependencies.jda.api.events.message.guild.GuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.amsam0.voicechatdiscord.Core.platform;

/** Discord-first account linking and real-name onboarding. */
public final class DiscordOnboarding extends ListenerAdapter implements Listener, AutoCloseable {
    private final PaperPlugin plugin;
    private final IdentityRegistry identities;
    private final long linkingChannelId;
    private final long guildId;
    private final long unlinkedRoleId;
    private final Map<String, PendingName> awaitingNames = new ConcurrentHashMap<>();
    private volatile JDA jda;
    private volatile boolean closed;

    private DiscordOnboarding(PaperPlugin plugin, IdentityRegistry identities, long guildId, long linkingChannelId, long unlinkedRoleId) {
        this.plugin = plugin;
        this.identities = identities;
        this.guildId = guildId;
        this.linkingChannelId = linkingChannelId;
        this.unlinkedRoleId = unlinkedRoleId;
    }

    public static @Nullable DiscordOnboarding start(PaperPlugin plugin, IdentityRegistry identities) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        if (!config.getBoolean("discord_onboarding.enabled", false)) return null;
        long channelId = config.getLong("discord_onboarding.linking_channel_id", 0L);
        long guildId = config.getLong("discord_onboarding.guild_id", 0L);
        long roleId = config.getLong("discord_onboarding.unlinked_role_id", 0L);
        if (channelId == 0L || guildId == 0L || roleId == 0L) {
            platform.error("discord_onboarding requires guild_id, linking_channel_id, and unlinked_role_id");
            return null;
        }
        Plugin discordSrv = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discordSrv == null || !discordSrv.isEnabled()) {
            platform.error("discord_onboarding is enabled, but DiscordSRV is unavailable");
            return null;
        }
        DiscordOnboarding onboarding = new DiscordOnboarding(plugin, identities, guildId, channelId, roleId);
        DiscordSRV.api.subscribe(onboarding);
        Bukkit.getPluginManager().registerEvents(onboarding, plugin);
        Thread initializer = new Thread(onboarding::waitForDiscordSrv, "voicechat-discord: Onboarding Initializer");
        initializer.setDaemon(true);
        initializer.start();
        return onboarding;
    }

    private void waitForDiscordSrv() {
        for (int attempt = 0; attempt < 120 && !closed; attempt++) {
            if (DiscordSRV.isReady && DiscordSRV.getPlugin().getJda() != null) {
                jda = DiscordSRV.getPlugin().getJda();
                jda.addEventListener(this);
                platform.info("Discord-first player onboarding enabled");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!identities.linked(player)) begin(player);
                }
                return;
            }
            try { Thread.sleep(500); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
        }
        if (!closed) platform.error("Timed out waiting for DiscordSRV to initialize player onboarding");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!identities.linked(event.getPlayer())) begin(event.getPlayer());
    }

    private void begin(Player player) {
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
        if (discordId != null) {
            awaitingNames.put(discordId, new PendingName(player.getUniqueId(), player.getName()));
            promptForName(discordId, player.getName());
            player.sendMessage("Your Discord is linked. Answer the bot in #linking to finish.");
            return;
        }
        String code = DiscordSRV.getPlugin().getAccountLinkManager().generateCode(player.getUniqueId());
        player.sendMessage("§6§lGenevaMC setup");
        player.sendMessage("§f1. Join Discord: §bhttps://genevamc.net/new");
        player.sendMessage("§f2. Go to §b#rules§f and read the rules.");
        player.sendMessage("§f3. In §b#linking§f, type this code: §e§l" + code);
        player.sendMessage("§f4. Follow the bot's prompts there.");
    }

    @Subscribe
    public void onAccountLinked(AccountLinkedEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (identities.linked(playerId)) return;
        String discordId = event.getUser().getId();
        String ign = event.getPlayer().getName() == null ? playerId.toString() : event.getPlayer().getName();
        awaitingNames.put(discordId, new PendingName(playerId, ign));
        removeUnlinkedRole(discordId);
        promptForName(discordId, ign);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getGuild().getIdLong() != guildId || event.getUser().isBot()) return;
        if (DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getUser().getId()) != null) return;
        Role role = event.getGuild().getRoleById(unlinkedRoleId);
        if (role == null) {
            platform.error("Configured unlinked Discord role is unavailable");
            return;
        }
        event.getGuild().addRoleToMember(event.getMember(), role).queue(
                ignored -> {}, error -> platform.error("Failed to assign unlinked role to " + event.getUser().getId(), error));
    }

    private void removeUnlinkedRole(String discordId) {
        JDA current = jda != null ? jda : DiscordSRV.getPlugin().getJda();
        Guild guild = current == null ? null : current.getGuildById(guildId);
        Role role = guild == null ? null : guild.getRoleById(unlinkedRoleId);
        Member member = guild == null ? null : guild.getMemberById(discordId);
        if (guild == null || role == null || member == null || !member.getRoles().contains(role)) return;
        guild.removeRoleFromMember(member, role).queue(
                ignored -> {}, error -> platform.error("Failed to remove unlinked role from " + discordId, error));
    }

    private void promptForName(String discordId, String ign) {
        TextChannel channel = channel();
        if (channel == null) return;
        channel.sendMessage("<@" + discordId + "> Hey! Your Minecraft account **" + ign
                + "** is linked. Please read **#rules**, then reply here with just your real first and last name.").queue();
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getChannel().getIdLong() != linkingChannelId) return;
        PendingName pending = awaitingNames.get(event.getAuthor().getId());
        if (pending == null) return;
        String name = event.getMessage().getContentRaw().trim();
        if (name.matches("[0-9]{4,8}")) return;
        if (!name.matches("[\\p{L}][\\p{L} .'-]{1,63}")) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention()
                    + " Please reply with your real first and last name (letters, spaces, apostrophes, periods, or hyphens).").queue();
            return;
        }
        awaitingNames.remove(event.getAuthor().getId());
        String ign = pending.ign();
        Player online = Bukkit.getPlayer(pending.playerId());
        if (online != null) ign = online.getName();
        identities.completeDiscordLink(pending.playerId(), ign, name);
        event.getChannel().sendMessage(event.getAuthor().getAsMention()
                + " You're all set! **" + ign + "** can now play on GenevaMC.").queue();
        event.getMessage().delete().queue(null, ignored -> {});
    }

    private TextChannel channel() {
        JDA current = jda != null ? jda : DiscordSRV.getPlugin().getJda();
        TextChannel channel = current == null ? null : current.getTextChannelById(linkingChannelId);
        if (channel == null) platform.error("Configured Discord linking channel is unavailable");
        return channel;
    }

    @Override
    public void close() {
        closed = true;
        DiscordSRV.api.unsubscribe(this);
        if (jda != null) jda.removeEventListener(this);
        awaitingNames.clear();
    }

    private record PendingName(UUID playerId, String ign) {}
}
