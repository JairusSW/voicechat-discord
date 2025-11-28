package dev.amsam0.voicechatdiscord;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.UUID;
import java.util.function.Consumer;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static dev.amsam0.voicechatdiscord.Core.*;

@Mod(NeoForgeMod.MOD_ID)
public class NeoForgeMod {
    public static final String MOD_ID = "voicechat_discord";

    public static Consumer<UUID> onPlayerLeaveHandler = (ignored) -> {
    };

    public NeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onServerSetup);

        NeoForge.EVENT_BUS.register(this);
    }

    private void onServerSetup(FMLDedicatedServerSetupEvent event) {
        if (platform == null) {
            platform = new NeoForgePlatform();
        }

        enable();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(SubCommands.build(literal("dvc")));
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        onPlayerLeaveHandler.accept(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        disable();
    }
}
