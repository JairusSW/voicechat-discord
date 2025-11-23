package dev.amsam0.voicechatdiscord;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.amsam0.voicechatdiscord.Core.platform;

public class EntityTracker {
    private static final ConcurrentHashMap<UUID, Location> trackedEntityLocations = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> lastNeededTrackedEntityMillis = new ConcurrentHashMap<>();
    private static final long cleanupThresholdMillis = 60000;

    private static Thread cleanupThread = null;
    private static boolean cleanupThreadRunning = false;

    private static void startCleanupThread() {
        if (cleanupThread != null) {
            return;
        }

        cleanupThreadRunning = true;
        cleanupThread = new Thread(() -> {
            platform.debug("EntityTracker cleanup thread starting");
            while (cleanupThreadRunning) {
                platform.debug("Checking for entities to remove - currently tracking " + trackedEntityLocations.size());
                for (var entry : trackedEntityLocations.entrySet()) {
                    var lastNeededMillis = lastNeededTrackedEntityMillis.get(entry.getKey());
                    if (
                            lastNeededMillis == null ||
                                    System.currentTimeMillis() - lastNeededMillis > cleanupThresholdMillis
                    ) {
                        platform.debug("Removing entity " + entry.getKey());
                        trackedEntityLocations.remove(entry.getKey());
                        lastNeededTrackedEntityMillis.remove(entry.getKey());
                    }
                }

                try {
                    Thread.sleep(cleanupThresholdMillis);
                } catch (InterruptedException e) {
                    break;
                }
            }
            platform.debug("EntityTracker cleanup thread ending");
        }, "voicechat-discord: Tracked Entities Cleanup");
        cleanupThread.start();
    }

    public static void stopCleanupThread() {
        if (cleanupThread == null || !cleanupThreadRunning) {
            return;
        }

        try {
            cleanupThreadRunning = false;
            cleanupThread.interrupt(); // this really doesn't help stop the thread
            for (int i = 0; i < 20; i++) {
                if (cleanupThread != null && cleanupThread.isAlive()) {
                    try {
                        platform.debug("waiting for cleanup thread to end");
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    break;
                }
            }
        } catch (Throwable e) {
            platform.error("Failed to stop EntityTracker cleanup thread", e);
        }

        cleanupThread = null;
    }

    public static @Nullable Location getEntityLocation(UUID uuid) {
        lastNeededTrackedEntityMillis.put(uuid, System.currentTimeMillis());
        return trackedEntityLocations.get(uuid);
    }

    public static void requestTracking(UUID uuid) {
        platform.debug("Tracking " + uuid);

        Bukkit.getGlobalRegionScheduler().execute(PaperPlugin.get(), () -> {
            Entity entity = Bukkit.getServer().getEntity(uuid);
            if (entity == null) {
                return;
            }

            entity.getScheduler().execute(PaperPlugin.get(), () -> {
                if (!trackedEntityLocations.containsKey(uuid)) {
                    trackedEntityLocations.put(uuid, entity.getLocation());
                    platform.debug("Got initial location for " + uuid);
                } else {
                    platform.debug("Already had location for " + uuid);
                }
            }, null, 0);
        });

        startCleanupThread();
    }

    public static void updateEntityLocation(UUID uuid, Location location) {
        if (trackedEntityLocations.containsKey(uuid)) {
            trackedEntityLocations.put(uuid, location);
            platform.debugVerbose("Updating location of entity " + uuid);
        }
    }
}
