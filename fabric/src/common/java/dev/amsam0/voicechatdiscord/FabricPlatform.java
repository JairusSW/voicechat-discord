package dev.amsam0.voicechatdiscord;

import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.voicechat.api.Player;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
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
        return ((ServerCommandSource) sender.getSource()).getPlayer() != null;
    }

    @Override
    public ServerPlayer commandContextToPlayer(CommandContext<?> context) {
        return api.fromServerPlayer(((ServerCommandSource) context.getSource()).getPlayer());
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
        return ((ServerCommandSource) sender.getSource()).hasPermissionLevel(2);
    }

    @Override
    public boolean hasPermission(CommandContext<?> sender, String permission) {
        return Permissions.check((ServerCommandSource) sender.getSource(), permission);
    }

    @Override
    public void sendMessage(CommandContext<?> sender, Component... message) {
        ((ServerCommandSource) sender.getSource()).sendMessage(toNative(message));
    }

    @Override
    public void sendMessage(Player player, Component... message) {
        ((ServerPlayerEntity) player.getPlayer()).sendMessage(toNative(message));
    }

    private Text toNative(Component... message) {
        MutableText nativeText = null;

        for (var component : message) {
            MutableText mapped = Text
                    .literal(component.text())
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
            return Text.empty();
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
    public @Nullable String getSimpleVoiceChatVersion() {
        ModContainer svcMod = FabricLoader.getInstance().getModContainer("voicechat").orElse(null);
        if (svcMod == null) {
            error("Simple Voice Chat mod is null");
            return null;
        }
        return svcMod.getMetadata().getVersion().toString();
    }

    @Override
    public String getConfigPath() {
        return "config/voicechat-discord.yml";
    }

    @Override
    public Loader getLoader() {
        return Loader.FABRIC;
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