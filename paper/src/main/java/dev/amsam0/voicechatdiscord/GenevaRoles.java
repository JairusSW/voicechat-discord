package dev.amsam0.voicechatdiscord;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** GenevaMC staff roles, teams, display tags, and utility commands. */
public final class GenevaRoles implements Listener, CommandExecutor {
    private final PaperPlugin plugin;
    private final IdentityRegistry identities;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Role> roles = new ConcurrentHashMap<>();
    private final Map<String, Team> teams = new ConcurrentHashMap<>();

    public GenevaRoles(PaperPlugin plugin, IdentityRegistry identities) {
        this.plugin = plugin;
        this.identities = identities;
        file = new File(plugin.getDataFolder(), "roles-teams.yml");
        data = YamlConfiguration.loadConfiguration(file);
        load();
        Bukkit.getOnlinePlayers().forEach(this::updateDisplay);
    }

    private void load() {
        ConfigurationSection roleSection = data.getConfigurationSection("roles");
        if (roleSection != null) {
            for (String id : roleSection.getKeys(false)) {
                try {
                    roles.put(UUID.fromString(id), Role.valueOf(roleSection.getString(id, "MEMBER").toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid role entry for " + id);
                }
            }
        }
        ConfigurationSection teamSection = data.getConfigurationSection("teams");
        if (teamSection != null) {
            for (String key : teamSection.getKeys(false)) {
                String base = "teams." + key;
                String name = data.getString(base + ".name", key);
                String leaderText = data.getString(base + ".leader");
                if (leaderText == null) continue;
                try {
                    UUID leader = UUID.fromString(leaderText);
                    List<UUID> members = new ArrayList<>();
                    for (String member : data.getStringList(base + ".members")) {
                        try { members.add(UUID.fromString(member)); } catch (IllegalArgumentException ignored) {}
                    }
                    if (!members.contains(leader)) members.add(leader);
                    teams.put(key(key), new Team(name, leader, members));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring team with invalid leader: " + key);
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "ping" -> ping(sender, args);
            case "role" -> role(sender, args);
            default -> team(sender, args);
        };
    }

    private boolean ping(CommandSender sender, String[] args) {
        Player target;
        if (args.length == 0 && sender instanceof Player player) {
            target = player;
        } else if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) return error(sender, "That player is not online.");
        } else {
            return error(sender, "Usage: /ping [player]");
        }
        sender.sendMessage(Component.text(target.getName() + "'s ping: ", NamedTextColor.GRAY)
                .append(Component.text(target.getPing() + " ms", pingColor(target.getPing()))));
        return true;
    }

    private boolean role(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            UUID target = resolve(args[1]);
            if (target == null) return error(sender, "Unknown linked player: " + args[1]);
            sender.sendMessage(Component.text(identities.knownIgn(target) + " is " + effectiveRole(target).display + ".", NamedTextColor.YELLOW));
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("set")) return error(sender, "Usage: /role set <player> <owner|staff|member>");
        if (!isOwner(sender)) return error(sender, "Only an Owner can change roles.");
        UUID target = resolve(args[1]);
        if (target == null) return error(sender, "Unknown linked player: " + args[1]);
        Role wanted;
        try { wanted = Role.valueOf(args[2].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return error(sender, "Role must be owner, staff, or member."); }
        if (wanted == Role.MEMBER) roles.remove(target); else roles.put(target, wanted);
        save();
        updateDisplay(target);
        sender.sendMessage(Component.text("Set " + identities.knownIgn(target) + " to " + wanted.display + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean team(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) return list(sender);
        if (action.equals("info")) return info(sender, args);
        if (!(sender instanceof Player) && !isStaff(sender)) return error(sender, "This command requires a player or console.");

        return switch (action) {
            case "add" -> add(sender, args);
            case "remove" -> remove(sender, args);
            case "edit" -> edit(sender, args);
            case "invite" -> invite(sender, args);
            case "kick" -> kick(sender, args);
            default -> error(sender, "Unknown team action. Use /team help.");
        };
    }

    private boolean add(CommandSender sender, String[] args) {
        if (!isStaff(sender)) return error(sender, "Only Staff or an Owner can create teams.");
        if (args.length != 3) return error(sender, "Usage: /team add <team> <leader>");
        if (!validName(args[1])) return error(sender, "Team names must be 2–16 letters, numbers, _ or -.");
        String key = key(args[1]);
        if (teams.containsKey(key)) return error(sender, "That team already exists.");
        UUID leader = resolve(args[2]);
        if (leader == null) return error(sender, "Unknown linked player: " + args[2]);
        if (leadingTeam(leader) != null) return error(sender, "That player already leads another team.");
        removeFromTeam(leader);
        teams.put(key, new Team(args[1], leader, new ArrayList<>(List.of(leader))));
        saveAndRefresh();
        sender.sendMessage(Component.text("Created " + args[1] + " with leader " + identities.knownIgn(leader) + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (!isStaff(sender)) return error(sender, "Only Staff or an Owner can remove teams.");
        if (args.length != 2) return error(sender, "Usage: /team remove <team>");
        Team removed = teams.remove(key(args[1]));
        if (removed == null) return error(sender, "Team not found.");
        saveAndRefresh();
        sender.sendMessage(Component.text("Removed team " + removed.name + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean edit(CommandSender sender, String[] args) {
        if (!isStaff(sender)) return error(sender, "Only Staff or an Owner can edit teams.");
        if (args.length != 4) return error(sender, "Usage: /team edit <team> <name|leader> <value>");
        String oldKey = key(args[1]);
        Team team = teams.get(oldKey);
        if (team == null) return error(sender, "Team not found.");
        if (args[2].equalsIgnoreCase("name")) {
            if (!validName(args[3])) return error(sender, "Team names must be 2–16 letters, numbers, _ or -.");
            String newKey = key(args[3]);
            if (!newKey.equals(oldKey) && teams.containsKey(newKey)) return error(sender, "That team already exists.");
            teams.remove(oldKey);
            team.name = args[3];
            teams.put(newKey, team);
        } else if (args[2].equalsIgnoreCase("leader")) {
            UUID leader = resolve(args[3]);
            if (leader == null) return error(sender, "Unknown linked player: " + args[3]);
            Team led = leadingTeam(leader);
            if (led != null && led != team) return error(sender, "That player already leads another team.");
            removeFromTeam(leader);
            team.leader = leader;
            if (!team.members.contains(leader)) team.members.add(leader);
        } else return error(sender, "Editable fields are name and leader.");
        saveAndRefresh();
        sender.sendMessage(Component.text("Updated team " + team.name + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean invite(CommandSender sender, String[] args) {
        Team team = controlledTeam(sender, args, "invite");
        if (team == null) return true;
        String playerName = args[args.length - 1];
        UUID target = resolve(playerName);
        if (target == null) return error(sender, "Unknown linked player: " + playerName);
        Team led = leadingTeam(target);
        if (led != null && led != team) return error(sender, "That player leads another team and cannot be moved.");
        removeFromTeam(target);
        if (!team.members.contains(target)) team.members.add(target);
        saveAndRefresh();
        sender.sendMessage(Component.text("Added " + identities.knownIgn(target) + " to " + team.name + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean kick(CommandSender sender, String[] args) {
        Team team = controlledTeam(sender, args, "kick");
        if (team == null) return true;
        String playerName = args[args.length - 1];
        UUID target = resolve(playerName);
        if (target == null || !team.members.contains(target)) return error(sender, "That player is not on this team.");
        if (target.equals(team.leader)) return error(sender, "Change the leader or remove the team instead.");
        team.members.remove(target);
        saveAndRefresh();
        sender.sendMessage(Component.text("Removed " + identities.knownIgn(target) + " from " + team.name + ".", NamedTextColor.GREEN));
        return true;
    }

    private Team controlledTeam(CommandSender sender, String[] args, String action) {
        if (args.length < 2 || args.length > 3) {
            error(sender, "Usage: /team " + action + " [team] <player>");
            return null;
        }
        Team team;
        if (args.length == 3) {
            if (!isStaff(sender)) {
                error(sender, "Only Staff or an Owner can specify another team.");
                return null;
            }
            team = teams.get(key(args[1]));
        } else if (sender instanceof Player player) {
            team = teamOf(player.getUniqueId());
        } else {
            error(sender, "Console must specify a team.");
            return null;
        }
        if (team == null) {
            error(sender, "Team not found.");
            return null;
        }
        if (!isStaff(sender) && sender instanceof Player player && !team.leader.equals(player.getUniqueId())) {
            error(sender, "Only your team leader, Staff, or an Owner can do that.");
            return null;
        }
        return team;
    }

    private boolean list(CommandSender sender) {
        if (teams.isEmpty()) return error(sender, "There are no teams yet.");
        sender.sendMessage(Component.text("Teams:", NamedTextColor.GOLD));
        teams.values().stream().sorted(Comparator.comparing(t -> t.name.toLowerCase(Locale.ROOT))).forEach(team ->
                sender.sendMessage(Component.text("[" + team.name + "] ", NamedTextColor.AQUA)
                        .append(Component.text(team.members.size() + " members • leader " + identities.knownIgn(team.leader), NamedTextColor.GRAY))));
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        Team team = args.length == 2 ? teams.get(key(args[1])) : sender instanceof Player player ? teamOf(player.getUniqueId()) : null;
        if (team == null) return error(sender, "Team not found. Use /team info <team>.");
        sender.sendMessage(Component.text("[" + team.name + "]", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Leader: " + identities.knownIgn(team.leader), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Members: " + team.members.stream().map(identities::knownIgn).sorted().reduce((a, b) -> a + ", " + b).orElse("none"), NamedTextColor.GRAY));
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("Team commands", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/team list • /team info [team]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Leader: /team invite <player> • /team kick <player>", NamedTextColor.GRAY));
        if (isStaff(sender)) {
            sender.sendMessage(Component.text("Staff: /team add <team> <leader> • remove <team>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Staff: /team edit <team> <name|leader> <value>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Staff: /team invite|kick <team> <player>", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateDisplay(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Component display = displayTag(event.getPlayer());
        if (display == null) return;
        event.renderer((source, sourceDisplayName, message, viewer) -> display
                .append(Component.text(": ", NamedTextColor.DARK_GRAY)).append(message));
    }

    private Component tag(Team team, String ign) {
        return Component.text("[" + team.name + "] ", NamedTextColor.AQUA)
                .append(Component.text(ign, NamedTextColor.WHITE));
    }

    private Component displayTag(Player player) {
        Role role = effectiveRole(player.getUniqueId());
        if (role == Role.OWNER) {
            return Component.text("[OWNER] ", NamedTextColor.GOLD).append(Component.text(player.getName(), NamedTextColor.WHITE));
        }
        if (role == Role.STAFF) {
            return Component.text("[STAFF] ", NamedTextColor.RED).append(Component.text(player.getName(), NamedTextColor.WHITE));
        }
        Team team = teamOf(player.getUniqueId());
        return team == null ? null : tag(team, player.getName());
    }

    private void updateDisplay(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) updateDisplay(player);
    }

    private void updateDisplay(Player player) {
        player.getScheduler().run(plugin, task -> {
            Component display = displayTag(player);
            player.playerListName(display == null ? Component.text(player.getName()) : display);
        }, null);
    }

    private void saveAndRefresh() {
        save();
        Bukkit.getOnlinePlayers().forEach(this::updateDisplay);
    }

    private synchronized void save() {
        data.set("roles", null);
        roles.forEach((id, role) -> data.set("roles." + id, role.name()));
        data.set("teams", null);
        teams.forEach((key, team) -> {
            data.set("teams." + key + ".name", team.name);
            data.set("teams." + key + ".leader", team.leader.toString());
            data.set("teams." + key + ".members", team.members.stream().map(UUID::toString).toList());
        });
        try { data.save(file); }
        catch (IOException error) { throw new RuntimeException("Failed to save GenevaMC roles and teams", error); }
    }

    private UUID resolve(String ign) {
        Player online = Bukkit.getPlayerExact(ign);
        return online != null ? online.getUniqueId() : identities.findLinkedPlayer(ign);
    }

    private Team teamOf(UUID player) {
        return teams.values().stream().filter(team -> team.members.contains(player)).findFirst().orElse(null);
    }

    private Team leadingTeam(UUID player) {
        return teams.values().stream().filter(team -> team.leader.equals(player)).findFirst().orElse(null);
    }

    private void removeFromTeam(UUID player) {
        teams.values().forEach(team -> {
            if (!team.leader.equals(player)) team.members.remove(player);
        });
    }

    private Role effectiveRole(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOp()) return Role.OWNER;
        return roles.getOrDefault(id, Role.MEMBER);
    }

    private boolean isOwner(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOp() || effectiveRole(player.getUniqueId()) == Role.OWNER;
    }

    private boolean isStaff(CommandSender sender) {
        if (isOwner(sender)) return true;
        return sender instanceof Player player && effectiveRole(player.getUniqueId()) == Role.STAFF;
    }

    private static boolean validName(String name) { return name.matches("[A-Za-z0-9_-]{2,16}"); }
    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    private static NamedTextColor pingColor(int ping) { return ping < 80 ? NamedTextColor.GREEN : ping < 160 ? NamedTextColor.YELLOW : NamedTextColor.RED; }
    private static boolean error(CommandSender sender, String message) { sender.sendMessage(Component.text(message, NamedTextColor.RED)); return true; }

    private enum Role {
        OWNER("Owner"), STAFF("Staff"), MEMBER("Member");
        private final String display;
        Role(String display) { this.display = display; }
    }

    private static final class Team {
        private String name;
        private UUID leader;
        private final List<UUID> members;
        private Team(String name, UUID leader, List<UUID> members) {
            this.name = name;
            this.leader = leader;
            this.members = members;
        }
    }
}
