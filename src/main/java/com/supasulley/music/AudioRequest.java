package com.supasulley.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class AudioRequest {
	
	private AudioTrack track;
	private AudioChannel audioChannel;
	private MessageChannel requestOrigin;
	
	public AudioRequest(AudioTrack track, AudioChannel audioChannel, MessageChannel requestOrigin)
	{
		this.track = track;
		this.audioChannel = audioChannel;
		this.requestOrigin = requestOrigin;
	}
	
	public AudioTrack getAudioTrack()
	{
		return track;
	}
	
	public void sendToOrigin(String message)
	{
		System.out.println("Sending \"" + message + "\" to voice request origin");
		requestOrigin.sendMessage(message).queue();
	}
	
	public void openAudioConnection()
	{
		audioChannel.getGuild().getAudioManager().openAudioConnection(audioChannel);
	}
}
