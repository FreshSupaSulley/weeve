package io.github.freshsupasulley.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

public class AudioRequest {
	
	private AudioTrack track;
	private AudioChannel audioChannel;
	
	public AudioRequest(AudioTrack track, AudioChannel audioChannel)
	{
		this.track = track;
		this.audioChannel = audioChannel;
	}
	
	public AudioChannel getAudioChannel()
	{
		return audioChannel;
	}
	
	public AudioTrack getAudioTrack()
	{
		return track;
	}
	
	public void openAudioConnection()
	{
		audioChannel.getGuild().getAudioManager().openAudioConnection(audioChannel);
	}
}
