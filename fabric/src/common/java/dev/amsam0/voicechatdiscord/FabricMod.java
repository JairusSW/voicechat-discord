package dev.amsam0.voicechatdiscord;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static dev.amsam0.voicechatdiscord.Core.*;

public class FabricMod implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        if (platform == null) {
            platform = new FabricPlatform();
        }

        enable();

        FabricVersionSpecific.getCommandDispatcher(dispatcher -> dispatcher.register(SubCommands.build(literal("dvc"))));

        ServerLifecycleEvents.SERVER_STOPPED.register((server -> disable()));
    }
}
