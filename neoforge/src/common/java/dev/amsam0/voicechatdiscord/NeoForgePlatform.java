package dev.amsam0.voicechatdiscord;

import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.voicechat.api.Player;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.amsam0.voicechatdiscord.Constants.PLUGIN_ID;
import static dev.amsam0.voicechatdiscord.Core.api;

public class NeoForgePlatform implements Platform {
    private static final Logger LOGGER = LoggerFactory.getLogger(PLUGIN_ID);

    @Override
    public boolean isValidPlayer(CommandContext<?> sender) {
        return ((CommandSourceStack) sender.getSource()).getPlayer() != null;
    }

    @Override
    public ServerPlayer commandContextToPlayer(CommandContext<?> context) {
        return api.fromServerPlayer(((CommandSourceStack) context.getSource()).getPlayer());
    }

    @Override
    public @Nullable Position getEntityPosition(ServerLevel level, UUID uuid) {
        net.minecraft.server.level.ServerLevel world = (net.minecraft.server.level.ServerLevel) level.getServerLevel();
        Entity entity = world.getEntity(uuid);
        if (entity == null) {
            return null;
        }
        return api.createPosition(
                entity.getX(),
                entity.getY(),
                entity.getZ()
        );
    }

    @Override
    public boolean isOperator(CommandContext<?> sender) {
        var commandSourceStack = (CommandSourceStack) sender.getSource();
        return commandSourceStack.hasPermission(commandSourceStack.getServer().operatorUserPermissionLevel());
    }

    @Override
    public boolean hasPermission(CommandContext<?> sender, String permission) {
        net.minecraft.server.level.ServerPlayer player = ((CommandSourceStack) sender.getSource()).getPlayer();
        if (player == null) {
            return false;
        }
        String[] permissionSplit = permission.split("\\.");
        PermissionNode<Boolean> node = new PermissionNode<>(
                permissionSplit[0],
                String.join(".", Arrays.copyOfRange(permissionSplit, 1, permissionSplit.length)),
                PermissionTypes.BOOLEAN,
                (player2, uuid, context) -> Boolean.FALSE
        );
        return PermissionAPI.getPermission(player, node);
    }

    @Override
    public void sendMessage(CommandContext<?> sender, Component... message) {
        ((CommandSourceStack) sender.getSource()).sendSystemMessage(toNative(message));
    }

    @Override
    public void sendMessage(Player player, Component... message) {
        ((net.minecraft.server.level.ServerPlayer) player.getPlayer()).sendSystemMessage(toNative(message));
    }

    private net.minecraft.network.chat.Component toNative(Component... message) {
        MutableComponent nativeText = null;

        for (var component : message) {
            MutableComponent mapped = ((MutableComponent) net.minecraft.network.chat.Component.nullToEmpty(component.text()))
                    .withStyle(switch (component.color()) {
                        case WHITE -> ChatFormatting.WHITE;
                        case RED -> ChatFormatting.RED;
                        case YELLOW -> ChatFormatting.YELLOW;
                        case GREEN -> ChatFormatting.GREEN;
                    });
            if (nativeText == null) {
                nativeText = mapped;
            } else {
                nativeText = nativeText.append(mapped);
            }
        }

        if (nativeText == null) {
            // Cast is needed on newer versions
            //noinspection RedundantCast
            return net.minecraft.network.chat.Component.nullToEmpty((String) null);
        }
        return nativeText;
    }

    @Override
    public String getName(Player player) {
        return ((net.minecraft.world.entity.player.Player) player.getPlayer()).getName().getString();
    }

    @Override
    public void setOnPlayerLeaveHandler(Consumer<UUID> handler) {
        NeoForgeMod.onPlayerLeaveHandler = handler;
    }

    @Override
    public String getConfigPath() {
        return "config/voicechat-discord.yml";
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
