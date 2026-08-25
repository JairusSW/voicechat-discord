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
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import static dev.amsam0.voicechatdiscord.Core.platform;

/** Discord-first account linking and real-name onboarding. */
public final class DiscordOnboarding extends ListenerAdapter implements Listener, AutoCloseable {
    private final PaperPlugin plugin;
    private final IdentityRegistry identities;
    private final GenevaRoles genevaRoles;
    private final long linkingChannelId;
    private final long minecraftChatChannelId;
    private final long guildId;
    private final long unlinkedRoleId;
    private final Map<String, PendingName> awaitingNames = new ConcurrentHashMap<>();
    private final Map<String, PendingName> awaitingRuleAcceptance = new ConcurrentHashMap<>();
    private final Map<String, PendingName> linkingCodes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private volatile JDA jda;
    private volatile boolean closed;

    private DiscordOnboarding(PaperPlugin plugin, IdentityRegistry identities, GenevaRoles genevaRoles, long guildId, long linkingChannelId, long minecraftChatChannelId, long unlinkedRoleId) {
        this.plugin = plugin;
        this.identities = identities;
        this.genevaRoles = genevaRoles;
        this.guildId = guildId;
        this.linkingChannelId = linkingChannelId;
        this.minecraftChatChannelId = minecraftChatChannelId;
        this.unlinkedRoleId = unlinkedRoleId;
    }

    public static @Nullable DiscordOnboarding start(PaperPlugin plugin, IdentityRegistry identities, GenevaRoles genevaRoles) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        if (!config.getBoolean("discord_onboarding.enabled", false)) return null;
        long channelId = config.getLong("discord_onboarding.linking_channel_id", 0L);
        long minecraftChatChannelId = config.getLong("discord_onboarding.minecraft_chat_channel_id", 0L);
        long guildId = config.getLong("discord_onboarding.guild_id", 0L);
        long roleId = config.getLong("discord_onboarding.unlinked_role_id", 0L);
        if (channelId == 0L || minecraftChatChannelId == 0L || guildId == 0L || roleId == 0L) {
            platform.error("discord_onboarding requires guild_id, linking_channel_id, minecraft_chat_channel_id, and unlinked_role_id");
            return null;
        }
        Plugin discordSrv = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discordSrv == null || !discordSrv.isEnabled()) {
            platform.error("discord_onboarding is enabled, but DiscordSRV is unavailable");
            return null;
        }
        DiscordOnboarding onboarding = new DiscordOnboarding(plugin, identities, genevaRoles, guildId, channelId, minecraftChatChannelId, roleId);
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
            PendingName pending = new PendingName(player.getUniqueId(), player.getName());
            awaitingRuleAcceptance.put(discordId, pending);
            promptForRules(discordId, pending.ign());
            player.sendMessage("Your Discord is linked. Review the rules in #linking to finish.");
            return;
        }
        linkingCodes.entrySet().removeIf(entry -> entry.getValue().playerId().equals(player.getUniqueId()));
        String code;
        do { code = String.format("%03d", random.nextInt(1_000)); }
        while (linkingCodes.containsKey(code));
        linkingCodes.put(code, new PendingName(player.getUniqueId(), player.getName()));
        player.sendMessage("§6§lGenevaMC setup");
        player.sendMessage("§f1. Join Discord: §bhttps://genevamc.net/new");
        player.sendMessage("§f2. In §b#linking§f, type this code: §e§l" + code);
        player.sendMessage("§f3. Read the rules and reply §ayes §for §cno§f.");
        player.sendMessage("§f4. Enter your real name when prompted.");
    }

    @Subscribe
    public void onAccountLinked(AccountLinkedEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (identities.linked(playerId)) return;
        String discordId = event.getUser().getId();
        String ign = event.getPlayer().getName() == null ? playerId.toString() : event.getPlayer().getName();
        PendingName pending = new PendingName(playerId, ign);
        awaitingRuleAcceptance.put(discordId, pending);
        promptForRules(discordId, ign);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getGuild().getIdLong() != guildId || event.getUser().isBot()) return;
        if (identities.hasDiscordAccount(event.getUser().getId())
                || DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getUser().getId()) != null) return;
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
        channel.sendMessage("<@" + discordId + "> Thanks for accepting the rules. Now reply with just your real first and last name for **"
                + ign + "**.").queue();
    }

    private void promptForRules(String discordId, String ign) {
        TextChannel channel = channel();
        if (channel == null) return;
        channel.sendMessage("<@" + discordId + "> Code accepted for Minecraft account **" + ign + "**.\n\n"
                + "**GenevaMC Rules**\n"
                + "1. Be respectful to each other.\n"
                + "2. Don't ruin each other's builds.\n"
                + "3. Participate in and respect player government.\n"
                + "4. Don't use profane language.\n\n"
                + "Do you understand and agree to follow these rules? Reply with **yes** or **no**.").queue();
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (event.getChannel().getIdLong() == minecraftChatChannelId
                && event.getMessage().getContentRaw().trim().startsWith("/")) {
            handleMinecraftCommand(event);
            return;
        }
        if (event.getChannel().getIdLong() != linkingChannelId) return;
        String content = event.getMessage().getContentRaw().trim();
        PendingName codeAccount = linkingCodes.remove(content.toUpperCase(java.util.Locale.ROOT));
        if (codeAccount != null) {
            if (awaitingNames.containsKey(event.getAuthor().getId()) || awaitingRuleAcceptance.containsKey(event.getAuthor().getId())) {
                linkingCodes.put(content.toUpperCase(java.util.Locale.ROOT), codeAccount);
                event.getChannel().sendMessage(event.getAuthor().getAsMention()
                        + " Finish the current account's name prompt before linking another account.").queue();
                return;
            }
            awaitingRuleAcceptance.put(event.getAuthor().getId(), codeAccount);
            promptForRules(event.getAuthor().getId(), codeAccount.ign());
            return;
        }
        PendingName rulesPending = awaitingRuleAcceptance.get(event.getAuthor().getId());
        if (rulesPending != null) {
            if (content.equalsIgnoreCase("yes")) {
                awaitingRuleAcceptance.remove(event.getAuthor().getId());
                awaitingNames.put(event.getAuthor().getId(), rulesPending);
                promptForName(event.getAuthor().getId(), rulesPending.ign());
            } else if (content.equalsIgnoreCase("no")) {
                awaitingRuleAcceptance.remove(event.getAuthor().getId());
                event.getChannel().sendMessage(event.getAuthor().getAsMention()
                        + " Onboarding cancelled. Rejoin Minecraft when you're ready to review the rules again.").queue();
            } else {
                event.getChannel().sendMessage(event.getAuthor().getAsMention()
                        + " Please respond with **yes** to accept the rules or **no** to cancel.").queue();
            }
            return;
        }
        PendingName pending = awaitingNames.get(event.getAuthor().getId());
        if (pending == null) return;
        String name = content;
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
        identities.completeDiscordLink(pending.playerId(), ign, name, event.getAuthor().getId());
        genevaRoles.syncDiscordSoon();
        if (DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getAuthor().getId()) == null) {
            DiscordSRV.getPlugin().getAccountLinkManager().link(event.getAuthor().getId(), pending.playerId());
        }
        removeUnlinkedRole(event.getAuthor().getId());
        event.getChannel().sendMessage(event.getAuthor().getAsMention()
                + " You're all set! **" + ign + "** can now play on GenevaMC.").queue();
        event.getMessage().delete().queue(null, ignored -> {});
    }

    private void handleMinecraftCommand(GuildMessageReceivedEvent event) {
        String raw = event.getMessage().getContentRaw().trim().substring(1).trim();
        if (raw.isEmpty()) return;
        Player player = identities.onlinePlayerForDiscord(event.getAuthor().getId());
        if (player == null) {
            UUID primary = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getAuthor().getId());
            if (primary != null) player = Bukkit.getPlayer(primary);
        }
        if (player == null || !player.isOnline()) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention()
                    + " Join GenevaMC with a linked account before running Minecraft commands here.").queue();
            return;
        }
        String root = raw.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        java.util.Set<String> publicCommands = java.util.Set.of("ping", "team", "whois", "discord", "dvc");
        if (!player.isOp() && !publicCommands.contains(root)) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention()
                    + " You may use `/ping`, `/team`, `/whois`, and `/discord` here. Other commands require Minecraft OP.").queue();
            return;
        }
        Player target = player;
        target.getScheduler().run(plugin, task -> {
            boolean accepted = target.performCommand(raw);
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + (accepted
                    ? " Ran `/" + raw.replace("`", "") + "` as **" + target.getName() + "**. Check Minecraft for the output."
                    : " Minecraft did not recognize that command.")).queue();
        }, null);
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
        awaitingRuleAcceptance.clear();
        linkingCodes.clear();
    }

    private record PendingName(UUID playerId, String ign) {}
}
