package com.supasulley.music;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.supasulley.main.AudioSource;
import com.supasulley.main.Main;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

public class AudioHandler {
	
	// 1 hour before the bot leaves
	public static final long IDLE_TIME = 60 * 1000 * 60;
	
	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;
	
	/** The default AudioSource. Rotates when one fails */
	private static AudioSource DEFAULT = AudioSource.SOUNDCLOUD;
	
	public AudioHandler()
	{
		this.playerManager = new DefaultAudioPlayerManager();
		
		// Add each audio source manager
		for(AudioSource source : AudioSource.values())
		{
			playerManager.registerSourceManager(source.getManager(null, playerManager));
		}
		
		this.musicManagers = new HashMap<Long, GuildMusicManager>();
		
		// Not needed
		// Apparently you need to call this after you add sources?
		AudioSourceManagers.registerRemoteSources(playerManager);
//		AudioSourceManagers.registerLocalSource(playerManager);
	}
	
	@Deprecated
	public void supplyYTCookies(String cookies, SlashCommandInteractionEvent event)
	{
		BasicCookieStore store = new BasicCookieStore();
		
		// Break it down
		try {
			JsonParser.parseString(cookies).getAsJsonArray().forEach(element ->
			{
				JsonObject rawCookie = element.getAsJsonObject();
				BasicClientCookie cookie = new BasicClientCookie(rawCookie.get("name").getAsString(), rawCookie.get("value").getAsString());
				
				rawCookie.asMap().forEach((key, value) ->
				{
					switch(key)
					{
						case "expirationDate":
						{
							cookie.setExpiryDate(new Date((long) (value.getAsDouble() * 1000)));
							break;
						}
						case "domain":
						{
							cookie.setDomain(value.getAsString());
							break;
						}
						case "path":
						{
							cookie.setPath(value.getAsString());
							break;
						}
						case "secure":
						{
							cookie.setSecure(value.getAsBoolean());
							break;
						}
						default:
						{
							Main.log.warn("Unknown cookie property {}", key);
							break;
						}
					}
				});
				
//				cookie.setAttribute(cookies, cookies);
//				rawCookie.get("httpOnly").getAsBoolean(), rawCookie.get("persistent??????").getAsBoolean(), rawCookie.get("hostOnly").getAsBoolean(), null
				store.addCookie(cookie);
			});
		} catch(Throwable t) {
			event.reply("Cookies need to be in JSON format. Use [this extension](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc) to harvest cookies.").setEphemeral(true).queue();
			Main.error("Failed to parse cookie as JSON", t);
			return;
		}
		
//		(((Web) ytManager.getClients()[0]).
//		ytWebClient.BASE_CONFIG.put
//		ytManager.getHttpInterfaceManager().configureBuilder(builder -> builder.setDefaultCookieStore(store));
		event.reply("Cookies added. If YouTube still isn't supported, make sure you are exporting them correctly.").setEphemeral(true).queue();
	}
	
	public void handleSongRequest(@Nullable AudioSource desiredSource, boolean playNext, long userID, SlashCommandInteractionEvent event)
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
		
		// Search for the video
		event.deferReply(true).queue(hook ->
		{
			final String sourceWarning;
			AudioSource source = desiredSource == null ? DEFAULT : desiredSource;
			
			
			// If we can't process YouTube
//			if(source == AudioSource.YOUTUBE)
//			{
//				// Switch to alternative source
//				source = AudioSource.SOUNDCLOUD;
//				// Warn user they need to provide poToken
//				sourceWarning = "You're using **" + source.getFancyName() + "**. To use YouTube, supply tokens with </yttoken:" + Main.getCommandByName("yttoken").getId() + ">.\n";
//			}
//			else
			{
				sourceWarning = "";
			}
			
			playerManager.loadItem(source.getSearchPrefix() + query, new AudioLoadResultHandler()
			{
				@Override
				public void trackLoaded(AudioTrack track)
				{
					// It's a link, immediately play it
					loadURL(playNext, query, audioChannel, event.getChannel());
				}
				
				@Override
				public void playlistLoaded(AudioPlaylist playlist)
				{
					// Playlist tracks can apparently be empty on some providers (SoundCloud)
					if(playlist.getTracks().isEmpty())
					{
						noMatches();
						return;
					}
					
					StringBuilder builder = new StringBuilder("**Select a track:**");
					ArrayList<ButtonImpl> buttons = new ArrayList<ButtonImpl>();
					
					for(int i = 0; i < Math.min(playlist.getTracks().size(), 5); i++)
					{
						AudioTrackInfo info = playlist.getTracks().get(i).getInfo();
						buttons.add(new ButtonImpl("" + i, "" + (i + 1), playNext ? ButtonStyle.SECONDARY : ButtonStyle.PRIMARY, false, null));
						builder.append("\n" + "**" + (i + 1) + ":** " + info.title + (info.length != Units.DURATION_MS_UNKNOWN ? " (**" + parseDuration(info.length) + "**)" : ""));
					}
					
					hook.sendMessage(sourceWarning + builder.toString()).addActionRow(buttons).setEphemeral(true).onSuccess(message -> {
						// This only grabs the first 5 from the tracks
						getGuildMusicManager(event.getGuild()).addRequest(message.getId(), playlist.getTracks().stream().limit(buttons.size()).collect(Collectors.toList()));
					}).queue();
				}
				
				@Override
				public void noMatches()
				{
					hook.sendMessage(sourceWarning + "Nothing found by `" + MarkdownSanitizer.sanitize(query) + "`").setEphemeral(true).queue();
				}
				
				@Override
				public void loadFailed(FriendlyException exception)
				{
					// Rotate default if this was what was loaded
					if(source == DEFAULT)
					{
						DEFAULT = AudioSource.values()[(DEFAULT.ordinal() + 1) % AudioSource.values().length];
						Main.log.warn("Rotated default audio source to {}", DEFAULT);
					}
					
					hook.sendMessage(sourceWarning + "No audio could be found for `" + MarkdownSanitizer.sanitize(query) + "`").setEphemeral(true).queue();
					Main.error("Failed to load audio for " + query, exception);
				}
			});
		});
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
		
		GuildMusicManager musicManager = getGuildMusicManager(audioChannel.getGuild());
		
		// Check if the options are still cached
		List<AudioTrack> selections = musicManager.getAudioTrack(event.getMessageId());
		
		if(selections == null)
		{
			event.deferEdit().setComponents().setContent("This event has expired. Try " + Main.getCommandReference("play") + " again.").queue();
			return;
		}
		
		// Delete the select track message
		event.deferEdit().queue(success -> success.deleteOriginal().queue());
		event.getChannel().sendMessage(loadURL(event.getButton().getStyle() == ButtonStyle.SECONDARY, selections.get(Integer.parseInt(event.getButton().getId())).getInfo().uri, audioChannel, event.getChannel())).queue();
//		musicManager.queue(event.getButton().getStyle() == ButtonStyle.SECONDARY, track, event.getChannel());
	}
	
	private AudioChannel getMemberAudioChannel(Interaction event) throws Exception
	{
		Guild guild = event.getGuild();
		AudioChannel audioChannel = event.getMember().getVoiceState().getChannel();
		
		// If the user is not in a voice channel
		if(audioChannel == null)
		{
			throw new Exception("Join a voice channel first");
		}
		
		if(!guild.getSelfMember().hasPermission(event.getMember().getVoiceState().getChannel(), Permission.VOICE_CONNECT))
		{
			throw new Exception(Main.getBotName() + " needs permission to join the call");
		}
		
		return audioChannel;
	}
	
	private String loadURL(boolean playNext, String url, AudioChannel audioChannel, MessageChannel textChannel)
	{
		GuildMusicManager musicManager = getGuildMusicManager(audioChannel.getGuild());
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
	public GuildMusicManager getGuildMusicManager(Guild guild)
	{
		GuildMusicManager musicManager = musicManagers.get(guild.getIdLong());
		
		if(musicManager == null)
		{
			musicManager = new GuildMusicManager(playerManager);
			musicManagers.put(guild.getIdLong(), musicManager);
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
		if(duration == Units.DURATION_MS_UNKNOWN) return "?";
		
		int seconds = (int) ((duration % 60000) / 1000);
		int minutes = (int) ((duration % 3600000) / 60000);
		int hours = (int) (duration / 3600000);
		
		return (hours != 0 ? String.format("%01d", hours) + ":" : "") + String.format("%0" + (hours == 0 ? "1" : "2") + "d", minutes) + ":" + String.format("%02d", seconds);
	}
	
	public void tick(JDA jda)
	{
		Iterator<Entry<Long, GuildMusicManager>> iterator = musicManagers.entrySet().iterator();
		
		while(iterator.hasNext())
		{
			Entry<Long, GuildMusicManager> entry = iterator.next();
			GuildMusicManager manager = entry.getValue();
			
			// If this manager isn't playing anything AND it's been a fat sec since we've had to handle a request
			// manager.getLeaveTime() != 0 is likely redundant
			if(!manager.isPlaying() && manager.getLeaveTime() != 0 && System.currentTimeMillis() > manager.getLeaveTime())
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
