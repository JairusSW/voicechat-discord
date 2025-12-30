package dev.amsam0.voicechatdiscord;

import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.voicechat.api.Player;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
    public boolean isValidPlayer(CommandContext<?> sender) {
        //# {% if minecraft_version <= mc_1_18_2 %}
        //# try {
        //# {% endif %}

        return ((ServerCommandSource) sender.getSource()).getPlayer() != null;

        //# {% if minecraft_version <= mc_1_18_2 %}
        //# } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
        //#     throw new RuntimeException(e);
        //# }
        //# {% endif %}
    }

    @Override
    public ServerPlayer commandContextToPlayer(CommandContext<?> context) {
        //# {% if minecraft_version <= mc_1_18_2 %}
        //# try {
        //# {% endif %}

        return api.fromServerPlayer(((ServerCommandSource) context.getSource()).getPlayer());

        //# {% if minecraft_version <= mc_1_18_2 %}
        //# } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
        //#     throw new RuntimeException(e);
        //# }
        //# {% endif %}
    }

    @Override
    public @Nullable Position getEntityPosition(ServerLevel level, UUID uuid) {
        ServerWorld world = (ServerWorld) level.getServerLevel();
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
        var serverCommandSource = (ServerCommandSource) sender.getSource();

        //# {% if minecraft_version <= mc_1_16_5 %}
        //# var server = serverCommandSource.getMinecraftServer();
        //# {% else %}
        var server = serverCommandSource.getServer();
        //# {% endif %}

        //# {% if minecraft_version <= mc_1_21_10 %}
        //# return serverCommandSource.hasPermissionLevel(server.getOpPermissionLevel());
        //# {% else %}
        return serverCommandSource.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(server.getOpPermissionLevel().getLevel()));
        //# {% endif %}
    }

    @Override
    public boolean hasPermission(CommandContext<?> sender, String permission) {
        return Permissions.check((ServerCommandSource) sender.getSource(), permission);
    }

    @Override
    public void sendMessage(CommandContext<?> sender, Component... message) {
        //# {% if minecraft_version <= mc_1_18_2 %}
        //# ((ServerCommandSource) sender.getSource()).sendFeedback(toNative(message), false);
        //# {% else %}
        ((ServerCommandSource) sender.getSource()).sendMessage(toNative(message));
        //# {% endif %}
    }

    @Override
    public void sendMessage(Player player, Component... message) {
        //# {% if minecraft_version <= mc_1_18_2 %}
        //# ((ServerPlayerEntity) player.getPlayer()).sendMessage(toNative(message), false);
        //# {% else %}
        ((ServerPlayerEntity) player.getPlayer()).sendMessage(toNative(message));
        //# {% endif %}
    }

    private Text toNative(Component... message) {
        MutableText nativeText = null;

        for (var component : message) {
            MutableText mapped = ((MutableText) Text.of(component.text()))
                    .formatted(switch (component.color()) {
                        case WHITE -> Formatting.WHITE;
                        case RED -> Formatting.RED;
                        case YELLOW -> Formatting.YELLOW;
                        case GREEN -> Formatting.GREEN;
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
            return Text.of((String) null);
        }
        return nativeText;
    }

    @Override
    public String getName(Player player) {
        return ((PlayerEntity) player.getPlayer()).getName().getString();
    }

    @Override
    public void setOnPlayerLeaveHandler(Consumer<UUID> handler) {
        ServerPlayConnectionEvents.DISCONNECT.register((minecraftHandler, server) -> handler.accept(minecraftHandler.player.getUuid()));
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