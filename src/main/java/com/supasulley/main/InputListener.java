package com.supasulley.main;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import com.supasulley.music.AudioHandler;
import com.supasulley.music.GuildMusicManager;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.GatewayPingEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public class InputListener extends ListenerAdapter {
	
	private static final int MAX_CLEARS = 50;
	private static final File BASE_PATH = new File(System.getProperty("user.dir"));
	
	private final String ownerID;
	
	private final AudioHandler audioHandler;
	private long disconnected;
	
	/**
	 * Creates a new InputListener instance.
	 * 
	 * @param jda     jda instance
	 * @param ownerID ID of bot owner, can be null
	 */
	public InputListener(JDA jda, String ownerID)
	{
		this.audioHandler = new AudioHandler();
		this.ownerID = ownerID;
	}
	
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
	{
		// There's a lot of difficulty trying to create a global exception handler
		// This is the best I can do right now
		try {
			handleSlashCommand(event);
		} catch(Throwable t) {
			Main.error(t);
			if(!event.isAcknowledged()) event.reply(Main.ERROR_MESSAGE).queue();
		}
	}
	
	private void handleSlashCommand(SlashCommandInteractionEvent event)
	{
		// Message content
		String content = event.getCommandString();
		if(content.contains(":"))
			content = content.substring(content.indexOf(":") + 2);
		
		User user = event.getUser();
		Guild guild = event.getGuild();
		
		Main.log.info("[SLASH COMMAND (" + (event.isFromGuild() ? (guild.getId() + ", channel #" + event.getChannel().getId()) : user.getId()) + ") - '" + user.getName() + "']: \"" + event.getCommandString() + "\"");
		
		switch(event.getName())
		{
			case "play":
			{
				audioHandler.handleSongRequest(event.getOption("source", (option) -> AudioSource.valueOf(option.getAsString())), event.getOption("next", false, option -> option.getAsBoolean()), user.getIdLong(), event);
				break;
			}
			case "skip":
			{
				int songs = event.getOption("amount", 1, OptionMapping::getAsInt);
				event.reply(audioHandler.getGuildMusicManager(guild).skipTracks(songs)).queue();
				break;
			}
			case "stop":
			{
				audioHandler.getGuildMusicManager(guild).reset();
				event.reply("Stopped playback").queue();
				break;
			}
			case "forward":
			{
				GuildMusicManager guildMusicManager = audioHandler.getGuildMusicManager(guild);
				if(!guildMusicManager.isPlaying())
				{
					event.reply("Nothing is playing").queue();
					break;
				}
				
				AtomicLong msSkip = new AtomicLong();
				
				event.getOptionsByType(OptionType.INTEGER).forEach(option ->
				{
					long value = option.getAsLong();
					
					switch(option.getName())
					{
						case "seconds":
							msSkip.set(msSkip.get() + value * 1000);
							break;
						case "minutes":
							msSkip.set(msSkip.get() + value * 60000);
							break;
						case "hours":
							msSkip.set(msSkip.get() + value * 3600000);
							break;
						default:
							Main.error("Unhandled integer OptionType for forward command");
							break;
					}
				});
				
				if(msSkip.get() == 0)
					event.reply("You must skip at least one second").queue();
				else
					event.reply(guildMusicManager.forward(msSkip.get())).queue();
				break;
			}
			case "loop":
			{
				event.reply(audioHandler.getGuildMusicManager(guild).loop(event.getOption("loop").getAsBoolean())).queue();
				break;
			}
			case "queue":
			{
				event.reply(audioHandler.getGuildMusicManager(guild).getQueueList()).queue();
				break;
			}
			case "leave":
			{
				AudioManager manager = guild.getAudioManager();
				
				if(manager.isConnected())
				{
					event.reply("Left **" + MarkdownSanitizer.sanitize(manager.getConnectedChannel().getName()) + "**").queue();
					audioHandler.leaveCall(guild);
				}
				else
				{
					event.reply(Main.getBotName() + " is not in a channel").queue();
				}
				
				break;
			}
			// Private
			case "logs":
			{
				// Restrict to bot owner and DMs only
				if(event.isFromGuild() || !user.getId().equals(ownerID))
				{
					event.reply(user.getId().equals(ownerID) ? "Use in DMs" : "Command only accessible to bot owner").setEphemeral(true).queue();
					return;
				}
				
				// Optional
				String path = event.getOption("path", option -> option.getAsString());
				File file = path == null ? BASE_PATH : new File(path);
				
				// Prevent directory traversal
				Path basePath = Paths.get(System.getProperty("user.dir"));
				Path resolvedPath = basePath.resolve(file.toPath()).normalize();
				
				if(!resolvedPath.startsWith(basePath))
				{
					event.reply("No directory traversal for you").queue();
					break;
				}
				
				// If it's a file return it
				if(!file.isDirectory())
				{
					if(file.exists()) event.replyFiles(FileUpload.fromData(file)).queue();
					else event.reply("`" + file + "` not found").queue();
				}
				else
				{
					File[] files = getDirectoryFiles(file);
					
					if(files.length == 0)
					{
						event.reply("No files found!").queue();
						break;
					}
					
					final int MAX_LOGS = 10;
					
					// Optional, but min value would be 1 if provided, then zero based
					final int page = event.getOption("page", 1, option -> option.getAsInt()) - 1;
					final int maxPages = (int) Math.ceil(1f * files.length / MAX_LOGS);
					
					// Check if out of range
					if(page * MAX_LOGS >= files.length)
					{
						event.reply("Offset exceeds number of files (**" + files.length + "**). Use pages 1 - " + maxPages).queue();
						break;
					}
					
					StringBuilder builder = new StringBuilder("**Select a file (" + files.length + " total" + (maxPages == 1 ? "" : (", page " + (page + 1) + "/" + maxPages)) + "):**\n");
					
					// Arbitrarily saying only put 20 files here
					for(int i = page * MAX_LOGS; i < Math.min(files.length, page * MAX_LOGS + MAX_LOGS); i++)
					{
						builder.append("- ```" + basePath.relativize(Paths.get(files[i].getAbsolutePath())) + "```\n");
					}
					
					// If there's more files to show
					if(files.length - page * MAX_LOGS > MAX_LOGS)
					{
						builder.append("... and **" + (files.length - MAX_LOGS) + "** more. ");
					}
					
					// Splice just in case
					String toSend = builder.toString();
					event.reply(toSend.substring(0, Math.min(toSend.length(), Message.MAX_CONTENT_LENGTH))).queue();
				}
				
				break;
			}
			// Both
			case "clean-up":
			{
				if(event.isFromGuild() && !guild.getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
				{
					event.reply(Main.getBotName() + " needs **Manage Messages** and **Read Message History** permissions to clear messages.").setEphemeral(true).queue();
					break;
				}
				
				// Private channel
				handleClearRequest(event.getMessageChannel());
				event.reply("Clearing " + MAX_CLEARS + "...").setEphemeral(true).queue();
				break;
			}
			default:
			{
				Main.log.error("Unhandled command " + event.getFullCommandName());
				event.reply(Main.ERROR_MESSAGE).setEphemeral(true).queue();
				break;
			}
		}
	}
	
	private void handleClearRequest(MessageChannel channel)
	{
		channel.getIterableHistory().takeWhileAsync(MAX_CLEARS, rule -> rule.getAuthor().getId().equals(Main.getBotID())).thenAccept(channel::purgeMessages);
	}
	
	/**
	 * @return a potentially empty list of all files in the running directory folder 
	 */
	private File[] getDirectoryFiles(File path)
	{
		File[] files = path.listFiles();
		if(files == null) return new File[0];
		
		// Sort them by last modified
		Arrays.sort(files, (one, two) -> Long.compare(one.lastModified(), two.lastModified()));
		return files;
	}
	
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event)
	{
		audioHandler.handleButtonPress(event);
	}
	
	/**
	 * We need this for detecting inactivity
	 */
	@Override
	public void onGatewayPing(GatewayPingEvent event)
	{
		// Make sure we're not in a call by ourselves
		audioHandler.tick(event.getJDA());
	}
	
	@Override
	public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event)
	{
		Guild guild = event.getGuild();
		
		// If someone left
		if(event.getChannelLeft() != null)
		{
			GuildMusicManager manager = audioHandler.getGuildMusicManager(guild);
			GuildVoiceState state = guild.getSelfMember().getVoiceState();
			
			if(state.inAudioChannel())
			{
				AudioChannel channel = state.getChannel();
				int nonBots = 0;
				
				// Count all bot users
				for(Member member : channel.getMembers())
				{
					if(!member.getUser().isBot())
					{
						nonBots++;
					}
				}
				
				// If the channel is now empty
				if(nonBots == 0)
				{
					// Leave the call
					manager.sendToOrigin("All users left **" + MarkdownSanitizer.sanitize(channel.getName()) + "**");
					audioHandler.leaveCall(guild);
				}
			}
		}
	}
	
	@Override
	public void onGuildLeave(GuildLeaveEvent event)
	{
		onUnavailableGuildLeave(new UnavailableGuildLeaveEvent(event.getJDA(), event.getResponseNumber(), event.getGuild().getIdLong()));
	}
	
	@Override
	public void onUnavailableGuildLeave(UnavailableGuildLeaveEvent event)
	{
		audioHandler.deleteMusicManager(event.getGuildIdLong());
	}
	
	@Override
	public void onSessionDisconnect(SessionDisconnectEvent event)
	{
		disconnected = System.currentTimeMillis();
		CloseCode code = event.getCloseCode();
		Main.log.info("DISCONNECTED! Close code: " + (code == null ? "null" : code.getCode() + ". Meaning: " + code.getMeaning()) + ". Closed by discord: " + event.isClosedByServer());
	}
	
	@Override
	public void onSessionResume(SessionResumeEvent event)
	{
		Main.log.info("Reconnected!");
		
		long notifyTime = 60000;
		long disconnectTime = System.currentTimeMillis() - disconnected;
		
		if(disconnectTime > notifyTime)
		{
			Main.log.info("Bot was disconnected for " + disconnectTime / 1000 + " seconds");
		}
	}
	
	@Override
	public void onException(ExceptionEvent event)
	{
		Main.error(event.getCause());
	}
}
