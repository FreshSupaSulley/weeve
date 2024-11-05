package com.supasulley.main;

import java.util.ArrayList;
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
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public class InputListener extends ListenerAdapter {
	
	private static final int MAX_CLEARS = 50;
	
	private final AudioHandler audioHandler;
	private long disconnected;
	
	public InputListener(JDA jda)
	{
		this.audioHandler = new AudioHandler();
	}
	
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
	{
		if(!event.isFromGuild())
		{
			if(event.getName().equals("clean-up"))
			{
				handleClearRequest(event.getMessageChannel());
				event.reply("Clearing " + MAX_CLEARS + "...").setEphemeral(true).queue();
				return;
			}
			else
			{
				event.reply("DM commands are not supported. Please use " + Main.BOT_NAME + " in your server.").setEphemeral(true).queue();
			}
			
			return;
		}
		
		// Message content
		String content = event.getCommandString();
		if(content.contains(":")) content = content.substring(content.indexOf(":") + 2);
		
		User user = event.getUser();
		Guild guild = event.getGuild();
		
		System.out.println("[SLASH COMMAND (" + guild.getId() + ", channel #" + event.getChannel().getId() + ") - '" + user.getName() + "']: \"" + event.getCommandString() + "\"");
		
		GuildMusicManager guildMusicManager = audioHandler.getGuildAudioPlayer(guild);
		
		switch(event.getFullCommandName())
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
				event.reply(guildMusicManager.skipTracks(songs)).queue();
				return;
			}
			case "stop":
			{
				guildMusicManager.reset();
				event.reply("Stopped playback").queue();
				return;
			}
			case "forward":
			{
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
				
				if(msSkip.get() == 0) event.reply("You must skip at least one second").queue();
				else event.reply(guildMusicManager.forward(msSkip.get())).queue();
				return;
			}
			case "loop":
			{
				event.reply(guildMusicManager.loop(event.getOption("loop").getAsBoolean())).queue();
				return;
			}
			case "queue":
			{
				event.reply(guildMusicManager.getQueueList()).queue();
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
					event.reply(Main.BOT_NAME + " is not in a channel").queue();
				}
				
				return;
			}
			case "clean-up":
			{
				if(!guild.getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
				{
					event.reply(Main.BOT_NAME + " needs **Manage Messages** and **Read Message History** permissions to clear messages.").setEphemeral(true).queue();
					return;
				}
				
				handleClearRequest(event.getMessageChannel());
				event.reply("Clearing " + MAX_CLEARS + "...").setEphemeral(true).queue();
				return;
			}
			default:
			{
				System.err.println("Unhandled command " + event.getFullCommandName());
				event.reply(Main.ERROR_MESSAGE).setEphemeral(true).queue();
				return;
			}
		}
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
				if(sample.isPinned()) continue;
				
				if(sample.getAuthor().getId().equals(Main.BOT_ID))
				{
					toDelete.add(messages.get(i));
				}
			}
			
			if(toDelete.size() > 1)
			{
				channel.purgeMessages(toDelete).forEach(future -> {
					future.whenComplete((done, error) -> {
						if(error != null) {
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
