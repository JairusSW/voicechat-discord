package dev.amsam0.voicechatdiscord;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;

import static dev.amsam0.voicechatdiscord.Core.platform;

@ForgeVoicechatPlugin
public class NeoForgeVoicechatPlugin extends VoicechatPlugin {
    @Override
    public String getPluginId() {
        return NeoForgeMod.MOD_ID;
    }

    @Override
    protected void ensurePlatformInitialized() {
        if (platform == null) {
            platform = new NeoForgePlatform();
        }
    }
}
