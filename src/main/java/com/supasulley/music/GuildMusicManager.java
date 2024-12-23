package com.supasulley.music;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.supasulley.main.Main;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * Handles all music management for a guild.
 */
public class GuildMusicManager extends AudioEventAdapter {
	
	private final AudioPlayer player;
	private final AudioPlayerSendHandler sendHandler;
	
	private long leaveTime;
	
	// Scheduler
	private final LinkedBlockingDeque<AudioRequest> queue;
	private boolean loop;
	
	/** Stores the last channel someone sent a message in */
	private MessageChannel messageChannel;
	
//	private Map<String, List<AudioTrack>> requestMap;
	private Map<String, Map<String, ButtonAction>> requestMap;
	
	public GuildMusicManager(AudioPlayerManager manager)
	{
		this.player = manager.createPlayer();
		this.player.addListener(this);
		this.sendHandler = new AudioPlayerSendHandler(player);
		this.queue = new LinkedBlockingDeque<AudioRequest>();
		this.requestMap = new HashMap<String, Map<String, ButtonAction>>();
	}
	
	/**
	 * @return time (millis) when the bot should leave the call.
	 */
	public long getLeaveTime()
	{
		return leaveTime;
	}
	
	public void addButtonAction(Message message, Button button, ButtonAction action)
	{
		if(requestMap.computeIfAbsent(message.getId(), key -> new HashMap<String, ButtonAction>()).put(button.getId(), action) != null)
		{
			Main.log.error("Another button action mapping was already made for button ID " + button.getId() + " of " + action);
		}
	}
	
	public void runButtonAction(String messageID, ButtonInteractionEvent event, AudioHandler handler)
	{
		Map<String, ButtonAction> result = requestMap.remove(messageID);
		
		if(result == null)
		{
			event.deferEdit().setComponents().setContent("This event has expired. Try " + Main.getCommandReference("play") + " again.").queue();
			return;
		}
		
		ButtonAction action = result.get(event.getButton().getId());
		action.handle(event, handler);
		action.fire(event.getHook(), event.getButton(), handler);
	}
	
	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason)
	{
		leaveTime = System.currentTimeMillis() + AudioHandler.IDLE_TIME;
		
		// Only start the next track if the end reason is suitable for it (FINISHED or LOAD_FAILED)
		if(endReason.mayStartNext)
		{
			if(loop)
			{
				player.startTrack(track.makeClone(), false);
				// This makes short tracks susceptible to causing spam
//				sendToOrigin("Loop **" + ++loops + "** of " + new RequestInfoBuilder().bold().apply(track));
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
		Main.error("Error loading <" + track.getInfo().uri + ">", exception);
		sendToOrigin("Error loading " + new RequestInfoBuilder().bold().apply(track) + " [(link)](<" + track.getInfo().uri + ">): `" + exception.getMessage() + "`");
		nextTrack();
	}
	
	/**
	 * Add the next track to queue, or plays immediately if empty.
	 * 
	 * @param playNext    true if track should be inserted to the top of the queue, false to the back
	 * @param track       the track
	 * @param textChannel origin text channel
	 */
	public void queue(boolean playNext, AudioRequest track, MessageChannel textChannel)
	{
		leaveTime = 0;
		this.messageChannel = textChannel;
		
		// If queue is empty, immediately start it. Otherwise, add to queue
		if(player.startTrack(track.getAudioTrack(), true))
		{
			track.openAudioConnection();
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
	 * 
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
		}
		else
		{
			player.startTrack(next.getAudioTrack(), false);
			next.openAudioConnection();
		}
		
		return isPlaying;
	}
	
	/**
	 * Skips a number of tracks.
	 * 
	 * @param amount number of tracks
	 * @return response to command
	 */
	public String skipTracks(int amount)
	{
		int skipped = 0;
		
		// (amount - 1) to skip the playing track
		// Fuck while loops :sunglasses:
		for(; skipped < (amount - 1) && !queue.isEmpty(); skipped++)
		{
			queue.pop();
		}
		
		// NEW BEHAVIOR:
		// Disable looping upon skipping
		boolean oldLoop = loop;
		loop(false);
		
		// If looping was stopped
		String loopSuffix = "";
		
		if(oldLoop != loop)
		{
			loopSuffix = " and stopped loop";
		}
		
		AudioTrack playingTrack = player.getPlayingTrack();
		
		// nextTrack() DOES SOMETHING!!
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
			// Old code suggests playing track could be null here at some point
			// Choosing to look the other way like a piece of shit
			return "Skipped " + new RequestInfoBuilder().bold().apply(playingTrack) + loopSuffix;
		}
		
		// If the queue is now cleared it means you skipped all tracks
		return "Skipped " + (queue.isEmpty() ? "all" : "**" + skipped + "**") + " track" + (skipped > 1 ? "s" : "") + loopSuffix;
	}
	
	/**
	 * Fast-forwards the playing track by the provided milliseconds.
	 * 
	 * @param millis number of millis to fast-forward the track by
	 * @return response to command
	 */
	public String forward(long millis)
	{
		if(!isPlaying())
			return "Nothing is playing";
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
		
		if(!isPlaying())
		{
			builder.append("The queue is empty");
		}
		else
		{
			builder.append("\u266A " + new RequestInfoBuilder().bold().showDuration().showLink().showPosition().apply(getCurrentRequest()));
			
			if(!queue.isEmpty())
			{
				builder.append("\n**On deck**: " + new RequestInfoBuilder().showDuration().showLink().apply(queue.peek().getAudioTrack()));
				int request = 1;
				
				for(AudioRequest sample : queue)
				{
					// Special text for on deck
					if(request == 1)
					{
						request++;
						continue;
					}
					
					builder.append("\n**#" + ++request + "**: " + new RequestInfoBuilder().showDuration().showLink().apply(sample.getAudioTrack()));
					
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
		if(result.length() > Message.MAX_CONTENT_LENGTH)
			result = result.substring(0, Message.MAX_CONTENT_LENGTH - 3) + "...";
		return result;
	}
	
	/**
	 * @return true if playing, false otherwise
	 */
	public boolean isPlaying()
	{
//		return !queue.isEmpty();
		return player.getPlayingTrack() != null;
	}
	
	/**
	 * @return currently playing {@link AudioRequest}, or null if nothing is playing
	 */
	public AudioTrack getCurrentRequest()
	{
		return player.getPlayingTrack();
//		return queue.peek();
	}
	
	/**
	 * Enables or disables the current track.
	 * 
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
				return "Looping turned off";
			}
			
			return "Looping turned on. Skipping the track will stop the loop.";
		}
	}
	
	/**
	 * Empties the queue, disables looping, and stops the player.
	 */
	public void reset()
	{
		requestMap.clear();
		queue.clear();
		player.startTrack(null, false);
		loop = false;
	}
	
	/**
	 * @return wrapper around AudioPlayer to use it as an AudioSendHandler
	 */
	public AudioPlayerSendHandler getSendHandler()
	{
		return sendHandler;
	}
	
	public void sendToOrigin(String message)
	{
		if(messageChannel != null)
		{
			Main.log.error("Sending \"{}\" to voice request origin", message);
			messageChannel.sendMessage(message).queue();
		}
		else
		{
			Main.log.error("This isn't supposed to happen :( messageChannel is null, tried to send {}", message);
		}
	}
}
