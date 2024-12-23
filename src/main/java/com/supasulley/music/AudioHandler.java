package com.supasulley.music;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

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
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

public class AudioHandler {
	
	// 1 hour before the bot leaves
	public static final long IDLE_TIME = 60 * 1000 * 60;
	
	final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;
	
	/** The default AudioSource. Rotates when one fails */
	public static AudioSource DEFAULT = AudioSource.SOUNDCLOUD;
	
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
			// Test if query is a search term
			boolean isSearch = true;
			String query = event.getOption("query").getAsString();
			
			try {
				new URI(query).toURL();
				isSearch = false;
			} catch(Throwable t) {
				Main.log.info("Processing {} as search query (error: {})", query, t.getMessage());
			}
			
			searchQuery(hook, getGuildMusicManager(event.getGuild()), isSearch, playNext, query, source, audioChannel);
		});
	}
	
	/**
	 * Attempts to find a working alternative source to the provided one. If nothing was found, null is returned.
	 * 
	 * @param original source to find an alternative for
	 * @return an alternative source, or null if none was found
	 */
	private AudioSource getAlternativeSource(AudioSource original)
	{
		for(AudioSource newSource = original; (newSource = AudioSource.values()[(newSource.ordinal() + 1) % AudioSource.values().length]) != original && newSource.canHandle();)
		{
			return newSource;
		}
		
		return null;
	}
	
	public void searchQuery(InteractionHook hook, GuildMusicManager manager, boolean isSearch, boolean playNext, String query, AudioSource source, AudioChannel audioChannel)
	{
		AudioHandler handler = this;
		
		// Apply source search prefix if query a search
		playerManager.loadItem((isSearch ? source.getSearchPrefix() : "") + query, new AudioLoadResultHandler()
		{
			@Override
			public void trackLoaded(AudioTrack track)
			{
				// It's a link, immediately play it
				Main.log.info("Loaded {}", track.getInfo().uri);
				// We need to manually delete the hook because we can't spoof a ButtonInteractionEvent
				hook.deleteOriginal().queue();
				new TrackSelected(playNext, track, audioChannel).fire(hook, null, handler);
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
				
				// Creates button selections
				StringBuilder builder = new StringBuilder("**Select a track from " + source.getFancyName() + ":**");
				ArrayList<Button> buttons = new ArrayList<Button>();
				
				for(int i = 0; i < Math.min(playlist.getTracks().size(), 5); i++)
				{
					AudioTrackInfo info = playlist.getTracks().get(i).getInfo();
					buttons.add(new ButtonImpl("" + i, "" + (i + 1), playNext ? ButtonStyle.SECONDARY : ButtonStyle.PRIMARY, false, null));
					builder.append("\n" + "**" + (i + 1) + ":** " + MarkdownSanitizer.escape(info.title).replaceAll("http[s]?://[^\\s]+", "<$0>") + (info.length != Units.DURATION_MS_UNKNOWN ? " (**" + AudioHandler.parseDuration(info.length) + "**)" : ""));
				}
				
				attachAlternativeButton(hook.sendMessage(builder.toString()).addActionRow(buttons), message ->
				{
					// This only grabs the first 5 from the tracks
					for(int i = 0; i < buttons.size(); i++)
					{
						manager.addButtonAction(message, buttons.get(i), new TrackSelected(playNext, playlist.getTracks().get(i), audioChannel));
					}
				});
			}
			
			@Override
			public void noMatches()
			{
				attachAlternativeButton(hook.sendMessage("Nothing found by `" + MarkdownSanitizer.sanitize(query) + "`"), (message) -> {});
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
				if(isSearch && source == AudioHandler.DEFAULT)
				{
					rotated = true;
					AudioHandler.DEFAULT = getAlternativeSource(source);
					Main.log.warn("Rotated default audio source to {}", AudioHandler.DEFAULT);
				}
				
				hook.sendMessage("No audio could be found for `" + MarkdownSanitizer.sanitize(query) + "`" + (rotated ? " (switched default source to **" + AudioHandler.DEFAULT.getFancyName() + "**)" : "")).setEphemeral(true).queue();
				Main.error("Failed to load audio for " + query, exception);
			}
			
			private void attachAlternativeButton(WebhookMessageCreateAction<Message> preMessage, Consumer<? super Message> onSuccess)
			{
				// New search request button option
				AudioSource alternativeSource = getAlternativeSource(source);
				
				if(alternativeSource == null)
				{
					preMessage.setEphemeral(true).queue(onSuccess);
					return;
				}
				
				// Otherwise, attach and queue
				Button switchButton = Button.success(alternativeSource.toString(), "Try " + alternativeSource.getFancyName());
				preMessage.addActionRow(switchButton).setEphemeral(true).onSuccess(message ->
				{
					onSuccess.accept(message);
					// Now add listener for switch button
					manager.addButtonAction(message, switchButton, new SearchRequest(isSearch, playNext, query, alternativeSource, audioChannel, manager));
				}).queue();
			}
		});
	}
	
	public void handleButtonPress(ButtonInteractionEvent event)
	{
		// Currently, buttons require the user to be in an audio channel
		AudioChannel audioChannel;
		
		try {
			audioChannel = getMemberAudioChannel(event);
		} catch(Exception e) {
			event.reply(e.getMessage()).setEphemeral(true).queue();
			return;
		}
		
		getGuildMusicManager(audioChannel.getGuild()).runButtonAction(event.getMessageId(), event, this);
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
	 * @param textChannel  {@linkplain MessageChannel} for responding
	 */
	public void loadAndPlay(boolean playNext, String query, AudioChannel audioChannel, MessageChannel textChannel)
	{
		GuildMusicManager musicManager = getGuildMusicManager(audioChannel.getGuild());
		
		playerManager.loadItem(query, new AudioLoadResultHandler()
		{
			@Override
			public void trackLoaded(AudioTrack track)
			{
				if(musicManager.isPlaying())
				{
					textChannel.sendMessage(new RequestInfoBuilder().bold().showDuration().showLink().apply(track) + " " + (playNext ? "will play next" : "added to queue")).queue();
				}
				else
				{
					textChannel.sendMessage("Now playing " + new RequestInfoBuilder().bold().showDuration().showLink().apply(track)).queue();
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
					textChannel.sendMessage(new RequestInfoBuilder().bold().showDuration().showLink().apply(firstTrack) + " " + (playNext ? "will play next" : "added to queue") + suffix).queue();
				}
				else
				{
					textChannel.sendMessage("Now playing " + new RequestInfoBuilder().bold().showDuration().showLink().apply(firstTrack) + suffix).queue();
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
				textChannel.sendMessage("Nothing found by `" + query + "`").queue();
			}
			
			@Override
			public void loadFailed(FriendlyException exception)
			{
				textChannel.sendMessage("No audio could be found for `" + query + "`").queue();
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
