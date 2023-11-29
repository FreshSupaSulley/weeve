# weeve
Create and host your own personalized Discord music bot that plays YouTube, just for you and your friends.

# Setup
>Note: this requires some basic knowledge of running jar files and using the command line.

weeve runs on Java. Make sure you have Java installed (at least [JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)). Check by running ```java -version``` in the command line.

1. [Create a new Discord bot](#i-dont-know-how-to-make-a-discord-bot).
2. Download the [latest release](https://github.com/FreshSupaSulley/weeve/releases/latest/download/weeve.jar) of *weeve.jar*. Move it into a new folder.
3. Create a *tokens.json* file to hold your Discord bot's generated token, like this:
   ```json
   {"token": "your_bot_token"}
   ```
   You can optionally add a Discord user ID as ```owner_id``` to be notified when the bot goes online.
4. Run this in the command line:
   ```sh
   cd /path/to/tokens-and-weeve
   java -jar weeve.jar
   ```
   weeve will read the *tokens.json* file present in the working directory and use it to run your bot. Allow a few minutes before restarting Discord for the slash commands to appear.

# Commands
#### /play `query`
>Plays a URL, or searches YouTube with a query.

#### /skip `amount`
>Skips the playing song, or an optional number of tracks.

#### /next `query`
>Same as the play command, but inserts the song next in the queue.

#### /reset
>Stops playback and empties the queue.

#### /loop `boolean`
>Toggles looping of the playing track.

#### /queue
>Returns all queue songs.

#### /leave
>Leaves the call.

#### /clean-up
>Deletes up to 50 messages sent by the bot.

# FAQ
### I don't know how to make a Discord bot.
Head to the [discord developer portal](https://discord.com/developers/), create an Application and give it a name. Navigate to the bot settings and do the same. Here, you'll see the option to generate a token. Make sure to write it down, because you won't be able to see it again. To add the bot to your server, navigate to OAuth2 / URL Generator, then click the bot scope. Additional permissions are not required, but if you'd like to use the **/clean-up** command, you'll need to add Manage Messages and Read Message History. Finally, visit the generated URL at the bottom of the page to add it to your servers.

### Is this free?
A couple of servers for me and my friends:
>Yes. You can run weeve on an unused computer or even a Raspberry Pi with a decent internet connection.

More than 10:
>Probably not. If you're experiencing performance issues, you want to look into upgrading the hardware the bot is running on, or finding a hosting service to handle running the bot for you. I prefer PebbleHost which hosts bots for $3/month.

### Should I publicize my bot?
No. weeve is not designed to scale and cannot support hundreds of servers. You would need to reprogram the engine to handle that level of load, and even if you did, you wouldn't be able to verify it and grow past 100 servers as it violates Discord's terms of service. I am not responsible for your bot being taken down if you decide to do so.

### Why?
Since YouTube began forcing popular music bots offline, there's been a need to get that support back. weeve gives users the ability to get YouTube playback back themselves. YouTube has an unparalleled monopoly over online videos, and their attempts at limiting their exposure to the internet is a declaration of war.

### Is there any outside code involved?
No. weeve connects to the Discord API to run your bot without contacting any third-party servers.

# Engine
weeve runs on Java 17 and uses [JDA](https://github.com/discord-jda/JDA) to connect to Discord. It also uses a [fork of LavaPlayer](https://github.com/Demeng7215/lavaplayer-403retry/tree/master) that corrects a 403 YouTube error by [Demeng7215](https://github.com/Demeng7215). weeve is intentionally not built to scale (no sharding) because you cannot verify a bot that violates the Discord terms of service. I have not tested weeve's performance when handling playback for many servers simultaneously.

# Contributing
Feel free to do whatever you want with this repository. If you find a bug, please open an issue or make a push.
