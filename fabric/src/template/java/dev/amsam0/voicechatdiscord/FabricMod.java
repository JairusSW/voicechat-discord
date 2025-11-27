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

        //# {% if minecraft_version <= mc_1_18_2 %}
        //# net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> dispatcher.register(SubCommands.build(literal("dvc"))));
        //# {% else %}
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(SubCommands.build(literal("dvc"))));
        //# {% endif %}

        ServerLifecycleEvents.SERVER_STOPPED.register((server -> disable()));
    }
}
