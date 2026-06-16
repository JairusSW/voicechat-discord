package dev.amsam0.voicechatdiscord;

import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.voicechat.api.Player;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Consumer;

import static dev.amsam0.voicechatdiscord.Constants.PLUGIN_ID;
import static dev.amsam0.voicechatdiscord.Core.api;

public class FabricPlatform implements Platform {
    private static final Logger LOGGER = LoggerFactory.getLogger(PLUGIN_ID);

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
    public boolean isValidPlayer(CommandContext<?> sender) {
        try {
            return ((CommandSourceStack) sender.getSource()).getPlayerOrException() != null;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return false;
        }
    }

    @Override
    public ServerPlayer commandContextToPlayer(CommandContext<?> context) {
        try {
            return api.fromServerPlayer(((CommandSourceStack) context.getSource()).getPlayerOrException());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isOperator(CommandContext<?> sender) {
        var commandSourceStack = (CommandSourceStack) sender.getSource();

        //# {% if minecraft_version <= mc_1_21_8 %}
        //# var operatorUserPermissionLevel = commandSourceStack.getServer().getOperatorUserPermissionLevel();
        //# {% elif minecraft_version <= mc_1_21_10 %}
        //# var operatorUserPermissionLevel = commandSourceStack.getServer().operatorUserPermissionLevel();
        //# {% else %}
        var operatorUserPermissionLevel = commandSourceStack.getServer().operatorUserPermissions();
        //# {% endif %}

        //# {% if minecraft_version <= mc_1_21_10 %}
        //# return commandSourceStack.hasPermission(operatorUserPermissionLevel);
        //# {% else %}
        return commandSourceStack.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(operatorUserPermissionLevel.level()));
        //# {% endif %}
    }

    @Override
    public boolean hasPermission(CommandContext<?> sender, String permission) {
        return Permissions.check((CommandSourceStack) sender.getSource(), permission);
    }

    @Override
    public void sendMessage(CommandContext<?> sender, Component... message) {
        //# {% if minecraft_version <= mc_1_18_2 %}
        //# ((CommandSourceStack) sender.getSource()).sendSuccess(toNative(message), false);
        //# {% else %}
        ((CommandSourceStack) sender.getSource()).sendSystemMessage(toNative(message));
        //# {% endif %}
    }

    @Override
    public void sendMessage(Player player, Component... message) {
        //# {% if minecraft_version <= mc_1_18_2 %}
        //# ((net.minecraft.server.level.ServerPlayer) player.getPlayer()).displayClientMessage(toNative(message), false);
        //# {% else %}
        ((net.minecraft.server.level.ServerPlayer) player.getPlayer()).sendSystemMessage(toNative(message));
        //# {% endif %}
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
        ServerPlayConnectionEvents.DISCONNECT.register((minecraftHandler, server) -> handler.accept(minecraftHandler.player.getUUID()));
    }

    @Override
    public String getConfigPath() {
        return "config/voicechat-discord.yml";
    }

    protected static final String logPrefixAndFormatPlaceholder = "[" + Constants.PLUGIN_ID + "] {}";

    @Override
    public void info(String message) {
        LOGGER.info(logPrefixAndFormatPlaceholder, message);
    }

    @Override
    public void warn(String message) {
        LOGGER.warn(logPrefixAndFormatPlaceholder, message);
    }

    @Override
    public void error(String message) {
        LOGGER.error(logPrefixAndFormatPlaceholder, message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        LOGGER.error(logPrefixAndFormatPlaceholder, message, throwable);
    }
}