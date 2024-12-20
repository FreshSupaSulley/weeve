package com.supasulley.main;

import java.util.function.BiFunction;

import com.google.gson.JsonElement;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
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
//	SPOTIFY("Spotify", SpotifySourceManager.SEARCH_PREFIX, (json, playerManager) -> new SpotifySourceManager("b8bba9b5d74e4a4e920b2b01f0d0da38", "6027c2ecdb5445aca6d61cea4d0702bd", "US", playerManager, new DefaultMirroringAudioTrackResolver(new String[] {"scsearch:\"" + MirroringAudioSourceManager.ISRC_PATTERN + "\"",
//										"scsearch:" + MirroringAudioSourceManager.QUERY_PATTERN})));
	
	private String fancyName, searchPrefix;
	private AudioSourceManager manager;
	
	private BiFunction<JsonElement, AudioPlayerManager, AudioSourceManager> function;
	
	private AudioSource(String fancyName, String searchPrefix, AudioSourceManager manager)
	{
		this.fancyName = fancyName;
		this.searchPrefix = searchPrefix;
		this.manager = manager;
	}
	
	private AudioSource(String fancyName, String searchPrefix, BiFunction<JsonElement, AudioPlayerManager, AudioSourceManager> function)
	{
		this(fancyName, searchPrefix, (AudioSourceManager) null);
		
		this.function = function;
	}
	
	public AudioSourceManager getManager(JsonElement json, AudioPlayerManager playerManager)
	{
		if(this == YOUTUBE)
		{
			((YoutubeAudioSourceManager) manager).useOauth2(null, false);
		}
		
		// If not created yet
		if(manager == null)
		{
			Main.log.info("Creating AudioSourceManager on {}", this.toString());
			this.manager = function.apply(json, playerManager);
		}
		
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
