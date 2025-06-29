package io.github.freshsupasulley.weeve.music;

import io.github.freshsupasulley.weeve.AudioSource;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class SearchRequest implements ButtonAction {
	
	private boolean isSearch, playNext;
	private String query;
	private AudioSource source;
	private AudioChannel audioChannel;
	private GuildMusicManager manager;
	
	public SearchRequest(boolean isSearch, boolean playNext, String query, AudioSource source, AudioChannel audioChannel, GuildMusicManager manager)
	{
		this.isSearch = isSearch;
		this.playNext = playNext;
		this.query = query;
		this.source = source;
		this.audioChannel = audioChannel;
		this.manager = manager;
	}
	
	@Override
	public void fire(InteractionHook hook, Button button, AudioHandler handler)
	{
		handler.searchQuery(hook, manager, isSearch, playNext, query, source, audioChannel);
	}
}
