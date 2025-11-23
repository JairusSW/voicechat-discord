package dev.amsam0.voicechatdiscord;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static dev.amsam0.voicechatdiscord.Constants.RELOAD_CONFIG_PERMISSION;
import static dev.amsam0.voicechatdiscord.Core.*;
import static dev.amsam0.voicechatdiscord.GroupManager.*;
import static dev.amsam0.voicechatdiscord.util.Util.getArgumentOr;

/**
 * Subcommands for /dvc
 */
public final class SubCommands {
    @SuppressWarnings("unchecked")
    public static <S> LiteralArgumentBuilder<S> build(LiteralArgumentBuilder<S> builder) {
        return (LiteralArgumentBuilder<S>) ((LiteralArgumentBuilder<Object>) builder)
                .then(literal("start").executes(wrapInTry(SubCommands::start)))
                .then(literal("stop").executes(wrapInTry(SubCommands::stop)))
                .then(literal("reloadconfig").executes(wrapInTry(SubCommands::reloadConfig)))
                .then(literal("togglewhisper").executes(wrapInTry(SubCommands::toggleWhisper)))
                .then(literal("group").executes(GroupCommands::help)
                        .then(literal("list").executes(wrapInTry(GroupCommands::list)))
                        .then(literal("create")
                                // Yeah, this is kind of a mess, all because we would have to use a mixin to add a custom ArgumentType
                                // so instead we just use literals for the group type
                                .then(argument("name", string()).executes(wrapInTry(GroupCommands.create(Group.Type.NORMAL)))
                                        .then(argument("password", string()).executes(wrapInTry(GroupCommands.create(Group.Type.NORMAL)))
                                                .then(literal("normal").executes(wrapInTry(GroupCommands.create(Group.Type.NORMAL)))
                                                        .then(argument("persistent", bool()).executes(wrapInTry(GroupCommands.create(Group.Type.NORMAL))))
                                                )
                                                .then(literal("open").executes(wrapInTry(GroupCommands.create(Group.Type.OPEN)))
                                                        .then(argument("persistent", bool()).executes(wrapInTry(GroupCommands.create(Group.Type.OPEN))))
                                                )
                                                .then(literal("isolated").executes(wrapInTry(GroupCommands.create(Group.Type.ISOLATED)))
                                                        .then(argument("persistent", bool()).executes(wrapInTry(GroupCommands.create(Group.Type.ISOLATED))))
                                                )
                                        )
                                )
                        )
                        .then(literal("join")
                                .then(argument("id", integer(1)).executes(wrapInTry(GroupCommands::join))
                                        .then(argument("password", string()).executes(wrapInTry(GroupCommands::join)))
                                )
                        )
                        .then(literal("info").executes(wrapInTry(GroupCommands::info)))
                        .then(literal("leave").executes(wrapInTry(GroupCommands::leave)))
                        .then(literal("remove")
                                .then(argument("id", integer(1)).executes(wrapInTry(GroupCommands::remove)))
                        )
                );
    }

    private static <S> Command<S> wrapInTry(Consumer<CommandContext<?>> function) {
        return (sender) -> {
            try {
                function.accept(sender);
            } catch (Throwable e) {
                platform.error("An error occurred when running a command", e);
                platform.sendMessage(sender, Component.red("An error occurred when running the command. Please check the console or tell your server owner to check the console."));
            }
            return 1;
        };
    }

    private static void start(CommandContext<?> sender) {
        if (!platform.isValidPlayer(sender)) {
            platform.sendMessage(sender, Component.red("You must be a player to use this command!"));
            return;
        }

        ServerPlayer player = platform.commandContextToPlayer(sender);

        DiscordBot bot = getBotForPlayer(player.getUuid(), true);

        DiscordBot botForPlayer = getBotForPlayer(player.getUuid());
        if (botForPlayer != null) {
            if (!botForPlayer.isStarted()) {
                platform.sendMessage(sender, Component.yellow("Your voice chat is currently starting."));
            } else {
                platform.sendMessage(sender, Component.red("You have already started a voice chat! "), Component.yellow("Restarting your session..."));
                new Thread(() -> {
                    botForPlayer.stop();
                    try {
                        // Give some time for any songbird stuff to resolve
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                    botForPlayer.logInAndStart(player);
                }, "voicechat-discord: Bot Restart for " + player.getUuid()).start();
            }
            return;
        }

        if (bot == null) {
            platform.sendMessage(
                    sender,
                    Component.red("There are currently no bots available. You might want to contact your server owner to add more.")
            );
            return;
        }

        platform.sendMessage(sender, Component.yellow("Starting a voice chat..."));

        new Thread(() -> bot.logInAndStart(player), "voicechat-discord: Bot Start for " + player.getUuid()).start();
    }

    private static void stop(CommandContext<?> sender) {
        if (!platform.isValidPlayer(sender)) {
            platform.sendMessage(sender, Component.red("You must be a player to use this command!"));
            return;
        }

        ServerPlayer player = platform.commandContextToPlayer(sender);

        DiscordBot bot = getBotForPlayer(player.getUuid());
        if (bot == null || !bot.isStarted()) {
            platform.sendMessage(player, Component.red("You must start a voice chat before you can use this command!"));
            return;
        }

        platform.sendMessage(player, Component.yellow("Stopping the bot..."));

        new Thread(() -> {
            bot.stop();

            platform.sendMessage(sender, Component.green("Successfully stopped the bot!"));
        }, "voicechat-discord: Bot Stop for " + player.getUuid()).start();
    }

    private static void reloadConfig(CommandContext<?> sender) {
        if (!platform.isOperator(sender) && !platform.hasPermission(
                sender,
                RELOAD_CONFIG_PERMISSION
        )) {
            platform.sendMessage(
                    sender,
                    Component.red("You must be an operator or have the `" + RELOAD_CONFIG_PERMISSION + "` permission to use this command!")
            );
            return;
        }

        platform.sendMessage(sender, Component.yellow("Stopping bots..."));

        new Thread(() -> {
            for (DiscordBot bot : bots)
                if (bot.player() != null)
                    platform.sendMessage(
                            bot.player(),
                            Component.red("The config is being reloaded which stops all bots. Please use "),
                            Component.white("/dvc start"),
                            Component.red(" to restart your session.")
                    );

            clearBots();

            platform.sendMessage(
                    sender,
                    Component.green("Successfully stopped bots! "),
                    Component.yellow("Reloading config...")
            );

            loadConfig();

            platform.sendMessage(
                    sender,
                    Component.green("Successfully reloaded config! Using " + bots.size() + " bot" + (bots.size() != 1 ? "s" : "") + ".")
            );
        }, "voicechat-discord: Reload Config").start();
    }

    private static void toggleWhisper(CommandContext<?> sender) {
        if (!platform.isValidPlayer(sender)) {
            platform.sendMessage(sender, Component.red("You must be a player to use this command!"));
            return;
        }

        ServerPlayer player = platform.commandContextToPlayer(sender);

        DiscordBot bot = getBotForPlayer(player.getUuid());
        if (bot == null || !bot.isStarted()) {
            platform.sendMessage(player, Component.red("You must start a voice chat before you can use this command!"));
            return;
        }

        var set = !bot.whispering();
        bot.whispering(set);

        platform.sendMessage(sender, set ? Component.green("Started whispering!") : Component.green("Stopped whispering!"));
    }

    private static final class GroupCommands {
        private static boolean checkIfGroupsEnabled(CommandContext<?> sender) {
            if (!api.getServerConfig().getBoolean("enable_groups", true)) {
                platform.sendMessage(sender, Component.red("Groups are currently disabled."));
                return true;
            }
            return false;
        }

        @SuppressWarnings("SameReturnValue")
        private static int help(CommandContext<?> sender) {
            platform.sendMessage(
                    sender,
                    Component.red("Available subcommands:\n"),
                    Component.red("- `"), Component.white("/dvc group list"), Component.red("`: List groups\n"),
                    Component.red("- `"), Component.white("/dvc group create <name> [password] [type] [persistent]"), Component.red("`: Create a group\n"),
                    Component.red("- `"), Component.white("/dvc group join <ID>"), Component.red("`: Join a group\n"),
                    Component.red("- `"), Component.white("/dvc group info"), Component.red("`: Get info about your current group\n"),
                    Component.red("- `"), Component.white("/dvc group leave"), Component.red("`: Leave your current group\n"),
                    Component.red("- `"), Component.white("/dvc group remove <ID>"), Component.red("`: Removes a persistent group if there is no one in it\n"),
                    Component.red("See "), Component.white("https://github.com/amsam0/voicechat-discord#dvc-group"), Component.red(" for more info on how to use these commands.")
            );
            return 1;
        }

        private static void list(CommandContext<?> sender) {
            if (checkIfGroupsEnabled(sender)) return;

            Collection<Group> apiGroups = api.getGroups();

            if (apiGroups.isEmpty())
                platform.sendMessage(sender, Component.red("There are currently no groups."));
            else {
                ArrayList<Component> groupsMessage = new ArrayList<>();
                groupsMessage.add(Component.green("Groups:"));

                for (Group group : apiGroups) {
                    groupsMessage.add(Component.white("\n"));

                    int friendlyId = groupFriendlyIds.get(group.getId());
                    platform.debugVerbose("Friendly ID for " + group.getId() + " (" + group.getName() + ") is " + friendlyId);

                    groupsMessage.add(Component.green(" - " + group.getName() + " (ID is " + friendlyId + "): "));

                    if (group.isPersistent()) {
                        groupsMessage.add(group.hasPassword() ? Component.red("Has password") : Component.green("No password"));
                        groupsMessage.add(Component.yellow(", persistent."));
                    } else {
                        groupsMessage.add(group.hasPassword() ? Component.red("Has password.") : Component.green("No password."));
                    }

                    String groupType;
                    if (group.getType() == Group.Type.NORMAL) {
                        groupType = "normal";
                    } else if (group.getType() == Group.Type.OPEN) {
                        groupType = "open";
                    } else if (group.getType() == Group.Type.ISOLATED) {
                        groupType = "isolated";
                    } else {
                        groupType = "unknown";
                    }
                    groupsMessage.add(Component.green(" Group type is " + groupType + ". Players: "));

                    Component playersMessage = Component.red("No players");
                    List<ServerPlayer> players = groupPlayers.get(group.getId());
                    if (players == null) {
                        playersMessage = Component.red("Unable to get players");
                    } else if (!players.isEmpty()) {
                        playersMessage = Component.green(players.stream().map(player -> platform.getName(player)).collect(Collectors.joining(", ")));
                    }
                    groupsMessage.add(playersMessage);
                }

                platform.sendMessage(sender, groupsMessage.toArray(Component[]::new));
            }
        }

        private static Consumer<CommandContext<?>> create(Group.Type type) {
            return (sender) -> {
                if (!platform.isValidPlayer(sender)) {
                    platform.sendMessage(sender, Component.red("You must be a player to use this command!"));
                    return;
                }

                if (checkIfGroupsEnabled(sender)) return;

                String name = sender.getArgument("name", String.class);
                String password = getArgumentOr(sender, "password", String.class, null);
                if (password != null)
                    if (password.trim().isEmpty())
                        password = null;
                Boolean persistent = getArgumentOr(sender, "persistent", Boolean.class, false);
                assert persistent != null;

                VoicechatConnection connection = Objects.requireNonNull(api.getConnectionOf(platform.commandContextToPlayer(sender)));
                if (connection.getGroup() != null) {
                    platform.sendMessage(sender, Component.red("You are already in a group!"));
                    return;
                }

                Group group = api.groupBuilder()
                        .setName(name)
                        .setPassword(password)
                        .setType(type)
                        .setPersistent(persistent)
                        .build();
                connection.setGroup(group);

                platform.sendMessage(sender, Component.green("Successfully created the group!"));
            };
        }

        private static void join(CommandContext<?> sender) {
            if (!platform.isValidPlayer(sender)) {
                platform.sendMessage(sender, Component.red("You must be a player to use this command!"));
                return;
            }

            if (checkIfGroupsEnabled(sender)) return;

            Integer friendlyId = sender.getArgument("id", Integer.class);
            UUID groupId = groupFriendlyIds.getKey(friendlyId);
            if (groupId == null) {
                platform.sendMessage(sender, Component.red("Invalid group ID. Please use "), Component.white("/dvc group list"), Component.red(" to see all groups."));
                return;
            }

            Group group = Objects.requireNonNull(api.getGroup(groupId));
            if (group.hasPassword()) {
                String inputPassword = getArgumentOr(sender, "password", String.class, null);
                if (inputPassword != null)
                    if (inputPassword.trim().isEmpty())
                        inputPassword = null;

                if (inputPassword == null) {
                    platform.sendMessage(sender, Component.red("The group has a password, and you have not provided one. Please rerun the command, including the password."));
                    return;
                }

                String groupPassword = getPassword(group);
                if (groupPassword == null) {
                    platform.sendMessage(sender, Component.red("Since the group has a password, we need to check if the password you supplied is correct. However, we failed to get the password for the group (the server owner can see the error in console). You may need to update Simple Voice Chat Discord Bridge."));
                    return;
                }

                if (!inputPassword.equals(groupPassword)) {
                    platform.sendMessage(sender, Component.red("The password you provided is incorrect. You may want to surround the password in quotes if the password has spaces in it."));
                    return;
                }
            }

            VoicechatConnection connection = Objects.requireNonNull(api.getConnectionOf(platform.commandContextToPlayer(sender)));
            if (connection.getGroup() != null) {
                platform.sendMessage(sender, Component.red("You are already in a group! Leave it using "), Component.white("/dvc group leave"), Component.red(", then join this group."));
                return;
            }
            var botForPlayer = getBotForPlayer(platform.commandContextToPlayer(sender).getUuid());
            if (!connection.isInstalled() && (botForPlayer == null || !botForPlayer.isStarted())) {
                platform.sendMessage(sender,
                        Component.red("You must have the Simple Voice Chat mod installed on your client or use "),
                        Component.white("/dvc start"),
                        Component.red(" before you can use this command!")
                );
                return;
            }
            connection.setGroup(group);

            platform.sendMessage(sender, Component.green("Successfully joined group \"" + group.getName() + "\". Use "), Component.white("/dvc group info"), Component.green(" to see info on the group, and "), Component.white("/dvc group leave"), Component.green(" to leave the group."));
        }

        private static void info(CommandContext<?> sender) {
            if (!platform.isValidPlayer(sender)) {
                platform.sendMessage(sender, Component.red("You must be a player to use this command!"));
                return;
            }

            if (checkIfGroupsEnabled(sender)) return;

            VoicechatConnection connection = Objects.requireNonNull(api.getConnectionOf(platform.commandContextToPlayer(sender)));
            Group group = connection.getGroup();
            if (group == null) {
                platform.sendMessage(sender, Component.red("You are not in a group!"));
                return;
            }

            ArrayList<Component> components = new ArrayList<>();

            components.add(Component.green("You are currently in \"" + group.getName() + "\". It "));

            if (group.isPersistent()) {
                components.add(group.hasPassword() ? Component.red("has a password") : Component.green("does not have a password"));
                components.add(Component.yellow(" and is persistent."));
            } else {
                components.add(group.hasPassword() ? Component.red("has a password.") : Component.green("does not have a password."));
            }

            String groupType;
            if (group.getType() == Group.Type.NORMAL) {
                groupType = "normal";
            } else if (group.getType() == Group.Type.OPEN) {
                groupType = "open";
            } else if (group.getType() == Group.Type.ISOLATED) {
                groupType = "isolated";
            } else {
                groupType = "unknown";
            }
            components.add(Component.green(" Group type is " + groupType + ". Players: "));


            Component playersMessage = Component.red("No players");
            List<ServerPlayer> players = groupPlayers.get(group.getId());
            if (players == null) {
                playersMessage = Component.red("Unable to get players");
            } else if (!players.isEmpty()) {
                playersMessage = Component.green(players.stream().map(player -> platform.getName(player)).collect(Collectors.joining(", ")));
            }
            components.add(playersMessage);

            platform.sendMessage(sender, components.toArray(Component[]::new));
        }

        private static void leave(CommandContext<?> sender) {
            if (!platform.isValidPlayer(sender)) {
                platform.sendMessage(sender, Component.red("You must be a player to use this command!"));
                return;
            }

            if (checkIfGroupsEnabled(sender)) return;

            VoicechatConnection connection = Objects.requireNonNull(api.getConnectionOf(platform.commandContextToPlayer(sender)));
            if (connection.getGroup() == null) {
                platform.sendMessage(sender, Component.red("You are not in a group!"));
                return;
            }
            connection.setGroup(null);

            platform.sendMessage(sender, Component.green("Successfully left the group."));
        }

        private static void remove(CommandContext<?> sender) {
            if (checkIfGroupsEnabled(sender)) return;

            Integer friendlyId = sender.getArgument("id", Integer.class);
            UUID groupId = groupFriendlyIds.getKey(friendlyId);
            if (groupId == null) {
                platform.sendMessage(sender, Component.red("Invalid group ID. Please use "), Component.white("/dvc group list"), Component.red(" to see all groups."));
                return;
            }

            if (!api.removeGroup(groupId)) {
                platform.sendMessage(sender, Component.red("Couldn't remove the group. This means it either has players in it or it is not persistent."));
                return;
            }

            platform.sendMessage(sender, Component.green("Successfully removed the group!"));
        }
    }
}
