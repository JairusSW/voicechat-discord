package dev.amsam0.voicechatdiscord;

import github.scarsz.discordsrv.DiscordSRV;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent real-name onboarding and bridge command integration. */
public final class IdentityRegistry implements Listener, CommandExecutor {
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, PendingLink> pendingLinks = new ConcurrentHashMap<>();

    public IdentityRegistry(PaperPlugin plugin) {
        file = new File(plugin.getDataFolder(), "identities.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    private boolean linked(Player player) {
        return data.isString("players." + player.getUniqueId() + ".real_name");
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

    private void requireLink(Player player) {
        player.sendMessage(Component.text("Welcome to GenevaMC! Run ", NamedTextColor.YELLOW)
                .append(Component.text("/link", NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.runCommand("/link"))
                        .hoverEvent(HoverEvent.showText(Component.text("Start setup"))))
                .append(Component.text(" and I'll walk you through setup.", NamedTextColor.YELLOW)));
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

        if (args[0].equalsIgnoreCase("cancel")) {
            pendingLinks.remove(player.getUniqueId());
            player.sendMessage(Component.text("Linking cancelled. Run /link whenever you're ready.", NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("confirm")) {
            PendingLink pending = pendingLinks.remove(player.getUniqueId());
            if (pending == null) {
                player.sendMessage(Component.text("There is no name waiting for confirmation. Run /link to begin.", NamedTextColor.RED));
                return true;
            }
            completeLink(player, pending.realName());
            if (pending.voice()) showVoiceSetup(player); else showVoiceChoice(player);
            return true;
        }

        boolean voice = args[args.length - 1].equalsIgnoreCase("--voice");
        String realName = String.join(" ", Arrays.copyOf(args, voice ? args.length - 1 : args.length)).trim();
        if (!realName.matches("[\\p{L}][\\p{L} .'-]{1,63}")) {
            player.sendMessage(Component.text("Use 2–64 letters; spaces, apostrophes, periods, and hyphens are allowed.", NamedTextColor.RED));
            return true;
        }

        pendingLinks.put(player.getUniqueId(), new PendingLink(realName, voice));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Please confirm your real name:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text(realName, NamedTextColor.WHITE));
        player.sendMessage(Component.text("This will be visible to players through /whois.", NamedTextColor.GRAY));
        player.sendMessage(button("[Confirm]", NamedTextColor.GREEN, "/link confirm")
                .append(Component.space())
                .append(button("[Change it]", NamedTextColor.YELLOW, "/link cancel")));
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
        player.sendMessage(Component.text("1. Enter your real first and last name.", NamedTextColor.WHITE));
        player.sendMessage(Component.text("2. Confirm it. Other players can see it with /whois.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("3. Optionally connect Discord proximity voice.", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Type: ", NamedTextColor.YELLOW)
                .append(Component.text("/link First Last", NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.suggestCommand("/link "))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to fill in the command")))));
    }

    private void completeLink(Player player, String realName) {

        String path = "players." + player.getUniqueId();
        data.set(path + ".real_name", realName);
        data.set(path + ".ign", player.getName());
        data.set(path + ".linked_at", Instant.now().toString());
        save();
        leaveOnboarding(player);
        player.sendMessage(Component.text("Linked " + player.getName() + " to " + realName + ". You can now play!", NamedTextColor.GREEN));
    }

    private void showVoiceChoice(Player player) {
        player.sendMessage(Component.text("Would you like to set up Discord proximity voice? ", NamedTextColor.YELLOW)
                .append(button("[Set up voice]", NamedTextColor.GREEN, "/link voice"))
                .append(Component.space())
                .append(Component.text("[Not now]", NamedTextColor.GRAY)));
    }

    private void showVoiceSetup(Player player) {
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
        if (discordId == null) {
            player.sendMessage(Component.text("Voice setup — step 1 of 2", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Run ", NamedTextColor.YELLOW)
                    .append(button("/discord link", NamedTextColor.WHITE, "/discord link"))
                    .append(Component.text(", then post the code in the Discord linking channel.", NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("After linking, join the Discord voice lobby. The bridge will move you automatically.", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("Voice setup — ready", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Your Discord is linked. Join the Discord voice lobby and the bridge will move you automatically.", NamedTextColor.YELLOW));
        }
    }

    private static Component button(String text, NamedTextColor color, String command) {
        return Component.text(text, color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("Run " + command)));
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

    private record PendingLink(String realName, boolean voice) {}
}
