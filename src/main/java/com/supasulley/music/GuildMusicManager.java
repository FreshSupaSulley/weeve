package com.supasulley.music;

import java.util.concurrent.LinkedBlockingDeque;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Handles all music management for a guild.
 */
public class GuildMusicManager extends AudioEventAdapter {
	
	private final AudioPlayer player;
	private final AudioPlayerSendHandler sendHandler;
	
	// Scheduler
	private final LinkedBlockingDeque<AudioRequest> queue;
	private AudioRequest currentRequest;
	private boolean loop;
	private int loops;
	
	private long lastPlay;
	private MessageChannel messageChannel;
	
	public GuildMusicManager(AudioPlayerManager manager)
	{
		this.player = manager.createPlayer();
		this.player.addListener(this);
		this.sendHandler = new AudioPlayerSendHandler(player);
		
		this.queue = new LinkedBlockingDeque<AudioRequest>();
	}
	
	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason)
	{
		lastPlay = System.currentTimeMillis();
		
		// Only start the next track if the end reason is suitable for it (FINISHED or LOAD_FAILED)
		if(endReason.mayStartNext)
		{
			if(loop)
			{
				player.startTrack(track.makeClone(), false);
				sendToOrigin("Loop **" + ++loops + "** of **" + track.getInfo().title + "**");
			}
			else
			{
				nextTrack();
			}
		}
	}
	
	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception)
	{
		System.err.println("Error loading " + track.getInfo().uri);
		sendToOrigin("An error occured loading **" + track.getInfo().title + "** (<" + track.getInfo().uri + ">). Skipping...");
		nextTrack();
	}
	
	/**
	 * Add the next track to queue, or plays immediately if empty.
	 * @param playNext true if track should be inserted to the top of the queue, false to the back
	 * @param track the track
	 * @param textChannel origin text channel
	 */
	public void queue(boolean playNext, AudioRequest track, MessageChannel textChannel)
	{
		this.messageChannel = textChannel;
		lastPlay = System.currentTimeMillis();
		
		// If queue is empty, immediately start it. Otherwise, add to queue
		if(player.startTrack(track.getAudioTrack(), true))
		{
			track.openAudioConnection();
			currentRequest = track;
		}
		else if(playNext)
		{
			queue.addFirst(track);
		}
		else
		{
			queue.offer(track);
		}
	}
	
	/**
	 * Start the next track, stopping the current one if it is playing.
	 * @return true if the track was skipped, false if nothing was playing
	 */
	private boolean nextTrack()
	{
		boolean isPlaying = isPlaying();
		AudioRequest next = queue.poll();
		
		// If there is no more tracks, stop player
		if(next == null)
		{
			player.startTrack(null, false);
			currentRequest = null;
		}
		else
		{
			player.startTrack(next.getAudioTrack(), false);
			currentRequest = next;
			next.openAudioConnection();
		}
		
		return isPlaying;
	}
	
	/**
	 * Skips a number of tracks.
	 * @param amount number of tracks
	 * @return response to command
	 */
	public String skipTracks(int amount)
	{
		int skipped = 0;
		
		// (amount - 1) to skip the playing track
		// I don't use while loops :sunglasses:
		for(; skipped < (amount - 1) && !queue.isEmpty(); skipped++)
		{
			queue.pop();
		}
		
		String trackName = isPlaying() ? player.getPlayingTrack().getInfo().title : null;
		
		if(nextTrack())
		{
			skipped++;
		}
		
		if(skipped == 0)
		{
			return "Nothing is playing";
		}
		else if(skipped == 1)
		{
			return "Skipped **" + trackName + "**";
		}
		
		return "Skipped **" + skipped + "** track" + (skipped > 1 ? "s" : "");
	}
	
	/**
	 * Fast-forwards the playing track by the provided milliseconds.
	 * @param millis number of millis to fast-forward the track by
	 * @return response to command
	 */
	public String forward(long millis)
	{
		if(!isPlaying()) return "Nothing is playing";
		AudioTrack track = player.getPlayingTrack();
		
		if(track.getInfo().isStream)
		{
			return "Can't fast-forward streams";
		}
		
		long position = Math.min(track.getPosition() + millis, track.getDuration());
		
		// If we skipped to the end
		if(track.getDuration() == position)
		{
			return skipTracks(1);
		}
		
		// Skip the time
		track.setPosition(position);
		return "Skipped to **" + AudioHandler.parseDuration(position) + "**/" + AudioHandler.parseDuration(track.getDuration()) + " of playing track";
	}
	
	/**
	 * @return list of all queued songs, including the playing song
	 */
	public String getQueueList()
	{
		StringBuilder builder = new StringBuilder();
		
		if(currentRequest == null)
		{
			builder.append("The queue is empty");
		}
		else
		{
			builder.append("\u266A " + parseRequestInfo(true, currentRequest));
			
			if(!queue.isEmpty())
			{
				builder.append("\n**On deck**: " + parseRequestInfo(false, queue.peek()));
				int request = 1;
				
				for(AudioRequest sample : queue)
				{
					// Special text for on deck
					if(request == 1)
					{
						request++;
						continue;
					}
					
					builder.append("\n**#" + ++request + "**: " + parseRequestInfo(false, sample));
					
					// If we've already printed 10 tracks, and there's more to print
					if(request > 8 && queue.size() > request)
					{
						// Add back the request = 1 start
						builder.append("\n\t*... " + (queue.size() - request + 1) + " more*");
						break;
					}
				}
			}
			else
			{
				builder.append(". No tracks queued");
			}
		}
		
		String result = builder.toString();
		if(result.length() > Message.MAX_CONTENT_LENGTH) result = result.substring(0, Message.MAX_CONTENT_LENGTH - 3) + "...";
		return result;
	}
	
	private String parseRequestInfo(boolean boldTitle, AudioRequest track)
	{
		AudioTrackInfo info = track.getAudioTrack().getInfo();
		return (boldTitle ? "**" : "") + info.title + (boldTitle ? "**" : "") + " (**" + AudioHandler.parseDuration(info.length) + "**)";
	}
	
	/**
	 * @return true if playing, false otherwise
	 */
	public boolean isPlaying()
	{
		return player.getPlayingTrack() != null ? true : false;
	}
	
	/**
	 * @return currently playing {@link AudioRequest}, or null if nothing is playing
	 */
	public AudioRequest getCurrentRequest()
	{
		return currentRequest;
	}
	
	/**
	 * Enables or disables the current track.
	 * @param toggle true to enable looping, false to disable looping
	 * @return response to command
	 */
	public String loop(boolean toggle)
	{
		// If nothing changed
		if(loop == toggle)
		{
			return "Looping is already " + (loop ? "on" : "off");
		}
		else
		{
			// Sets loop = toggle, and if looping is now off
			if(!(this.loop = toggle))
			{
				loops = 0;
				return "Looping turned off";
			}
			
			return "Looping turned on";
		}
	}
	
	/**
	 * Empties the queue, disables looping, and stops the player.
	 */
	public void reset()
	{
		queue.clear();
		player.startTrack(null, false);
		currentRequest = null;
		loop = false;
		loops = 0;
	}
	
	/**
	 * @return wrapper around AudioPlayer to use it as an AudioSendHandler
	 */
	public AudioPlayerSendHandler getSendHandler()
	{
		return sendHandler;
	}
	
	/**
	 * @return time last track was queued
	 */
	public long timeSinceLastRequest()
	{
		return System.currentTimeMillis() - lastPlay;
	}
	
	public void sendToOrigin(String message)
	{
		if(messageChannel != null)
		{
			System.out.println("Sending \"" + message + "\" to voice request origin");
			messageChannel.sendMessage(message).queue();
		}
		else
		{
			System.out.println("This isn't supposed to happen :( messageChannel is null, tried to send " + message);
		}
	}
}
