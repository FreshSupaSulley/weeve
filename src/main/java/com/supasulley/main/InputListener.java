package com.supasulley.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.supasulley.music.AudioHandler;
import com.supasulley.music.GuildMusicManager;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
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
import net.dv8tion.jda.api.interactions.commands.ICommandReference;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public class InputListener extends ListenerAdapter {
	
	private static final int MAX_CLEARS = 50;
	
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
				audioHandler.handleSongRequest(AudioHandler.ButtonCode.PLAY_NOW, user.getIdLong(), event);
				return;
			}
			case "next":
			{
				audioHandler.handleSongRequest(AudioHandler.ButtonCode.PLAY_NEXT, user.getIdLong(), event);
				return;
			}
			case "skip":
			{
				int songs = event.getOption("amount", 1, OptionMapping::getAsInt);
				event.reply(audioHandler.getGuildAudioPlayer(guild).skipTracks(songs)).queue();
				return;
			}
			case "stop":
			{
				audioHandler.getGuildAudioPlayer(guild).reset();
				event.reply("Stopped playback").queue();
				return;
			}
			case "forward":
			{
				GuildMusicManager guildMusicManager = audioHandler.getGuildAudioPlayer(guild);
				if(!guildMusicManager.isPlaying())
				{
					event.reply("Nothing is playing").queue();
					return;
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
							System.err.println("Unhandled integer OptionType for forward command");
							break;
					}
				});
				
				if(msSkip.get() == 0)
					event.reply("You must skip at least one second").queue();
				else
					event.reply(guildMusicManager.forward(msSkip.get())).queue();
				return;
			}
			case "loop":
			{
				event.reply(audioHandler.getGuildAudioPlayer(guild).loop(event.getOption("loop").getAsBoolean())).queue();
				return;
			}
			case "queue":
			{
				event.reply(audioHandler.getGuildAudioPlayer(guild).getQueueList()).queue();
				return;
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
				
				return;
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
				
				switch(event.getSubcommandName())
				{
					case "list":
					{
						File[] files = getLogFiles();
						
						if(files.length == 0)
						{
							event.reply("No files found!").queue();
							return;
						}
						
						StringBuilder builder = new StringBuilder("**Select a file:**\n");
						
						// Arbitrarily saying only put 20 files here
						for(int i = 0; i < Math.min(files.length, 20); i++)
						{
							builder.append("- " + files[i].getName() + "\n");
						}
						
						// If there's more files than normal
						if(files.length > Main.MAX_LOGS)
						{
							builder.append("... and **" + (files.length - Main.MAX_LOGS) + "** more. ");
						}
						
						ICommandReference command = Main.getCommandByName("logs get");
						builder.append("Pass the filename (and extension) in </" + command.getFullCommandName() + ":" + command.getId() + ">");
						
						// Splice just in case
						String toSend = builder.toString();
						event.reply(toSend.substring(0, Math.min(toSend.length(), Message.MAX_CONTENT_LENGTH))).queue();
						break;
					}
					case "get":
					{
						// Required option
						String logFile = event.getOption("file", option -> option.getAsString());
						
						/* 
						 * We're doing this the secure way
						 * Instead of just grabbing new File("/logs" + logIndex), I'm
						 * iterating through each file in the logs directory and checking
						 * if the name matches up. That way you can't back traverse
						 * (even though that should be fine as it's just the operator)
						 */
						for(File file : getLogFiles())
						{
							if(file.getName().equals(logFile))
							{
								event.replyFiles(FileUpload.fromData(file)).queue();
								return;
							}
						}
						
						event.reply("`" + logFile + "` was not found in logs").queue();
						break;
					}
					case "clear":
					{
						// Don't delete the current log
						int count = 0;
						
						for(File file : getLogFiles())
						{
							// Do not remove active log file, it doesn't seem to get regenerated
							if(file.getName().equals(Main.LOG_CURRENT.getName()))
							{
								continue;
							}
							
							count++;
							file.delete();
						}
						
						event.reply("Deleted **" + count + "** logs").queue();
						break;
					}
				}
				
				return;
			}
			// Both
			case "clean-up":
			{
				if(event.isFromGuild() && !guild.getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
				{
					event.reply(Main.getBotName() + " needs **Manage Messages** and **Read Message History** permissions to clear messages.").setEphemeral(true).queue();
					return;
				}
				
				// Private channel
				handleClearRequest(event.getMessageChannel());
				event.reply("Clearing " + MAX_CLEARS + "...").setEphemeral(true).queue();
				return;
			}
			default:
			{
				Main.log.error("Unhandled command " + event.getFullCommandName());
				event.reply(Main.ERROR_MESSAGE).setEphemeral(true).queue();
				return;
			}
		}
	}
	
	/**
	 * @return a potentially empty list of all log files in the logs folder 
	 */
	private File[] getLogFiles()
	{
		File[] logs = new File("logs").listFiles();
		if(logs == null) return new File[0];
		
		// Sort them by last modified
		Arrays.sort(logs, (one, two) -> Long.compare(one.lastModified(), two.lastModified()));
		return logs;
	}
	
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event)
	{
		audioHandler.handleButtonPress(event);
	}
	
	private void handleClearRequest(MessageChannel channel)
	{
		MessageHistory history = new MessageHistory(channel);
		history.retrievePast(MAX_CLEARS).queue(messages ->
		{
			List<Message> toDelete = new ArrayList<Message>();
			
			for(int i = 0; i < messages.size(); i++)
			{
				Message sample = messages.get(i);
				if(sample.isPinned())
					continue;
				
				if(sample.getAuthor().getId().equals(Main.getBotName()))
				{
					toDelete.add(messages.get(i));
				}
			}
			
			if(toDelete.size() > 1)
			{
				channel.purgeMessages(toDelete).forEach(future ->
				{
					future.whenComplete((done, error) ->
					{
						if(error != null)
						{
							Main.log.error("Failed to delete message", error);
						}
					});
				});
			}
			else if(toDelete.size() != 0)
			{
				toDelete.get(0).delete().queue();
			}
		});
	}
	
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
		
		if(event.getChannelLeft() != null)
		{
			GuildMusicManager manager = audioHandler.getGuildAudioPlayer(guild);
			
			if(manager.isPlaying())
			{
				AudioChannel audioChannel = manager.getCurrentRequest().getAudioChannel();
				// If we are playing music in the same channel as the person who left
				// This is better than using the AudioManager because it's apparently unreliable for async reasons
				if(audioChannel.getIdLong() == event.getChannelLeft().getIdLong())
				{
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
		System.out.println("DISCONNECTED! Close code: " + (code == null ? "null" : code.getCode() + ". Meaning: " + code.getMeaning()) + ". Closed by discord: " + event.isClosedByServer());
	}
	
	@Override
	public void onSessionResume(SessionResumeEvent event)
	{
		System.out.println("Reconnected!");
		
		long notifyTime = 60000;
		long disconnectTime = System.currentTimeMillis() - disconnected;
		
		if(disconnectTime > notifyTime)
		{
			System.out.println("Bot was disconnected for " + disconnectTime / 1000 + " seconds");
		}
	}
	
	@Override
	public void onException(ExceptionEvent event)
	{
		System.err.println("JDA Exception! Code: " + event.getResponseNumber() + ". Logged: " + event.isLogged() + ". Cause: " + event.getCause());
	}
}
