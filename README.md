> ⚠️ **Feedback Wanted**
>
> I am looking for feedback on what Minecraft version and server platforms/loaders to support. **Please fill out this short, anonymous Google Form**: https://docs.google.com/forms/d/e/1FAIpQLSfhQhD9tSZK__N1GYHki1t2Zezd6WOd1IuKVqtAxlTQkZf4CA/viewform

> ℹ️ **Note**
>
> This project is in **Maintenance Mode**. This means that I currently do not plan to implement any more features into the addon (PRs are welcome though!). I will try my best to make bug fixes and support the latest Minecraft versions, but I cannot guarantee these fixes will be made in a timely manner, if they are made at all.

# Simple Voice Chat Discord Bridge

[<img alt="Modrinth" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg">](https://modrinth.com/plugin/simple-voice-chat-discord-bridge)
[<img alt="Requires Fabric API" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg">](https://modrinth.com/mod/fabric-api)

> ⚠️ **Warning**
>
> This is not an official addon. **Please don't go to the Simple Voice Chat discord server for support! Instead, please use [GitHub issues](https://github.com/amsam0/voicechat-discord/issues) for support.** I'll try to provide support as soon as possible but there is no guarantee for how long it will take.

Simple Voice Chat Discord Bridge is a Simple Voice Chat addon for Fabric and Paper/Purpur that makes a bridge between Simple Voice Chat and Discord to allow for players without the mod to hear and speak. **For example, this addon allows Bedrock edition players connected through Geyser to use voice chat!**

Changelog: https://gitlab.com/amsam0/voicechat-discord/-/blob/main/CHANGELOG.md

# Project Infrastructure

> ℹ️ **Note**
>
> If you just want to use the addon and don't care about where the source code and other infrastructure is hosted, you can skip this section.

Source code and development is done on GitLab at https://gitlab.com/amsam0/voicechat-discord.

However, because there are a lot more users with GitHub accounts than GitLab accounts, bug reports, feature requests, and general support is done through GitHub at https://github.com/amsam0/voicechat-discord/issues.

In addition, GitHub Actions is used to build and release the addon because I can't be bothered to migrate to GitLab CI. The workflow files are in the GitHub repository at https://github.com/amsam0/voicechat-discord, and you can view workflow run results at https://github.com/amsam0/voicechat-discord/actions. (While using GitHub Actions to clone the GitLab repository arguably reduces security, the GitHub Actions workflows upload the Git repository used to build the release as an artifact to allow for independent analysis. The sha256 hashes of all files and the output of `git log` is also logged.)

# Installation and Usage

First, ensure that you have [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) installed and correctly configured. Please refer to [the Simple Voice Chat wiki](https://modrepo.de/minecraft/voicechat/wiki) for more info.

> ℹ️ **Note**
>
> Simple Voice Chat Discord Bridge requires version 2.4.11 or later of Simple Voice Chat. Please ensure you have updated!

Then, you'll want to [download](https://modrinth.com/mod/simple-voice-chat-discord-bridge/versions) the latest version of Simple Voice Chat Discord Bridge that is compatible with your Minecraft version.

> ℹ️ **Note**
>
> If you are using the Fabric mod, it requires the [Fabric API](https://modrinth.com/mod/fabric-api).

## Finding the configuration file

Make sure to run your server once with Simple Voice Chat and Simple Voice Chat Discord Bridge installed to generate Simple Voice Chat Discord Bridge's configuration file.

**Fabric:** Simple Voice Chat Discord Bridge's configuration file should be located at `config/voicechat-discord.yml`.

**Paper/Purpur:** Simple Voice Chat Discord Bridge's configuration file should be located at `plugins/voicechat-discord/config.yml`.

## Setting up a bot

First, create an application at [discord.com/developers/applications](https://discord.com/developers/applications) by clicking `New Application` at the top right. Choose the name that you want your bot to be called.

![](https://gitlab.com/amsam0/voicechat-discord/-/raw/main/images/new-application.jpg)

On the left, click `Bot`. Copy the token by clicking `Reset Token`. After resetting the token, there should be a `Copy` button.

![](https://gitlab.com/amsam0/voicechat-discord/-/raw/main/images/reset-token.jpg)

Now, open [the configuration file](#finding-the-configuration-file) with a text editor. Replace `DISCORD_BOT_TOKEN_HERE` with the token you copied. It should look something like this:

```yaml
bots:
    - token: TheTokenYouJustPasted
      vc_id: VOICE_CHANNEL_ID_HERE
```

Next, click `Installation` on the left. Change `Install Link` from `Discord Provided Link` to `None`.

![](https://gitlab.com/amsam0/voicechat-discord/-/raw/main/images/remove-install-link.jpg)

Next, click `Bot` on the left. Scroll down and disable disable `Public Bot`.

![](https://gitlab.com/amsam0/voicechat-discord/-/raw/main/images/disable-public-bot.jpg)

Finally, click `General Information` on the left and copy the Application ID.

![](https://gitlab.com/amsam0/voicechat-discord/-/raw/main/images/copy-application-id.jpg)

In your browser, go to `discord.com/api/oauth2/authorize?client_id=YOUR_APPLICATION_ID_HERE&permissions=36700160&scope=bot` but replace `YOUR_APPLICATION_ID_HERE` with the application ID you just copied. Choose the server you want to invite your bot to. **Make sure not to disable any of its permissions.**

Now, follow the steps at [support.discord.com/articles/Where-can-I-find-my-User-Server-Message-ID](https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID-) to enable Developer Mode in Discord. Then, right click the voice channel you want the bot to use as a bridge between Simple Voice Chat and Discord and click `Copy ID`.

> ⚠️ **Warning**
>
> There cannot be more than one person speaking in the voice channel at a time, or there will be weird audio glitches. **We recommend limiting bot voice channels to 2 users to ensure that this does not cause an issue.**

Now, reopen [the configuration file](#finding-the-configuration-file) with a text editor. Replace `VOICE_CHANNEL_ID_HERE` with the channel ID you copied. It should look something like this:

```yaml
bots:
    - token: TheTokenYouJustPasted
      vc_id: TheChannelIDYouJustPasted
```

You can redo this process for however many bots you want. There is some info in [the configuration file](#finding-the-configuration-file) about having multiple bots.

> ℹ️ **Note**
>
> The amount of bots you have is equivalent to the amount of people who can be connected to Simple Voice Chat through Discord at one time. So if you have 3 bots, up to 3 people can use the addon at the same time.

## Troubleshooting

If you run into errors while trying to use a bot, please read the console. The errors will always include the vc_id of the bot, which allows you to identify which bot caused the error.

## Using it in-game

> ⚠️ **Warning**
>
> There cannot be more than one person speaking in the voice channel at a time, or there will be weird audio glitches. **We recommend limiting bot voice channels to 2 users to ensure that this does not cause an issue.**

Most of Simple Voice Chat Discord Bridge's functionality is exposed through the `/dvc` command. This section will go through all of its subcommands.

For commands that take string arguments, you can wrap them in quotes to escape spaces.

### `/dvc start`

Starts a voice chat session using the first available bot. You may have to wait a few seconds for it to start. After it starts, join the voice channel as instructed. You should be able to hear players speak, and other players should be able to hear you speak.

If you are having issues while in a voice chat session, you can try restarting it by using `/dvc start` again.

### `/dvc stop`

Stops the current voice chat session and disconnects the bot.

### `/dvc group`

Allows you to list, create, join, leave and remove groups.

#### `/dvc group list`

Gives you a list of all groups.

#### `/dvc group create <name> [password] [type] [persistent]`

Allows you to create a group.

Arguments:

- `name` (required): The name of the group.
- `password` (optional, defaults to `""` (no password)): The password of the group. If you don't want a password but want to change the group type or persistency, just pass an empty string: `""`
- `type` (optional, defaults to `normal`): Can be `normal`, `open` or `isolated`.
  - `normal`: Players in a group can hear nearby players that are not in a group, but not vice versa
  - `open`: Players in a group can hear nearby players and nearby players can hear players in the group
  - `isolated`: Players in a group can only hear other players in the group
- `persistent` (optional, defaults to `false`): If `true`, the group will not be removed once all players leave. Instead, it has to be removed using [`/dvc group remove <ID>`](#dvc-group-remove-id)

#### `/dvc group join <ID>`

Allows you to join a group using an ID from [`/dvc group list`](#dvc-group-list).

#### `/dvc group info`

Gives you info about your current group.

#### `/dvc group leave`

Allows you to leave your current group.

#### `/dvc group remove <ID>`

Allows you to remove a **persistent** group **with no players in it** using an ID from [`/dvc group list`](#dvc-group-list).

### `/dvc togglewhisper`

Allows you to whisper.

### `/dvc reloadconfig`

If you are a operator (specifically permission level 2 or higher on fabric) or if you have the `voicechat-discord.reload-config` permission, you can use the `/dvc reloadconfig` subcommand to reload the config without have to reload/restart the server. **Using this subcommand will stop all running bots.**
