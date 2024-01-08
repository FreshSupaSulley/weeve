package com.supasulley.music;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.supasulley.main.Main;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

public class AudioHandler {
	
	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;
	
	public enum ButtonCode {
		
		PLAY_NOW, PLAY_NEXT;
		
		public int getCode()
		{
			return ordinal();
		}
		
		public static ButtonCode getPlayCode(int code)
		{
			for(ButtonCode sample : values())
			{
				if(sample.getCode() == code) return sample;
			}
			
			System.err.println("Could not find PlayCode with ID " + code);
			return null;
		}
	}
	
	public AudioHandler()
	{
		this.playerManager = new DefaultAudioPlayerManager();
		this.musicManagers = new HashMap<Long, GuildMusicManager>();
		
		AudioSourceManagers.registerLocalSource(playerManager);
		AudioSourceManagers.registerRemoteSources(playerManager);
	}
	
	public void handleSongRequest(ButtonCode code, long userID, SlashCommandInteractionEvent event)
	{
		// Get member audio channel
		AudioChannel audioChannel;
		
		try {
			audioChannel = getMemberAudioChannel(event);
		} catch(Exception e) {
			event.reply(e.getMessage()).setEphemeral(true).queue();
			return;
		}
		
		// Message content
		String query = event.getOption("query").getAsString();
		
		// It's a search term
		if(!query.startsWith("https://"))
		{
			event.deferReply(true).queue(hook ->
			{
				AtomicReference<String> reference = new AtomicReference<String>("**Select a track:**");
				ArrayList<ButtonImpl> buttons = new ArrayList<ButtonImpl>();
				
				YoutubeSearchProvider provider = new YoutubeSearchProvider();
				provider.loadSearchResult(query, new Function<AudioTrackInfo, AudioTrack>()
				{
					private int index = 0;
					
					@Override
					public AudioTrack apply(AudioTrackInfo t)
					{
						if(index < 5)
						{
							buttons.add(new ButtonImpl(code.getCode() + "" + t.uri, "" + ++index, ButtonStyle.PRIMARY, false, null));
							reference.set(reference.get() + "\n" + "**" + index + ":** " + t.title + " (**" + parseDuration(t.length) + "**)");
						}
						
						return null;
					}
				});
				
				if(buttons.size() == 0) event.getHook().sendMessage("No results for `" + query + "`").setEphemeral(true).queue();
				else event.getHook().sendMessage(reference.get()).addActionRow(buttons).setEphemeral(true).queue();
			});
		}
		// It's a link
		else
		{
			event.reply(loadURL(code == ButtonCode.PLAY_NEXT, query, audioChannel, event.getChannel())).queue();
		}
	}
	
	public void handleButtonPress(ButtonInteractionEvent event)
	{
		AudioChannel audioChannel;
		
		try {
			audioChannel = getMemberAudioChannel(event);
		} catch(Exception e) {
			event.reply(e.getMessage()).setEphemeral(true).queue();
			return;
		}
		
		int code = Integer.parseInt(event.getButton().getId().substring(0, 1));
		String url = event.getButton().getId().substring(1);
		
		event.deferEdit().queue(success -> success.deleteOriginal().queue());
		event.getChannel().sendMessage(loadURL(ButtonCode.getPlayCode(code) == ButtonCode.PLAY_NEXT, url, audioChannel, event.getChannel())).queue();
	}
	
	private AudioChannel getMemberAudioChannel(Interaction event) throws Exception
	{
		Guild guild = event.getGuild();
		AudioChannel audioChannel = event.getMember().getVoiceState().getChannel();
		
		// If the user is not in a voice channel
		if(audioChannel == null)
		{
			throw new Exception("Hop in a call to play songs");
		}
		
		if(!guild.getSelfMember().hasPermission(event.getMember().getVoiceState().getChannel(), Permission.VOICE_CONNECT))
		{
			throw new Exception(Main.BOT_NAME + " needs permission to join the call");
		}
		
		return audioChannel;
	}
	
	private String loadURL(boolean playNext, String url, AudioChannel audioChannel, MessageChannel textChannel)
	{
		GuildMusicManager musicManager = getGuildAudioPlayer(audioChannel.getGuild());
		CompletableFuture<String> result = new CompletableFuture<String>();
		
		playerManager.loadItemOrdered(musicManager, url, new AudioLoadResultHandler()
		{
			@Override
			public void trackLoaded(AudioTrack track)
			{
				if(musicManager.isPlaying())
				{
					result.complete("**" + track.getInfo().title + " (" + parseDuration(track.getDuration()) + ")" + "** " + (playNext ? "will play next" : "added to queue"));
				}
				else
				{
					result.complete("Now playing **" + track.getInfo().title + " (" + parseDuration(track.getDuration()) + ")**");
				}
				
				musicManager.queue(playNext, new AudioRequest(track, audioChannel, textChannel));
			}
			
			@Override
			public void playlistLoaded(AudioPlaylist playlist)
			{
				AudioTrack firstTrack = playlist.getSelectedTrack();
				
				if(firstTrack == null)
				{
					firstTrack = playlist.getTracks().get(0);
				}
				
				boolean flipOrder = playNext;
				String suffix = " (first song of playlist **" + playlist.getName() + "** with **" + playlist.getTracks().size() + "** songs)";
				
				if(musicManager.isPlaying())
				{
					result.complete("**" + firstTrack.getInfo().title + "** " + (playNext ? "will play next" : "added to queue") + suffix);
				}
				else
				{
					result.complete("Now playing **" + firstTrack.getInfo().title + "** " + suffix);
					// No need to flip the order if nothing is playing
					flipOrder = false;
				}
				
				// Queue all tracks in playlist
				for(int i = 0; i < playlist.getTracks().size(); i++)
				{
					int index = flipOrder ? playlist.getTracks().size() - i - 1 : i;
					musicManager.queue(playNext, new AudioRequest(playlist.getTracks().get(index), audioChannel, textChannel));
				}
			}
			
			@Override
			public void noMatches()
			{
				result.complete("Nothing found by `" + url + "`");
			}
			
			@Override
			public void loadFailed(FriendlyException exception)
			{
				result.complete("No audio could be found for `" + url + "`");
			}
		});
		
		try {
			return result.get();
		} catch(InterruptedException | ExecutionException e) {
			e.printStackTrace();
			return Main.ERROR_MESSAGE;
		}
	}
	
	/**
	 * Gets the GuildMusicManager for a particular guild using the guild's ID
	 */
	public GuildMusicManager getGuildAudioPlayer(Guild guild)
	{
		long guildId = Long.parseLong(guild.getId());
		GuildMusicManager musicManager = musicManagers.get(guildId);
		
		if(musicManager == null)
		{
			musicManager = new GuildMusicManager(playerManager);
			musicManagers.put(guildId, musicManager);
			guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
		}
		
		return musicManager;
	}
	
	public void deleteGuild(long guildID)
	{
		GuildMusicManager musicManager = musicManagers.get(guildID);
		
		if(musicManager != null)
		{
			musicManager.reset();
			musicManagers.remove(guildID);
		}
	}
	
	public static String parseDuration(long duration)
	{
		int seconds = (int) ((duration % 60000) / 1000);
		int minutes = (int) ((duration % 3600000) / 60000);
		int hours = (int) (duration / 3600000);
		
		return (hours != 0 ? String.format("%02d", hours) + ":" : "") + String.format("%02d", minutes) + ":" + String.format("%02d", seconds);
	}
	
	public void tick(JDA jda)
	{
		musicManagers.forEach((key, value) ->
		{
			// If this manager isn't playing anything
			if(!value.isPlaying())
			{
				// Check if the channel is empty
				Guild guild = jda.getGuildById(key);
				
				if(guild != null)
				{
					AudioChannel channel = guild.getAudioManager().getConnectedChannel();
					
					if(channel != null)
					{
						// Count all non-bot users
						int nonBots = 0;
						
						for(Member member : channel.getMembers())
						{
							if(!member.getUser().isBot())
							{
								nonBots++;
							}
						}
						
						if(nonBots == 0)
						{
							guild.getAudioManager().closeAudioConnection();
						}
					}
				}
			}
		});
	}
}
