package com.supasulley.music;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParserException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.supasulley.main.Main;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.http.RefreshTokenQueryResponse;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

public class AudioHandler {
	
	// 1 hour before the bot leaves
	private static final long IDLE_TIME = 60 * 1000 * 60;
	
	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;
	
	private final YoutubeAudioSourceManager ytSourceManager;
	
	// OAuth information
	private long lastOauthCheck, interval;
	private String userCode, deviceCode;
	
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
		
		// YouTube plugin
		this.ytSourceManager = new YoutubeAudioSourceManager();
		playerManager.registerSourceManager(ytSourceManager);
		
		this.musicManagers = new HashMap<Long, GuildMusicManager>();
		
		// OLD
//		AudioSourceManagers.registerLocalSource(playerManager);
//		AudioSourceManagers.registerRemoteSources(playerManager);
	}
	
	public void handleSongRequest(ButtonCode code, long userID, SlashCommandInteractionEvent event)
	{
		// First check if we are authenticated with YouTube
		// If we don't have a refresh token yet
		if(ytSourceManager.getOauth2RefreshToken() == null)
		{
			// If we never logged in before
			if(this.userCode == null)
			{
				Main.log.info("Starting oauth");
				
				// Start OAuth
				JsonObject object = ytSourceManager.getOauth2Handler().fetchDeviceCode();
				this.userCode = object.getString("user_code");
				this.deviceCode = object.getString("device_code");
				this.interval = object.getLong("interval") * 1000; // to ms
			}
			else
			{
				// Check if it came in the mail today
				// If we are already in a state of checking and we can check again
				if(System.currentTimeMillis() - lastOauthCheck > (interval == 0 ? 5000 : interval))
				{
					this.lastOauthCheck = System.currentTimeMillis();
					
					// Check if authenticated
					try {
						Main.log.info("Checking if oauth is completed");
						RefreshTokenQueryResponse response = ytSourceManager.getOauth2Handler().getRefreshTokenByDeviceCode(deviceCode);
						String error = response.getError();
						
						// If we don't have the code yet
						if(error != null)
						{
							switch(error)
							{
								case "authorization_pending":
								case "slow_down":
									// Still waiting
									Main.log.info("Still waiting on response");
									break;
								default:
									// Error occurred. Restart login
									throw new IOException(error);
							}
						}
						else
						{
							// Success!
							Main.log.info("Linked with Google");
							JsonObject json = response.getJsonObject();
							ytSourceManager.getOauth2Handler().updateTokens(json);
							
							// Manually set the refresh token to toggle enable flag
							ytSourceManager.getOauth2Handler().setRefreshToken(json.getString("refresh_token"), true);
						}
					} catch(IOException | JsonParserException e) {
						Main.log.warn("Failed to link with Google");
						Main.log.warn(e.getMessage());
						this.userCode = null;
						this.deviceCode = null;
					}
				}
			}
			
			// Check again if we have code
			if(ytSourceManager.getOauth2RefreshToken() == null)
			{
				event.reply("Link " + Main.BOT_NAME + " with a burner Google account. Go to <https://www.google.com/device> and enter code **" + userCode + "**.").addActionRow(Button.link("https://www.google.com/device", "Link")).queue();
				return;
			}
			else
			{
				
			}
		}
		
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
				
				// Do I need to migrate this??
				YoutubeSearchProvider provider = new YoutubeSearchProvider();
				provider.loadSearchResult(query, new Function<AudioTrackInfo, AudioTrack>()
				{
					private int index = 0;
					
					@Override
					public AudioTrack apply(AudioTrackInfo t)
					{
						// Streams can't be played
						if(!t.isStream && index < 5)
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
			throw new Exception("Join a voice channel to play songs");
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
					result.complete(new RequestInfoBuilder().bold().showDuration().showLink().apply(track) + " " + (playNext ? "will play next" : "added to queue"));
				}
				else
				{
					result.complete("Now playing " + new RequestInfoBuilder().bold().showDuration().showLink().apply(track));
				}
				
				musicManager.queue(playNext, new AudioRequest(track, audioChannel), textChannel);
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
				String suffix = " (first song of playlist **" + playlist.getName() + "** with **" + playlist.getTracks().size() + "** tracks)";
				
				if(musicManager.isPlaying())
				{
					result.complete(new RequestInfoBuilder().bold().showDuration().showLink().apply(firstTrack) + " " + (playNext ? "will play next" : "added to queue") + suffix);
				}
				else
				{
					result.complete("Now playing " + new RequestInfoBuilder().bold().showDuration().showLink().apply(firstTrack) + suffix);
					// No need to flip the order if nothing is playing
					flipOrder = false;
				}
				
				// Queue all tracks in playlist
				for(int i = 0; i < playlist.getTracks().size(); i++)
				{
					int index = flipOrder ? playlist.getTracks().size() - i - 1 : i;
					musicManager.queue(playNext, new AudioRequest(playlist.getTracks().get(index), audioChannel), textChannel);
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
	 * Gets the GuildMusicManager for a particular guild
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
	
	/**
	 * Leaves the guild's audio channel and deletes it from the music managers. Used when the bot is done being used.
	 * @param guild the guild
	 */
	public void leaveCall(Guild guild)
	{
		guild.getAudioManager().closeAudioConnection();
		deleteMusicManager(guild.getIdLong());
	}
	
	/**
	 * Deletes the music manager from the list.
	 */
	public void deleteMusicManager(long guildID)
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
		Iterator<Entry<Long, GuildMusicManager>> iterator = musicManagers.entrySet().iterator();
		
		while(iterator.hasNext())
		{
			Entry<Long, GuildMusicManager> entry = iterator.next();
			GuildMusicManager manager = entry.getValue();
			
			// If this manager isn't playing anything AND it's been a fat sec since we've had to handle a request
			if(!manager.isPlaying() && manager.timeSinceLastRequest() > IDLE_TIME)
			{
				// Check if the channel is empty
				Guild guild = jda.getGuildById(entry.getKey());
				
				if(guild != null && guild.getAudioManager().isConnected())
				{
					// Check if its been a while since the last command
					manager.sendToOrigin("Left due to inactivity");
					guild.getAudioManager().closeAudioConnection();
				}
				
				iterator.remove();
			}
		}
	}
}
