package dev.amsam0.voicechatdiscord;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.audiolistener.AudioListener;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static dev.amsam0.voicechatdiscord.Core.api;
import static dev.amsam0.voicechatdiscord.Core.platform;

public final class DiscordBot {
    // Make sure to mirror this value on the Rust side (`DiscordBot::reset_senders::DURATION_UNTIL_RESET`)
    private static final int MILLISECONDS_UNTIL_RESET = 1000;
    /**
     * ID for the voice channel the bot is assigned to
     */
    private long vcId;
    /**
     * Pointer to Rust struct
     */
    private final long ptr;
    private final long discordUserId;
    /**
     * The player that this Discord bot is linked to.
     */
    private ServerPlayer player;
    /**
     * The SVC audio sender used to send audio to SVC.
     */
    private AudioSender sender;
    /**
     * A thread that sends opus data to the AudioSender.
     */
    private Thread senderThread;
    /**
     * The last time (unix timestamp) that audio was sent to the audio sender.
     */
    private Long lastTimeAudioProvidedToSVC;
    /**
     * A thread that checks every 500ms if the audio sender, discord encoder and audio source decoders should be reset.
     */
    private Thread resetThread;
    /**
     * The SVC audio listener to listen for outgoing (to Discord) audio.
     */
    private AudioListener listener;
    private int connectionNumber = 0;

    public @Nullable ServerPlayer player() {
        return player;
    }

    public boolean whispering() {
        return sender.isWhispering();
    }

    public void whispering(boolean set) {
        sender.whispering(set);
    }

    private static native long _new(String token, long vcId);

    public DiscordBot(String token, long vcId) {
        this.vcId = vcId;
        String encodedId = token.substring(0, token.indexOf('.'));
        discordUserId = Long.parseLong(new String(Base64.getUrlDecoder().decode(encodedId), StandardCharsets.UTF_8));
        ptr = _new(token, vcId);
    }

    public long discordUserId() {
        return discordUserId;
    }

    private native void _setVoiceChannel(long ptr, long vcId);

    public void setVoiceChannel(long vcId) {
        if (player != null || isStarted()) {
            throw new IllegalStateException("Cannot change voice channel while a bot is in use");
        }
        this.vcId = vcId;
        _setVoiceChannel(ptr, vcId);
    }

    public void logInAndStart(ServerPlayer player) {
        this.player = player;
        if (logIn()) {
            start();
        }
    }

    private native boolean _isStarted(long ptr);

    public boolean isStarted() {
        return _isStarted(ptr);
    }

    private native void _logIn(long ptr) throws Throwable;

    private boolean logIn() {
        try {
            _logIn(ptr);
            platform.debug("Logged into the bot with vc_id " + vcId);
            return true;
        } catch (Throwable e) {
            platform.error("Failed to login to the bot with vc_id " + vcId, e);
            if (player != null) {
                platform.sendMessage(
                        player,
                        // The error message won't contain the token, but let's be safe and not show it to the player
                        Component.red("Failed to login to the bot. Please contact your server owner and ask them to look at the console since they will be able to see the error message.")
                );
                player = null;
            }
            return false;
        }
    }

    private native String _start(long ptr) throws Throwable;

    private void start() {
        try {
            // Note that player could become null later, if the player leaves - that's why we wrap the whole thing in a try-catch
            assert player != null;

            String vcName = _start(ptr);

            var connection = api.getConnectionOf(player);
            assert connection != null; // connection should only be null if the player is not connected to the server

            listener = api.playerAudioListenerBuilder()
                    .setPacketListener(this::handlePacket)
                    .setPlayer(player.getUuid())
                    .build();
            api.registerAudioListener(listener);

            sender = api.createAudioSender(connection);
            if (!api.registerAudioSender(sender)) {
                platform.error("Couldn't register audio sender. The player has the mod installed.");
                // Try-catch just in case sendMessage fails
                try {
                    if (player != null) {
                        platform.sendMessage(
                                player,
                                Component.red("It seems that you have Simple Voice Chat installed on your client. To use the addon, you must not have Simple Voice Chat installed on your client.")
                        );
                    }
                } catch (Throwable e) {
                    platform.error("Couldn't send error message to player", e);
                }
                // Needs to run after sending the message because it will set player to null
                stop();
                return;
            }

            connectionNumber++;

            resetThread = new Thread(() -> {
                var startConnectionNumber = connectionNumber;
                platform.debug("reset thread " + startConnectionNumber + " starting");
                while (true) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                        platform.debug("reset thread " + startConnectionNumber + " interrupted");
                        break;
                    }

                    // Check after sleeping instead of before sleeping
                    if (sender == null || connectionNumber != startConnectionNumber) break;

                    if (lastTimeAudioProvidedToSVC != null && System.currentTimeMillis() - MILLISECONDS_UNTIL_RESET > lastTimeAudioProvidedToSVC) {
                        platform.debugVerbose("resetting sender for player with UUID " + player.getUuid());
                        sender.reset();
                        lastTimeAudioProvidedToSVC = null;
                    }

                    _resetSenders(ptr);
                }
                platform.debug("reset thread " + startConnectionNumber + " ending");
            }, "voicechat-discord: Reset Thread #" + connectionNumber);
            resetThread.start();

            senderThread = new Thread(() -> {
                var startConnectionNumber = connectionNumber;
                platform.debug("sender thread " + startConnectionNumber + " starting");
                while (true) {
                    var data = _blockForSpeakingBufferOpusData(ptr);

                    // Check after blocking instead of before blocking
                    if (sender == null || connectionNumber != startConnectionNumber) break;

                    if (data.length > 0) {
                        sender.send(data);
                        // make sure this is after _blockForSpeakingBufferOpusData - we don't want the time before blocking
                        lastTimeAudioProvidedToSVC = System.currentTimeMillis();
                    }
                }
                platform.debug("sender thread " + startConnectionNumber + " ending");
            }, "voicechat-discord: Sender Thread #" + connectionNumber);
            senderThread.start();

            connection.setConnected(true);

            platform.info("Started voice chat for " + platform.getName(player) + " in channel " + vcName + " with bot with vc_id " + vcId);
            platform.sendMessage(
                    player,
                    Component.green("Started a voice chat! To stop it, use "),
                    Component.white("/dvc stop"),
                    Component.green(". If you are having issues, try restarting the session with "),
                    Component.white("/dvc start"),
                    Component.green(". Please join the following voice channel in discord: "),
                    Component.white(vcName)
            );
        } catch (Throwable e) {
            platform.error("Failed to start voice connection for bot with vc_id " + vcId, e);
            // Try-catch just in case sendMessage fails
            try {
                if (player != null) {
                    platform.sendMessage(
                            player,
                            Component.red("Failed to start voice connection. Please contact your server owner since they will be able to see the error message.")
                    );
                }
            } catch (Throwable e2) {
                platform.error("Couldn't send error message to player", e2);
            }
            // Needs to run after sending the message because it will set player to null
            stop();
        }
    }

    private native void _stop(long ptr) throws Throwable;

    public void stop() {
        // This method is very conservative about failing - we want to always return to a decent state even if things don't work out

        // Help the threads end
        connectionNumber++;

        try {
            if (listener != null) {
                api.unregisterAudioListener(listener);
            }
        } catch (Throwable e) {
            platform.error("Failed to stop bot with vc_id " + vcId + " (listener)", e);
        }

        try {
            if (sender != null) {
                sender.reset();
                api.unregisterAudioSender(sender);
            }
        } catch (Throwable e) {
            platform.error("Failed to stop bot with vc_id " + vcId + " (sender)", e);
        }

        try {
            if (resetThread != null) {
                resetThread.interrupt();
                for (int i = 0; i < 20; i++) {
                    if (resetThread != null && resetThread.isAlive()) {
                        try {
                            platform.debug("waiting for reset thread to end");
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    } else {
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            platform.error("Failed to stop bot with vc_id " + vcId + " (reset thread)", e);
        }

        try {
            if (senderThread != null) {
                senderThread.interrupt(); // this really doesn't help stop the thread
                for (int i = 0; i < 20; i++) {
                    if (senderThread != null && senderThread.isAlive()) {
                        try {
                            platform.debug("waiting for sender thread to end");
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    } else {
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            platform.error("Failed to stop bot with vc_id " + vcId + " (sender thread)", e);
        }

        try {
            if (player != null) {
                var connection = api.getConnectionOf(player);
                // connection should only be null if the player is not connected to the server
                if (connection != null) {
                    connection.setConnected(false);
                }
            }
        } catch (Throwable e) {
            platform.error("Failed to stop bot with vc_id " + vcId + " (connection)", e);
        }

        // Threads are ended, so reset the connection number back to original (it will be incremented in start)
        // This way it doesn't jump from 1 to 3
        connectionNumber--;

        lastTimeAudioProvidedToSVC = null;
        listener = null;
        sender = null;
        resetThread = null;
        senderThread = null;
        player = null;

        // Stop the rust side last so that the state is still Started for any received packets
        try {
            _stop(ptr);
        } catch (Throwable e) {
            platform.error("Failed to stop bot with vc_id " + vcId, e);
        }

        platform.debug("Stopped bot with vc_id " + vcId);
    }

    private native void _free(long ptr);

    /**
     * Safety: the class should be discarded after calling
     */
    public void free() {
        _free(ptr);
    }

    private native void _addAudioToHearingBuffer(long ptr, int senderId, byte[] rawOpusData, boolean adjustBasedOnDistance, double distance, double maxDistance);

    public void handlePacket(SoundPacket packet) {
        UUID senderId = packet.getSender();

        boolean shouldHavePosition = false;
        @Nullable Position position = null;
        double maxDistance = 0.0;
        boolean whispering = false;

        platform.debugExtremelyVerbose("packet is a " + packet.getClass().getSimpleName());
        if (packet instanceof EntitySoundPacket sound) {
            shouldHavePosition = true;
            position = platform.getEntityPosition(player.getServerLevel(), sound.getEntityUuid());
            maxDistance = sound.getDistance();
            whispering = sound.isWhispering();
        } else if (packet instanceof LocationalSoundPacket sound) {
            position = sound.getPosition();
            maxDistance = sound.getDistance();
        } else if (!(packet instanceof StaticSoundPacket)) {
            platform.warn("packet is not LocationalSoundPacket, StaticSoundPacket or EntitySoundPacket, it is " + packet.getClass().getSimpleName() + ". Please report this on GitHub Issues!");
        }

        if (shouldHavePosition && position == null) {
            platform.debug("Position is null when non-null expected for " + senderId);
            return;
        }

        if (whispering) {
            platform.debugExtremelyVerbose("player is whispering, original max distance is " + maxDistance);
            if (api.getServerConfig().hasKey("whisper_distance")) {
                maxDistance = api.getServerConfig().getDouble("whisper_distance", maxDistance);
            } else {
                maxDistance *= api.getServerConfig().getDouble("whisper_distance_multiplier", 1);
            }
            platform.debugExtremelyVerbose("adjusted max distance is " + maxDistance);
        }

        double distance = position != null
                ? distance(position, player.getPosition())
                : 0.0;

        platform.debugExtremelyVerbose("adding audio for " + senderId);

        _addAudioToHearingBuffer(ptr, senderId.hashCode(), packet.getOpusEncodedData(), position != null, distance, maxDistance);
    }

    private static double distance(Position pos1, Position pos2) {
        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        double dz = pos1.getZ() - pos2.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private native byte[] _blockForSpeakingBufferOpusData(long ptr);

    private native void _resetSenders(long ptr);
}
