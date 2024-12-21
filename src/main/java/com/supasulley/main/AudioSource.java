package com.supasulley.main;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.TvHtml5Embedded;

// playerManager.registerSourceManager(new YoutubeAudioSourceManager(true, null, null));
// playerManager.registerSourceManager(new YandexMusicAudioSourceManager(true));
// playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
// playerManager.registerSourceManager(new BandcampAudioSourceManager());
// playerManager.registerSourceManager(new VimeoAudioSourceManager());
// playerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
// playerManager.registerSourceManager(new BeamAudioSourceManager());
// playerManager.registerSourceManager(new GetyarnAudioSourceManager());
// playerManager.registerSourceManager(new NicoAudioSourceManager());
// playerManager.registerSourceManager(new HttpAudioSourceManager(containerRegistry));

public enum AudioSource
{
	YOUTUBE("YouTube", YoutubeAudioSourceManager.MUSIC_SEARCH_PREFIX, new YoutubeAudioSourceManager(new TvHtml5Embedded())),
	SOUNDCLOUD("SoundCloud", "scsearch:", SoundCloudAudioSourceManager.createDefault()),
	BANDCAMP("Bandcamp", "bcsearch:", new BandcampAudioSourceManager());
	
	private String fancyName, searchPrefix;
	private AudioSourceManager manager;
	
	private AudioSource(String fancyName, String searchPrefix, AudioSourceManager manager)
	{
		this.fancyName = fancyName;
		this.searchPrefix = searchPrefix;
		this.manager = manager;
	}
	
	public AudioSourceManager getManager()
	{
		return manager;
	}
	
	public String getFancyName()
	{
		return fancyName;
	}
	
	public String getSearchPrefix()
	{
		return searchPrefix;
	}
}
