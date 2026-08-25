package dev.amsam0.voicechatdiscord;

import github.scarsz.discordsrv.DiscordSRV;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent real-name onboarding and bridge command integration. */
public final class IdentityRegistry implements Listener, CommandExecutor {
    private final File file;
    private final YamlConfiguration data;
    private final Set<UUID> onboardingTeleports = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Long> lastJoined = new ConcurrentHashMap<>();

    public IdentityRegistry(PaperPlugin plugin) {
        file = new File(plugin.getDataFolder(), "identities.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean linked(Player player) {
        return data.isString("players." + player.getUniqueId() + ".real_name");
    }

    public boolean linked(UUID playerId) {
        return data.isString("players." + playerId + ".real_name");
    }

    public UUID findLinkedPlayer(String ign) {
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return null;
        for (String id : players.getKeys(false)) {
            if (ign.equalsIgnoreCase(data.getString("players." + id + ".ign", ""))) {
                try {
                    return UUID.fromString(id);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public String knownIgn(UUID playerId) {
        return data.getString("players." + playerId + ".ign", playerId.toString());
    }

    public String whoisName(String ign) {
        UUID playerId = findLinkedPlayer(ign);
        return playerId == null ? null : data.getString("players." + playerId + ".real_name");
    }

    public boolean hasDiscordAccount(String discordId) {
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return false;
        return players.getKeys(false).stream().anyMatch(id ->
                discordId.equals(data.getString("players." + id + ".discord_id")));
    }

    public String discordId(UUID playerId) {
        String stored = data.getString("players." + playerId + ".discord_id");
        if (stored != null) return stored;
        return DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(playerId);
    }

    public Player onlinePlayerForDiscord(String discordId) {
        ConfigurationSection players = data.getConfigurationSection("players");
        Player newest = null;
        long newestJoin = Long.MIN_VALUE;
        if (players != null) {
            for (String id : players.getKeys(false)) {
                if (!discordId.equals(data.getString("players." + id + ".discord_id"))) continue;
                try {
                    UUID uuid = UUID.fromString(id);
                    Player candidate = org.bukkit.Bukkit.getPlayer(uuid);
                    if (candidate != null && candidate.isOnline() && lastJoined.getOrDefault(uuid, 0L) >= newestJoin) {
                        newest = candidate;
                        newestJoin = lastJoined.getOrDefault(uuid, 0L);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return newest;
    }

    private void requireLink(Player player) {
        player.sendMessage(Component.text("Finish linking through the GenevaMC Discord to play.", NamedTextColor.YELLOW));
    }

    private void enterOnboarding(Player player) {
        String path = "onboarding." + player.getUniqueId();
        if (!data.isString(path + ".previous_game_mode")) {
            Location previous = player.getLocation();
            data.set(path + ".previous_game_mode", player.getGameMode().name());
            data.set(path + ".previous_location.world", previous.getWorld().getName());
            data.set(path + ".previous_location.x", previous.getX());
            data.set(path + ".previous_location.y", previous.getY());
            data.set(path + ".previous_location.z", previous.getZ());
            data.set(path + ".previous_location.yaw", previous.getYaw());
            data.set(path + ".previous_location.pitch", previous.getPitch());
            save();
        }
        player.setGameMode(GameMode.SPECTATOR);
        Location current = player.getLocation();
        onboardingTeleports.add(player.getUniqueId());
        player.teleportAsync(new Location(current.getWorld(), current.getX(), 320.0, current.getZ(), current.getYaw(), 0))
                .whenComplete((success, error) -> onboardingTeleports.remove(player.getUniqueId()));
        player.showTitle(Title.title(
                Component.text("Join the Discord", NamedTextColor.GOLD),
                Component.text("Then link your account to play", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(1))));
        requireLink(player);
    }

    private void leaveOnboarding(Player player) {
        String path = "onboarding." + player.getUniqueId();
        String previous = data.getString(path + ".previous_game_mode", GameMode.SURVIVAL.name());
        GameMode restored;
        try {
            restored = GameMode.valueOf(previous);
        } catch (IllegalArgumentException ignored) {
            restored = GameMode.SURVIVAL;
        }
        String worldName = data.getString(path + ".previous_location.world");
        World world = worldName == null ? null : org.bukkit.Bukkit.getWorld(worldName);
        Location destination = world == null ? null : new Location(
                world,
                data.getDouble(path + ".previous_location.x"),
                data.getDouble(path + ".previous_location.y"),
                data.getDouble(path + ".previous_location.z"),
                (float) data.getDouble(path + ".previous_location.yaw"),
                (float) data.getDouble(path + ".previous_location.pitch"));
        data.set(path, null);
        save();
        player.setGameMode(restored);
        if (destination != null) player.teleportAsync(destination);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastJoined.put(event.getPlayer().getUniqueId(), System.nanoTime());
        if (!linked(event.getPlayer())) enterOnboarding(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!linked(event.getPlayer()) && event.hasChangedBlock()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!linked(event.getPlayer()) && !onboardingTeleports.remove(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!linked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (!linked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (!linked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !linked(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !linked(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !linked(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!linked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!linked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        if (!linked(event.getPlayer())) {
            event.setCancelled(true);
            requireLink(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().substring(1);
        String lower = raw.toLowerCase(Locale.ROOT);

        if (lower.equals("discord") || lower.equals("discord voice") || lower.equals("discord start")) {
            event.setCancelled(true);
            event.getPlayer().performCommand("dvc start");
            return;
        }
        if (lower.equals("discord stop")) {
            event.setCancelled(true);
            event.getPlayer().performCommand("dvc stop");
            return;
        }
        if (lower.startsWith("discord group")) {
            event.setCancelled(true);
            event.getPlayer().performCommand("dvc " + raw.substring("discord ".length()));
            return;
        }

        if (!linked(event.getPlayer()) && !lower.startsWith("link ") && !lower.equals("link")) {
            event.setCancelled(true);
            requireLink(event.getPlayer());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("whois")) return whois(sender, args);
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be used by a player.");
            return true;
        }
        return link(player, args);
    }

    private boolean link(Player player, String[] args) {
        if (args.length == 0) {
            showLinkWelcome(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("voice")) {
            if (!linked(player)) {
                player.sendMessage(Component.text("Finish linking your real name first.", NamedTextColor.RED));
                showLinkWelcome(player);
            } else {
                showVoiceSetup(player);
            }
            return true;
        }

        boolean voice = args[args.length - 1].equalsIgnoreCase("--voice");
        String realName = String.join(" ", Arrays.copyOf(args, voice ? args.length - 1 : args.length)).trim();
        if (!realName.matches("[\\p{L}][\\p{L} .'-]{1,63}")) {
            player.sendMessage(Component.text("Use 2–64 letters; spaces, apostrophes, periods, and hyphens are allowed.", NamedTextColor.RED));
            return true;
        }

        completeLink(player, realName);
        if (voice) showVoiceSetup(player); else showVoiceChoice(player);
        return true;
    }

    private void showLinkWelcome(Player player) {
        if (linked(player)) {
            String realName = data.getString("players." + player.getUniqueId() + ".real_name");
            player.sendMessage(Component.text("You're already linked as " + realName + ".", NamedTextColor.GREEN));
            showVoiceChoice(player);
            return;
        }
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("GenevaMC setup", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Enter your real first and last name. Other players can see it with /whois.", NamedTextColor.WHITE));
        player.sendMessage(Component.text("You can optionally connect Discord proximity voice afterward.", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Type: /link First Last", NamedTextColor.YELLOW));
    }

    private void completeLink(Player player, String realName) {
        storeIdentity(player.getUniqueId(), player.getName(), realName);
        leaveOnboarding(player);
        player.sendMessage(Component.text("Linked " + player.getName() + " to " + realName + ". You can now play!", NamedTextColor.GREEN));
    }

    public void completeDiscordLink(UUID playerId, String ign, String realName, String discordId) {
        storeIdentity(playerId, ign, realName);
        data.set("players." + playerId + ".discord_id", discordId);
        save();
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player != null) {
            player.getScheduler().run(PaperPlugin.get(), task -> {
                leaveOnboarding(player);
                player.showTitle(Title.title(
                        Component.text("You're linked!", NamedTextColor.GREEN),
                        Component.text("Welcome to GenevaMC, " + realName, NamedTextColor.GRAY)));
                player.sendMessage(Component.text("Discord setup complete. You can now play!", NamedTextColor.GREEN));
            }, null);
        }
    }

    private void storeIdentity(UUID playerId, String ign, String realName) {
        String path = "players." + playerId;
        data.set(path + ".real_name", realName);
        data.set(path + ".ign", ign);
        data.set(path + ".linked_at", Instant.now().toString());
        save();
    }

    private void showVoiceChoice(Player player) {
        player.sendMessage(Component.text("Optional: type /link voice to set up Discord proximity voice.", NamedTextColor.YELLOW));
    }

    private void showVoiceSetup(Player player) {
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
        if (discordId == null) {
            player.sendMessage(Component.text("Voice setup — step 1 of 2", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Type /discord link, then post the code in the Discord linking channel.", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("After linking, join the Discord voice lobby. The bridge will move you automatically.", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("Voice setup — ready", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Your Discord is linked. Join the Discord voice lobby and the bridge will move you automatically.", NamedTextColor.YELLOW));
        }
    }

    private boolean whois(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        ConfigurationSection players = data.getConfigurationSection("players");
        Set<String> ids = players == null ? Set.of() : players.getKeys(false);
        for (String id : ids) {
            String base = "players." + id;
            if (args[0].equalsIgnoreCase(data.getString(base + ".ign", ""))) {
                sender.sendMessage(data.getString(base + ".ign") + " is " + data.getString(base + ".real_name"));
                return true;
            }
        }
        sender.sendMessage("No linked real name found for " + args[0] + ".");
        return true;
    }

    private synchronized void save() {
        try {
            data.save(file);
        } catch (IOException error) {
            throw new RuntimeException("Failed to save identity registry", error);
        }
    }

}
