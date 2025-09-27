# Simple Voice Chat Discord Bridge

Simple Voice Chat Discord Bridge is a Simple Voice Chat addon for Fabric and Paper/Purpur that makes a bridge between Simple Voice Chat and Discord to allow for players without the mod to hear and speak. **For example, this addon allows Bedrock edition players connected through Geyser to use voice chat!**

- Modrinth: https://modrinth.com/mod/simple-voice-chat-discord-bridge
- Bug reports, feature requests, and general support: https://github.com/amsam0/voicechat-discord/issues
- Source code (includes real README, changelog, and all source files): https://gitlab.com/amsam0/voicechat-discord

**Please go to the GitLab repository above for the real README** (this is just a placeholder to direct people to the correct place). See the Project Infrastructure section below for more information.

# Project Infrastructure

Source code and development is done on GitLab at https://gitlab.com/amsam0/voicechat-discord.

However, because there are a lot more users with GitHub accounts than GitLab accounts, bug reports, feature requests, and general support is done through GitHub at https://github.com/amsam0/voicechat-discord/issues.

In addition, GitHub Actions is used to build and release the addon because I can't be bothered to migrate to GitLab CI. The workflow files are in the GitHub repository at https://github.com/amsam0/voicechat-discord, and you can view workflow run results at https://github.com/amsam0/voicechat-discord/actions. (While using GitHub Actions to clone the GitLab repository arguably reduces security, the GitHub Actions workflows upload the Git repository used to build the release as an artifact to allow for independent analysis. The sha256 hashes of all files and the output of `git log` is also logged.)
