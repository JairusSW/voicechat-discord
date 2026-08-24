package dev.amsam0.voicechatdiscord;

import github.scarsz.discordsrv.DiscordSRV;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Persistent real-name onboarding and bridge command integration. */
public final class IdentityRegistry implements Listener, CommandExecutor {
    private final File file;
    private final YamlConfiguration data;

    public IdentityRegistry(PaperPlugin plugin) {
        file = new File(plugin.getDataFolder(), "identities.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    private boolean linked(Player player) {
        return data.isString("players." + player.getUniqueId() + ".real_name");
    }

    private void requireLink(Player player) {
        player.sendMessage(Component.text("Before playing, use /link <real name>. Add --voice to begin Discord voice setup.", NamedTextColor.YELLOW));
    }

    private void enterOnboarding(Player player) {
        String path = "onboarding." + player.getUniqueId();
        if (!data.isString(path + ".previous_game_mode")) {
            data.set(path + ".previous_game_mode", player.getGameMode().name());
            save();
        }
        player.setGameMode(GameMode.SPECTATOR);
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
        data.set(path, null);
        save();
        player.setGameMode(restored);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!linked(event.getPlayer())) enterOnboarding(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!linked(event.getPlayer()) && event.hasChangedBlock()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!linked(event.getPlayer())) event.setCancelled(true);
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
        if (args.length == 0) return false;
        boolean voice = args[args.length - 1].equalsIgnoreCase("--voice");
        String realName = String.join(" ", Arrays.copyOf(args, voice ? args.length - 1 : args.length)).trim();
        if (!realName.matches("[\\p{L}][\\p{L} .'-]{1,63}")) {
            player.sendMessage(Component.text("Use 2–64 letters; spaces, apostrophes, periods, and hyphens are allowed.", NamedTextColor.RED));
            return true;
        }

        String path = "players." + player.getUniqueId();
        data.set(path + ".real_name", realName);
        data.set(path + ".ign", player.getName());
        data.set(path + ".linked_at", Instant.now().toString());
        save();
        leaveOnboarding(player);
        player.sendMessage(Component.text("Linked " + player.getName() + " to " + realName + ". You can now play!", NamedTextColor.GREEN));

        if (voice) {
            String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
            if (discordId == null) {
                player.sendMessage(Component.text("Next run /discord link, post the code in Discord, then join the voice lobby.", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("Your Discord is linked. Join the Discord voice lobby to start proximity chat.", NamedTextColor.GREEN));
            }
        }
        return true;
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
