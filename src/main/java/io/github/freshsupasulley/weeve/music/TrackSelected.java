package io.github.freshsupasulley.weeve.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class TrackSelected implements ButtonAction {
	
	private boolean playNext;
	private AudioTrack track;
	private AudioChannel audioChannel;
	
	public TrackSelected(boolean playNext, AudioTrack track, AudioChannel audioChannel)
	{
		this.playNext = playNext;
		this.track = track;
		this.audioChannel = audioChannel;
	}
	
	@Override
	public void fire(InteractionHook hook, Button button, AudioHandler handler)
	{
		handler.loadAndPlay(playNext, track.getInfo().uri, audioChannel, hook.getInteraction().getMessageChannel());
	}
}
