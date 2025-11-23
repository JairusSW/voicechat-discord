package dev.amsam0.voicechatdiscord;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class FabricVersionSpecific {
    public static void getCommandDispatcher(Consumer<CommandDispatcher<ServerCommandSource>> dispatcherConsumer) {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> dispatcherConsumer.accept(dispatcher)));
    }

    public static ServerPlayerEntity getPlayerFromCommandSource(ServerCommandSource source) {
        return source.getPlayer();
    }

    public static void sendMessage(ServerCommandSource source, Text text) {
        source.sendMessage(text);
    }

    public static void sendMessage(ServerPlayerEntity source, Text text) {
        source.sendMessage(text);
    }
}
