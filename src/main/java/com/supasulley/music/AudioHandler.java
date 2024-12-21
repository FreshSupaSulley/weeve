package com.supasulley.music;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
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
import net.dv8tion.jda.api.interactions.InteractionHook;
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
			playerManager.registerSourceManager(source.getManager());
		}
		
		this.musicManagers = new HashMap<Long, GuildMusicManager>();
		
		// Not needed
		// Apparently you need to call this after you add sources?
//		AudioSourceManagers.registerRemoteSources(playerManager);
//		AudioSourceManagers.registerLocalSource(playerManager);
	}
	
	public void handleSongRequest(@Nullable AudioSource desiredSource, boolean playNext, long userID, SlashCommandInteractionEvent event)
	{
		AudioSource source = desiredSource == null ? DEFAULT : desiredSource;
		
		AtomicBoolean isSearch = new AtomicBoolean(true);
		String query = event.getOption("query").getAsString();
		String modifiedQuery = new Function<String, String>()
		{
			@Override
			public String apply(String query)
			{
				try {
					new URI(query).toURL();
					isSearch.set(false);
				} catch(Throwable t) {
					Main.log.info("Processing {} as search query (error was {})", query, t.getMessage());
					query = source.getSearchPrefix() + query;
				}
				
				return query;
			}
		}.apply(query);
		
		// Get member audio channel
		AudioChannel audioChannel;
		
		try {
			audioChannel = getMemberAudioChannel(event);
		} catch(Exception e) {
			event.reply(e.getMessage()).setEphemeral(true).queue();
			return;
		}
		
		// Search for the video
		event.deferReply(true).queue(hook ->
		{
			playerManager.loadItem(modifiedQuery, new AudioLoadResultHandler()
			{
				@Override
				public void trackLoaded(AudioTrack track)
				{
					Main.log.info("Loaded {}", track.getInfo().uri);
					// It's a link, immediately play it
					load(playNext, track.getInfo().uri, audioChannel, hook);
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
					
					StringBuilder builder = new StringBuilder("**Select a track from " + source.getFancyName() + ":**");
					ArrayList<ButtonImpl> buttons = new ArrayList<ButtonImpl>();
					
					for(int i = 0; i < Math.min(playlist.getTracks().size(), 5); i++)
					{
						AudioTrackInfo info = playlist.getTracks().get(i).getInfo();
						buttons.add(new ButtonImpl("" + i, "" + (i + 1), playNext ? ButtonStyle.SECONDARY : ButtonStyle.PRIMARY, false, null));
						builder.append("\n" + "**" + (i + 1) + ":** " + MarkdownSanitizer.escape(info.title).replaceAll("http[s]?://[^\\s]+", "<$0>") + (info.length != Units.DURATION_MS_UNKNOWN ? " (**" + parseDuration(info.length) + "**)" : ""));
					}
					
					hook.sendMessage(builder.toString()).addActionRow(buttons).setEphemeral(true).onSuccess(message -> {
						// This only grabs the first 5 from the tracks
						getGuildMusicManager(event.getGuild()).addRequest(message.getId(), playlist.getTracks().stream().limit(buttons.size()).collect(Collectors.toList()));
					}).queue();
				}
				
				@Override
				public void noMatches()
				{
					hook.sendMessage("Nothing found by `" + MarkdownSanitizer.sanitize(query) + "`").setEphemeral(true).queue();
				}
				
				@Override
				public void loadFailed(FriendlyException exception)
				{
					if(exception.getCause() instanceof NoCredentialsException)
					{
						((NoCredentialsException) exception.getCause()).fire(hook);
						return;
					}
					
					// Rotate default if this was what was loaded
					boolean rotated = false;
					
					// Only rotate if this wasn't a URL because they might've fucked up copying it
					if(isSearch.get() && source == DEFAULT)
					{
						rotated = true;
						DEFAULT = AudioSource.values()[(DEFAULT.ordinal() + 1) % AudioSource.values().length];
						Main.log.warn("Rotated default audio source to {}", DEFAULT);
					}
					
					hook.sendMessage("No audio could be found for `" + MarkdownSanitizer.sanitize(query) + "`" + (rotated ? " (switched default source to **" + DEFAULT.getFancyName() + "**)" : "")).setEphemeral(true).queue();
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
		load(event.getButton().getStyle() == ButtonStyle.SECONDARY, selections.get(Integer.parseInt(event.getButton().getId())).getInfo().uri, audioChannel, event.getHook());
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
	
	/**
	 * Queues whatever comes back from the query.
	 * 
	 * @param playNext     bump this track to the top of the queue
	 * @param query        search query or URL
	 * @param audioChannel {@linkplain AudioChannel} of member who made the request
	 * @param hook         {@linkplain InteractionHook} for responding
	 */
	private void load(boolean playNext, String query, AudioChannel audioChannel, InteractionHook hook)
	{
		GuildMusicManager musicManager = getGuildMusicManager(audioChannel.getGuild());
		MessageChannel textChannel = hook.getInteraction().getMessageChannel();
		
		playerManager.loadItem(query, new AudioLoadResultHandler()
		{
			@Override
			public void trackLoaded(AudioTrack track)
			{
				if(musicManager.isPlaying())
				{
					hook.sendMessage(new RequestInfoBuilder().bold().showDuration().showLink().apply(track) + " " + (playNext ? "will play next" : "added to queue")).queue();
				}
				else
				{
					hook.sendMessage("Now playing " + new RequestInfoBuilder().bold().showDuration().showLink().apply(track)).queue();
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
					hook.sendMessage(new RequestInfoBuilder().bold().showDuration().showLink().apply(firstTrack) + " " + (playNext ? "will play next" : "added to queue") + suffix).queue();
				}
				else
				{
					hook.sendMessage("Now playing " + new RequestInfoBuilder().bold().showDuration().showLink().apply(firstTrack) + suffix).queue();
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
				hook.sendMessage("Nothing found by `" + query + "`").queue();
			}
			
			@Override
			public void loadFailed(FriendlyException exception)
			{
				hook.sendMessage("No audio could be found for `" + query + "`").queue();
			}
		});
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
