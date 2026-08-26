# GenevaMC patch ledger

Fork: `JairusSW/voicechat-discord`  
Current Geneva base before this patch: `565296a47f70b1f27446af5273983bedf1d68b0d` (`3.2.0-geneva.30`).

## Unified voice commands (`geneva.31`)

- `/vc` is GenevaMC's recommended public command and help/status/setup/group/invite interface.
- User-facing DVC messages now point to `/vc` commands.
- `/vc test <player>` delegates to Simple Voice Chat's connectivity test and is operator-only.
- `/voicechat` and `/dvc` remain functional for compatibility with upstream tutorials. Invite/join/leave aliases continue to enter GenevaMC's shared SVC/Discord group flow.
- Unlinked-player command access uses `/vc` instead of advertising `/dvc`.

## Discord mute controls (`geneva.32`)

- `/vc mute` and `/vc unmute` server-mute or server-unmute the invoking player's linked Discord member while they are connected to voice.
- These control the Discord microphone path. Players using the native SVC client still use their client mute key.

## Prox-lobby mute cleanup (`geneva.33`)

- Members without an online Minecraft player are server-muted only while they remain in prox-lobby.
- Leaving prox-lobby for any other voice channel or disconnecting always clears that temporary server mute.
- Per-member locking and a current-channel check prevent asynchronous lobby handling from re-muting someone after they leave.

## Updating Minecraft/DVC

1. Merge the desired upstream DVC release into the Geneva branch.
2. Preserve `DvcGroupInvites.java`, its registration in `PaperPlugin`, and the user-facing `/vc` strings in `DiscordBot` and `SubCommands`.
3. Resolve API changes against the matching Simple Voice Chat API, then bump the `geneva.N` suffix in `buildSrc/src/main/kotlin/Properties.kt`.
4. Build with Java 25: `./gradlew :paper:compileJava :paper:shadowJar`.
5. Deploy the Paper artifact, restart Folia, and test `/vc help`, status, setup, invite/accept/leave, groups, start/stop, whisper, players, and operator test. Confirm `/dvc` and `/voicechat` compatibility too.
