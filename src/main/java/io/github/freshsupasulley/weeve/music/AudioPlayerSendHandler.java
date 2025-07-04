package io.github.freshsupasulley.weeve.music;

import java.nio.ByteBuffer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import net.dv8tion.jda.api.audio.AudioSendHandler;

/**
 * Wrapper for the {@link AudioSendHandler}.
 */
public class AudioPlayerSendHandler implements AudioSendHandler {
	
	private final AudioPlayer audioPlayer;
	private final ByteBuffer buffer;
	private final MutableAudioFrame frame;
	
	public AudioPlayerSendHandler(AudioPlayer audioPlayer)
	{
		this.audioPlayer = audioPlayer;
		this.buffer = ByteBuffer.allocate(1024);
		this.frame = new MutableAudioFrame();
		this.frame.setBuffer(buffer);
	}
	
	@Override
	public boolean canProvide()
	{
		return audioPlayer.provide(frame);
	}
	
	@Override
	public ByteBuffer provide20MsAudio()
	{
		return buffer.flip();
	}
	
	@Override
	public boolean isOpus()
	{
		return true;
	}
}