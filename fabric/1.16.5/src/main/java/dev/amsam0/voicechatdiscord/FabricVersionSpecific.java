package dev.amsam0.voicechatdiscord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class FabricVersionSpecific {
    public static void getCommandDispatcher(Consumer<CommandDispatcher<ServerCommandSource>> dispatcherConsumer) {
        CommandRegistrationCallback.EVENT.register(((dispatcher, dedicated) -> dispatcherConsumer.accept(dispatcher)));
    }

    public static ServerPlayerEntity getPlayerFromCommandSource(ServerCommandSource source) {
        try {
            return source.getPlayer();
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void sendMessage(ServerCommandSource source, Text text) {
        source.sendFeedback(text, false);
    }

    public static void sendMessage(ServerPlayerEntity source, Text text) {
        source.sendMessage(text, false);
    }
}
