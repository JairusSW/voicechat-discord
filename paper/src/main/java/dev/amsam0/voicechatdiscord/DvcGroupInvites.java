package dev.amsam0.voicechatdiscord;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.amsam0.voicechatdiscord.Core.api;
import static dev.amsam0.voicechatdiscord.Core.getBotForPlayer;

/** Makes SVC's familiar invite/join commands usable by Discord-bridge players. */
public final class DvcGroupInvites implements Listener, CommandExecutor, TabCompleter {
    private static final long INVITE_LIFETIME_MS = 5 * 60 * 1000L;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    private record Invite(UUID groupId, UUID inviterId, long expiresAt) {}

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().substring(1).trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.equals("vc") || lower.startsWith("vc ")) {
            event.setCancelled(true);
            String arguments = raw.length() == 2 ? "" : raw.substring(3).trim();
            dispatch(event.getPlayer(), arguments.isEmpty() ? new String[0] : arguments.split("\\s+"));
        } else if (lower.startsWith("voicechat invite ") || lower.startsWith("dvc invite ") || lower.startsWith("discord invite ")) {
            event.setCancelled(true);
            invite(event.getPlayer(), raw.substring(raw.lastIndexOf(' ') + 1));
        } else if (lower.equals("voicechat join") || lower.equals("dvc join") || lower.equals("discord join")) {
            event.setCancelled(true);
            accept(event.getPlayer());
        } else if (lower.equals("voicechat leave") || lower.equals("dvc leave") || lower.equals("discord leave")) {
            event.setCancelled(true);
            leave(event.getPlayer());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be used by a player.");
            return true;
        }
        dispatch(player, args);
        return true;
    }

    private void dispatch(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(player);
            return;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "status", "info" -> status(player);
            case "setup" -> setup(player);
            case "start", "stop", "restart" -> player.performCommand("dvc " + (subcommand.equals("restart") ? "start" : subcommand));
            case "whisper", "togglewhisper" -> player.performCommand("dvc togglewhisper");
            case "test" -> {
                if (!player.isOp()) player.sendMessage(Component.text("Only operators can run the voice connectivity test.", NamedTextColor.RED));
                else if (args.length < 2) player.sendMessage(Component.text("Usage: /vc test <player>", NamedTextColor.RED));
                else player.performCommand("voicechat test " + args[1]);
            }
            case "invite" -> {
                if (args.length < 2) player.sendMessage(Component.text("Usage: /vc invite <player>", NamedTextColor.RED));
                else invite(player, args[1]);
            }
            case "accept", "join" -> accept(player);
            case "leave" -> leave(player);
            case "groups", "list" -> player.performCommand("dvc group list");
            case "group" -> {
                if (args.length == 1) player.performCommand("dvc group");
                else player.performCommand("dvc group " + String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "players" -> players(player);
            default -> {
                player.sendMessage(Component.text("Unknown voice subcommand: " + args[0], NamedTextColor.RED));
                help(player);
            }
        }
    }

    private void help(Player player) {
        player.sendMessage(Component.text("Voice chat commands", NamedTextColor.GREEN));
        player.sendMessage(Component.text("/vc status", NamedTextColor.WHITE).append(Component.text(" — show your SVC and Discord status", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/vc setup", NamedTextColor.WHITE).append(Component.text(" — connection instructions", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/vc start|stop|restart", NamedTextColor.WHITE).append(Component.text(" — manage Discord voice", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("/vc invite <player> • /vc accept • /vc leave", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/vc groups • /vc group <create|join|info|leave|remove>", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/vc whisper • /vc players", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/vc test <player>", NamedTextColor.WHITE).append(Component.text(" — operator connectivity test", NamedTextColor.YELLOW)));
    }

    private void setup(Player player) {
        player.sendMessage(Component.text("Voice setup", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Client mod: install Simple Voice Chat and connect normally.", NamedTextColor.WHITE));
        player.sendMessage(Component.text("Discord: link your account, join the prox-lobby voice channel, and the server will move you automatically.", NamedTextColor.WHITE));
        player.sendMessage(Component.text("Both methods share proximity and /vc voice groups.", NamedTextColor.YELLOW));
    }

    private void status(Player player) {
        VoicechatConnection voiceConnection = connection(player);
        DiscordBot bot = getBotForPlayer(player.getUniqueId());
        boolean clientInstalled = voiceConnection != null && voiceConnection.isInstalled();
        boolean discordActive = bot != null && bot.isStarted();
        Group group = voiceConnection == null ? null : voiceConnection.getGroup();
        player.sendMessage(Component.text("Voice status for " + player.getName(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("SVC client: " + (clientInstalled ? "connected" : "not connected"), clientInstalled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Discord bridge: " + (discordActive ? "connected" : "not connected"), discordActive ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Group: " + (group == null ? "none" : group.getName()), group == null ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        if (invites.containsKey(player.getUniqueId())) player.sendMessage(Component.text("Pending invite: yes — use /vc accept", NamedTextColor.YELLOW));
    }

    private void players(Player sender) {
        List<String> connected = Bukkit.getOnlinePlayers().stream().filter(player -> {
            VoicechatConnection voiceConnection = connection(player);
            DiscordBot bot = getBotForPlayer(player.getUniqueId());
            return (voiceConnection != null && voiceConnection.isInstalled()) || (bot != null && bot.isStarted());
        }).map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        sender.sendMessage(Component.text("Voice-connected players: " + (connected.isEmpty() ? "none" : String.join(", ", connected)), NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("help", "status", "setup", "start", "stop", "restart", "invite", "accept", "leave", "groups", "group", "whisper", "players", "test").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("test") && sender.isOp()) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("group")) return List.of("list", "create", "join", "info", "leave", "remove").stream()
                .filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    private VoicechatConnection connection(Player player) {
        return api.getConnectionOf(api.fromServerPlayer(player));
    }

    private void invite(Player inviter, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            inviter.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            inviter.sendMessage(Component.text("You cannot invite yourself.", NamedTextColor.RED));
            return;
        }

        VoicechatConnection inviterConnection = connection(inviter);
        if (inviterConnection == null) {
            inviter.sendMessage(Component.text("Your voice connection is not ready yet. Try again in a moment.", NamedTextColor.RED));
            return;
        }
        Group group = inviterConnection.getGroup();
        if (group == null) {
            group = api.groupBuilder()
                    .setName(inviter.getName() + "'s group")
                    .setType(Group.Type.NORMAL)
                    .setPersistent(false)
                    .build();
            inviterConnection.setGroup(group);
        }

        invites.put(target.getUniqueId(), new Invite(group.getId(), inviter.getUniqueId(), System.currentTimeMillis() + INVITE_LIFETIME_MS));
        inviter.sendMessage(Component.text("Invited " + target.getName() + " to your voice group.", NamedTextColor.GREEN));
        target.sendMessage(Component.text(inviter.getName() + " invited you to a voice group. Type /vc accept to join (works with SVC or Discord voice).", NamedTextColor.YELLOW));
    }

    private void accept(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
            player.sendMessage(Component.text("You do not have an active voice-group invite.", NamedTextColor.RED));
            return;
        }
        Group group = api.getGroup(invite.groupId());
        VoicechatConnection playerConnection = connection(player);
        if (group == null || playerConnection == null) {
            player.sendMessage(Component.text("That voice group is no longer available. Ask for another invite.", NamedTextColor.RED));
            return;
        }
        if (playerConnection.getGroup() != null) {
            playerConnection.setGroup(null);
        }
        playerConnection.setGroup(group);
        Player inviter = Bukkit.getPlayer(invite.inviterId());
        player.sendMessage(Component.text("Joined " + group.getName() + ".", NamedTextColor.GREEN));
        if (inviter != null) inviter.sendMessage(Component.text(player.getName() + " joined your voice group.", NamedTextColor.GREEN));
    }

    private void leave(Player player) {
        VoicechatConnection playerConnection = connection(player);
        if (playerConnection == null || playerConnection.getGroup() == null) {
            player.sendMessage(Component.text("You are not in a voice group.", NamedTextColor.RED));
            return;
        }
        playerConnection.setGroup(null);
        player.sendMessage(Component.text("Left the voice group.", NamedTextColor.GREEN));
    }
}
