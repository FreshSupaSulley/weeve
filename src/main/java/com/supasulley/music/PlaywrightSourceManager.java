package com.supasulley.music;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class PlaywrightSourceManager implements AudioSourceManager {
	
	@Override
	public String getSourceName()
	{
		// FIXME Auto-generated method stub
		return null;
	}
	
	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference)
	{
		// FIXME Auto-generated method stub
		return null;
	}
	
	@Override
	public boolean isTrackEncodable(AudioTrack track)
	{
		// FIXME Auto-generated method stub
		return false;
	}
	
	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException
	{
		// FIXME Auto-generated method stub
		
	}
	
	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException
	{
		// FIXME Auto-generated method stub
		return null;
	}
	
	@Override
	public void shutdown()
	{
		// FIXME Auto-generated method stub
		
	}
}
