# weeve
Host your own Discord music bot that plays YouTube again.

# Setup
Make sure you have Java installed (at least [JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)). Check by running ```java -version```.

1. Download the [latest release](https://github.com/FreshSupaSulley/weeve/releases/latest/download/weeve.jar) of weeve. Move it into a new folder.
2. Create a *tokens.json* file to hold your bot information, like this:
   ```json
   {"token": "your_bot_token"}
   ```
   ```token``` is your Discord bot's generated token. You can optionally put your Discord user ID as ```owner_id``` to be notified when the bot goes online.
3. Run the following on your command line:
   ```sh
   cd /path/to/folder
   java -jar weeve.jar
   ```
   weeve will read the *tokens.json* file present in the working directory and use the token to start your bot. Allow a few minutes before restarting Discord for the slash commands to appear.

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
### Why?
Since YouTube began forcing popular music bots offline, there's been a need to get that support back. There can't be another big-name music bot, but there can be an underground army of small ones. Weeve is that bot engine that makes support possible again. YouTube has an unparalleled monopoly over online videos, and their attempts at limiting their exposure to the internet is a declaration of war.

### Is there any outside servers involved?
Nope, weeve only connects to the Discord API to run your bot.

### I don't know how to make a Discord bot.
It's easy. Head to the [discord developer portal](https://discord.com/developers/), create an Application, and give it a name. Navigate to the bot settings and do the same. Here, you'll see the option to generate a token. Make sure to write it down, because you won't be able to see it again. To add the bot to your server, navigate to OAuth2 / URL Generator, then click the bot scope. Additional permissions are not required, but if you'd like to use the **/clean-up** command, you'll need to add Manage Messages and Read Message History. Finally, visit the generated URL at the bottom of the page to add it to your servers.

# Development
