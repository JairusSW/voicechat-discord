package dev.amsam0.voicechatdiscord;

import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

import static dev.amsam0.voicechatdiscord.Core.api;
import static dev.amsam0.voicechatdiscord.PaperPlugin.LOGGER;
import static dev.amsam0.voicechatdiscord.PaperPlugin.commandHelper;

public class PaperPlatform implements Platform {
    @Override
    public boolean isValidPlayer(CommandContext<?> sender) {
        return commandHelper.bukkitEntity(sender) instanceof Player;
    }

    @Override
    public ServerPlayer commandContextToPlayer(CommandContext<?> context) {
        return api.fromServerPlayer(commandHelper.bukkitEntity(context));
    }

    @Override
    public @Nullable Position getEntityPosition(ServerLevel level, UUID uuid) {
        // DO NOT USE (level.getServerLevel() as World).getEntity(uuid)
        // That requires being on the main thread; Spigot's AsyncCatcher will be triggered
        // We could use a ton of messy reflection to get the moonrise entity lookup,
        // but it's more version friendly to use getPlayer, and if that fails,
        // ask our EntityMoveEvent listener to watch this entity

        Player player = Bukkit.getServer().getPlayer(uuid);
        if (player != null) {
            return api.createPosition(player.getX(), player.getY(), player.getZ());
        }

        Location entityLocation = EntityTracker.getEntityLocation(uuid);
        if (entityLocation != null) {
            return api.createPosition(
                    entityLocation.getX(),
                    entityLocation.getY(),
                    entityLocation.getZ()
            );
        } else {
            EntityTracker.requestTracking(uuid);
            return null;
        }
    }

    @Override
    public boolean isOperator(CommandContext<?> sender) {
        return commandHelper.bukkitSender(sender).isOp();
    }

    @Override
    public boolean hasPermission(CommandContext<?> sender, String permission) {
        return commandHelper.bukkitSender(sender).hasPermission(permission);
    }

    @Override
    public void sendMessage(CommandContext<?> sender, Component... message) {
        if (commandHelper.bukkitEntity(sender) instanceof Player player) {
            player.sendMessage(toNative(message));
        } else {
            commandHelper.bukkitSender(sender).sendMessage(toNative(message));
        }
    }

    @Override
    public void sendMessage(de.maxhenkel.voicechat.api.Player player, Component... message) {
        ((Player) player.getPlayer()).sendMessage(toNative(message));
    }

    public void sendMessage(CommandSender sender, Component... message) {
        sender.sendMessage(toNative(message));
    }

    private net.kyori.adventure.text.Component toNative(Component... message) {
        net.kyori.adventure.text.Component nativeComponent = null;

        for (var component : message) {
            net.kyori.adventure.text.Component mapped = net.kyori.adventure.text.Component.text(
                    component.text(),
                    switch (component.color()) {
                        case WHITE -> NamedTextColor.WHITE;
                        case RED -> NamedTextColor.RED;
                        case YELLOW -> NamedTextColor.YELLOW;
                        case GREEN -> NamedTextColor.GREEN;
                    }
            );
            if (nativeComponent == null) {
                nativeComponent = mapped;
            } else {
                nativeComponent = nativeComponent.append(mapped);
            }
        }

        if (nativeComponent == null) {
            return net.kyori.adventure.text.Component.empty();
        }
        return nativeComponent;
    }

    @Override
    public String getName(de.maxhenkel.voicechat.api.Player player) {
        return ((Player) player.getPlayer()).getName();
    }

    @Override
    public void setOnPlayerLeaveHandler(Consumer<UUID> handler) {
        EventListener.onPlayerLeaveHandler = handler;
    }

    @SuppressWarnings("deprecation")
    @Override
    public @Nullable String getSimpleVoiceChatVersion() {
        Plugin svcPlugin = Bukkit.getServer().getPluginManager().getPlugin("voicechat");
        if (svcPlugin == null) {
            error("Simple Voice Chat plugin is null");
            return null;
        }
        return svcPlugin.getDescription().getVersion();
    }

    @Override
    public String getConfigPath() {
        return "plugins/voicechat-discord/config.yml";
    }

    @Override
    public Loader getLoader() {
        return Loader.PAPER;
    }

    @Override
    public void info(String message) {
        LOGGER.info(message);
    }

    @Override
    public void warn(String message) {
        LOGGER.warn(message);
    }

    @Override
    public void error(String message) {
        LOGGER.error(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }
}
