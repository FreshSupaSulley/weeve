package com.supasulley.main;

import java.util.function.Supplier;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Web;

//playerManager.registerSourceManager(new YoutubeAudioSourceManager(true, null, null));
//playerManager.registerSourceManager(new YandexMusicAudioSourceManager(true));
//playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
//playerManager.registerSourceManager(new BandcampAudioSourceManager());
//playerManager.registerSourceManager(new VimeoAudioSourceManager());
//playerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
//playerManager.registerSourceManager(new BeamAudioSourceManager());
//playerManager.registerSourceManager(new GetyarnAudioSourceManager());
//playerManager.registerSourceManager(new NicoAudioSourceManager());
//playerManager.registerSourceManager(new HttpAudioSourceManager(containerRegistry));

public enum AudioSource
{
	/*YOUTUBE("YouTube", YoutubeAudioSourceManager.MUSIC_SEARCH_PREFIX), */SOUNDCLOUD("SoundCloud", "scsearch:"), BANDCAMP("Bandcamp", "bcsearch:");
	
	private String fancyName, searchPrefix;
	
	private AudioSource(String fancyName, String searchPrefix, Supplier<Boolean> canProcessRequest)
	{
		this.fancyName = fancyName;
		this.searchPrefix = searchPrefix;
	}
	
	private AudioSource(String fancyName, String searchPrefix)
	{
		this(fancyName, searchPrefix, () -> true);
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
