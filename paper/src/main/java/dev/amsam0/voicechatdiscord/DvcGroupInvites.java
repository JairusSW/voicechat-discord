package dev.amsam0.voicechatdiscord;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.amsam0.voicechatdiscord.Core.api;

/** Makes SVC's familiar invite/join commands usable by Discord-bridge players. */
public final class DvcGroupInvites implements Listener {
    private static final long INVITE_LIFETIME_MS = 5 * 60 * 1000L;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    private record Invite(UUID groupId, UUID inviterId, long expiresAt) {}

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().substring(1).trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("voicechat invite ") || lower.startsWith("dvc invite ") || lower.startsWith("discord invite ")) {
            event.setCancelled(true);
            invite(event.getPlayer(), raw.substring(raw.lastIndexOf(' ') + 1));
        } else if (lower.equals("voicechat join") || lower.equals("dvc join") || lower.equals("discord join")) {
            event.setCancelled(true);
            accept(event.getPlayer());
        } else if (lower.equals("voicechat leave") || lower.equals("dvc leave") || lower.equals("discord leave")) {
            event.setCancelled(true);
            leave(event.getPlayer());
        }
    }

    private VoicechatConnection connection(Player player) {
        return api.getConnectionOf(api.fromServerPlayer(player));
    }

    private void invite(Player inviter, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            inviter.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            inviter.sendMessage(Component.text("You cannot invite yourself.", NamedTextColor.RED));
            return;
        }

        VoicechatConnection inviterConnection = connection(inviter);
        if (inviterConnection == null) {
            inviter.sendMessage(Component.text("Your voice connection is not ready yet. Try again in a moment.", NamedTextColor.RED));
            return;
        }
        Group group = inviterConnection.getGroup();
        if (group == null) {
            group = api.groupBuilder()
                    .setName(inviter.getName() + "'s group")
                    .setType(Group.Type.NORMAL)
                    .setPersistent(false)
                    .build();
            inviterConnection.setGroup(group);
        }

        invites.put(target.getUniqueId(), new Invite(group.getId(), inviter.getUniqueId(), System.currentTimeMillis() + INVITE_LIFETIME_MS));
        inviter.sendMessage(Component.text("Invited " + target.getName() + " to your voice group.", NamedTextColor.GREEN));
        target.sendMessage(Component.text(inviter.getName() + " invited you to a voice group. Type /voicechat join to accept (works with SVC or Discord voice).", NamedTextColor.YELLOW));
    }

    private void accept(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
            player.sendMessage(Component.text("You do not have an active voice-group invite.", NamedTextColor.RED));
            return;
        }
        Group group = api.getGroup(invite.groupId());
        VoicechatConnection playerConnection = connection(player);
        if (group == null || playerConnection == null) {
            player.sendMessage(Component.text("That voice group is no longer available. Ask for another invite.", NamedTextColor.RED));
            return;
        }
        if (playerConnection.getGroup() != null) {
            playerConnection.setGroup(null);
        }
        playerConnection.setGroup(group);
        Player inviter = Bukkit.getPlayer(invite.inviterId());
        player.sendMessage(Component.text("Joined " + group.getName() + ".", NamedTextColor.GREEN));
        if (inviter != null) inviter.sendMessage(Component.text(player.getName() + " joined your voice group.", NamedTextColor.GREEN));
    }

    private void leave(Player player) {
        VoicechatConnection playerConnection = connection(player);
        if (playerConnection == null || playerConnection.getGroup() == null) {
            player.sendMessage(Component.text("You are not in a voice group.", NamedTextColor.RED));
            return;
        }
        playerConnection.setGroup(null);
        player.sendMessage(Component.text("Left the voice group.", NamedTextColor.GREEN));
    }
}
