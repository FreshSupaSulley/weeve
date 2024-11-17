# weeve ♪
Run your Discord bot with weeve to play YouTube in voice calls.

- [x] Supports YouTube in voice calls
- [x] Can search for videos and play links
- [x] Customizable bot avatar
- [x] Basic commands
- [ ] Free cloud hosting service
- [ ] Scalable

**weeve is a bot engine, not a bot**. You run your own bot on weeve.

# Setup
>[!NOTE]
>This requires basic knowledge of running jars, using the command line, and JSON.

weeve runs on Java 11, which should be backwards compatible with newer versions. Make sure you have Java installed by running ```java -version```.

1. [Create a new Discord bot](#q-i-dont-know-how-to-make-a-discord-bot).
2. Download the [latest release](https://github.com/FreshSupaSulley/weeve/releases/latest) of *weeve.jar*. Move it into a new folder.
3. Create a *tokens.json* file inside the folder to hold your Discord bot's generated token, like this:
   ```json
   {"token": "your_bot_token"}
   ```
4. Run this in the command line:
   ```sh
   cd /path/to/folder
   java -jar weeve.jar
   ```
   weeve will read the *tokens.json* file in the working directory and use it to run your bot. Allow a few minutes before restarting Discord for the slash commands to appear.

# Linking to YouTube
Thanks to Google's war against bots on YouTube, support for YouTube videos are (currently) cutting-edge. Since YouTube TV OAuth no longer works, you need to provide a `po-token` and `visitor-data` to connect to YouTube. If you chose to not provide a YouTube token, the bot runs on SoundCloud by default.

> [!WARNING]
> I **strongly recommend** you use a burner account to link with Google. The account used to generated tokens may get flagged and you risk getting your Google account banned. Visit [YouTube Plugin](https://github.com/lavalink-devs/youtube-source?tab=readme-ov-file#using-a-potoken) for details.

# Commands
| Name | Description |
| --- | --- |
| /play `**string**: query` | Plays a URL, or searches with a query. |
| /yttoken `**string**: po-token` `**string**: visitor-data` | Globally links the bot up to YouTube across all servers, enabling YouTube support. |
| /skip `**integer**: amount` `*optional* **boolean**: next` | Skips the playing song, or an optional number of tracks. Includes option to skip the queue and play next. |
| /reset | Stops playback and empties the queue. |
| /loop `**boolean**: loop` | Toggles looping of the playing track. |
| /queue | Returns all queued songs. |
| /leave | Leaves the call. |
| /clean-up | Deletes up to 50 messages sent by the bot.<br>*\*Requires Manage Messages and Read Message History* |

## Operator Commands
Only available to the bot owner. Used for logging and debugging.
| Name | Description |
| --- | --- |
| /logs list | Lists all log files |
| /logs get | Gets a log file |
| /logs clear | Deletes old log files |

**/clean-up** also works here.

# FAQ
### Q: I don't know how to make a Discord bot.
Head to the [discord developer portal](https://discord.com/developers/), create an Application and give it a name. Navigate to the bot settings and do the same. Here, you'll see the option to generate a token. Make sure to write it down, because you won't be able to see it again. To add the bot to your server, navigate to OAuth2 / URL Generator, then click the bot scope. Additional permissions are not required, but if you'd like to use the **/clean-up** command, you'll need to add Manage Messages and Read Message History. Finally, visit the generated URL at the bottom of the page to add it to your servers.

### Q: How do I host my own bot?
#### A couple of servers for me and my friends:
You can run weeve on an unused computer or even a Raspberry Pi with a decent internet connection without paying for a hosting service. You can also run it in the background without strain.

#### More than 10 servers:
You might experience performance issues, so you'll want to look into upgrading the hardware the bot is running on. Hosting services can handle running the bot for you, I've had a good experience with [PebbleHost](https://pebblehost.com/bot-hosting) ($3/month). Keep in mind that simultaneous service across multiple servers has not been thoroughly tested.

### Q: Should I publicize my bot?
No, weeve is not designed to scale and cannot support hundreds of servers. You would need to reprogram the engine to handle that level of load, and you would have trouble verifying it as it violates Discord's terms of service.

### Q: Why host your own?
Since YouTube took down popular music bots, there's been a need to get that support back. weeve allows you to get long-term YouTube support back by giving you the tools to host a bot yourself without the worry of your favorite YouTube bot getting taken down.

### Q: Are there any third-party servers?
No. weeve connects to the Discord API to run your bot without contacting any third-party servers.

# Run options
### 1. Receive a DM when online
You can add ```owner_id``` to *tokens.json* to be DM'd when the bot goes online. This way, you are notified if the bot has downtime, lost power, or had to restart.
> [!TIP]
> To find your owner ID, enable Developer Mode in the Discord advanced settings, then click on your profile to reveal *Copy User ID*.
```json
{"token": "your_bot_token", "owner_id": "your_owner_id"}
```
*You'll need to add your bot to a server you're in before you're able to receive DMs. You can clear DMs with /clean-up.*

### 2. Point to an external tokens file
Point to an external file outside the running directory using `--file` as a command line argument: `java -jar weeve.jar --file=/path/to/tokens`

### 3. Run without tokens.json
Use command line arguments to avoid using a tokens file: `java -jar weeve.jar --token=your_bot_token --owner_id=your_owner_id`

### 4. Error handling
Setting `notify_errors` in *tokens.json* to `true` or as a command line argument (`--notify_errors`) will allow the user at the `owner_id` to receive notifications if errors occur. Detailed logs are recorded in the running directory.

# Dependencies
weeve runs on Java 11 and uses [JDA](https://github.com/discord-jda/JDA) to connect to Discord. It uses [Lavalink's LavaPlayer](https://github.com/lavalink-devs/Lavalink) and Lavalink's [YouTube source manager plugin](https://github.com/lavalink-devs/youtube-source). weeve is intentionally not built to scale (no sharding), partly because you can't verify a bot that violates the Discord terms of service, but mostly because it's too much work. I have not tested weeve's performance when simultaneously handling playback for many servers.

# Contributing
Feel free to do whatever you want with this repository. If you find a bug, please open an issue with the logs attached.
