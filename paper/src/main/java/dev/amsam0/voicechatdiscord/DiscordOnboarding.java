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
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
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
import java.time.Duration;
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
    private final Map<UUID, ScheduledTask> reminderTasks = new ConcurrentHashMap<>();
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
            scheduleReminder(player, null);
            return;
        }
        linkingCodes.entrySet().removeIf(entry -> entry.getValue().playerId().equals(player.getUniqueId()));
        String code;
        do { code = String.format("%03d", random.nextInt(1_000)); }
        while (linkingCodes.containsKey(code));
        linkingCodes.put(code, new PendingName(player.getUniqueId(), player.getName()));
        scheduleReminder(player, code);
    }

    private void scheduleReminder(Player player, @Nullable String code) {
        UUID playerId = player.getUniqueId();
        ScheduledTask previous = reminderTasks.remove(playerId);
        if (previous != null) previous.cancel();
        ScheduledTask scheduled = player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!player.isOnline() || identities.linked(playerId)) {
                task.cancel();
                reminderTasks.remove(playerId, task);
                return;
            }
            player.showTitle(Title.title(
                    Component.text("Join the Discord", NamedTextColor.GOLD),
                    Component.text("Then link your account to play", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(16), Duration.ZERO)));
            if (code == null) {
                player.sendMessage("§fComplete the linking process in §b#linking§f.");
                return;
            }
            player.sendMessage("§f1. Join Discord: §bhttps://genevamc.net/new");
            player.sendMessage("§f2. In §b#linking§f, type this code: §e§l" + code);
            player.sendMessage("§f3. Complete the linking process in Discord.");
            player.sendMessage("§f4. Have fun!");
        }, () -> reminderTasks.remove(playerId), 100L, 300L);
        reminderTasks.put(playerId, scheduled);
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

    private void setLinkedNickname(Member member, String ign, String realName) {
        String nickname = member.getUser().getName() + " (" + ign + "/" + realName + ")";
        int codePoints = nickname.codePointCount(0, nickname.length());
        if (codePoints > 32) {
            nickname = nickname.substring(0, nickname.offsetByCodePoints(0, 32));
        }
        member.getGuild().modifyNickname(member, nickname)
                .reason("GenevaMC account linked")
                .queue(
                        ignored -> {},
                        error -> platform.error("Failed to set linked nickname for " + member.getId(), error));
    }

    private void promptForName(String discordId, String ign) {
        TextChannel channel = channel();
        if (channel == null) return;
        channel.sendMessage("<@" + discordId + "> Thanks for accepting the rules. Now reply with just your **first and last name** for "
                + ign + ". It'll help us know who you are.").queue();
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
        ScheduledTask reminder = reminderTasks.remove(pending.playerId());
        if (reminder != null) reminder.cancel();
        genevaRoles.syncDiscordSoon();
        if (DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getAuthor().getId()) == null) {
            DiscordSRV.getPlugin().getAccountLinkManager().link(event.getAuthor().getId(), pending.playerId());
        }
        Member member = event.getMember();
        if (member != null) setLinkedNickname(member, ign, name);
        removeUnlinkedRole(event.getAuthor().getId());
        event.getChannel().sendMessage(event.getAuthor().getAsMention()
                + " You're all set! **" + ign + "** can now play on GenevaMC.").queue();
        event.getMessage().delete().queue(null, ignored -> {});
    }

    private void handleMinecraftCommand(GuildMessageReceivedEvent event) {
        String raw = event.getMessage().getContentRaw().trim().substring(1).trim();
        if (raw.isEmpty()) return;
        String[] parts = raw.split("\\s+");
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);

        if (root.equals("whois")) {
            if (parts.length != 2) {
                event.getChannel().sendMessage(event.getAuthor().getAsMention() + " Usage: `/whois <username>`").queue();
                return;
            }
            String realName = identities.whoisName(parts[1]);
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + (realName == null
                    ? " No linked real name was found for **" + parts[1] + "**."
                    : " **" + parts[1] + "** is **" + realName + "**.")).queue();
            return;
        }

        if (root.equals("ping") && parts.length == 2) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                Player target = Bukkit.getPlayerExact(parts[1]);
                event.getChannel().sendMessage(event.getAuthor().getAsMention() + (target == null
                        ? " **" + parts[1] + "** is not online."
                        : " **" + target.getName() + "** has **" + target.getPing() + " ms** ping.")).queue();
            });
            return;
        }

        if (root.equals("team")) {
            String[] teamArgs = java.util.Arrays.copyOfRange(parts, 1, parts.length);
            String response = genevaRoles.discordTeamSummary(teamArgs);
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + "\n" + response).queue();
            return;
        }

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
        reminderTasks.values().forEach(ScheduledTask::cancel);
        reminderTasks.clear();
    }

    private record PendingName(UUID playerId, String ign) {}
}
