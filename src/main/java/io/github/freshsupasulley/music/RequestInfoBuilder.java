package io.github.freshsupasulley.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public class RequestInfoBuilder {
	
	private boolean bold, showPosition, showDuration, showLink;
	
	public RequestInfoBuilder bold()
	{
		this.bold = true;
		return this;
	}
	
	public RequestInfoBuilder showPosition()
	{
		// Duration must also be enabled to show position but this is handled in apply
		this.showPosition = true;
		return this;
	}
	
	public RequestInfoBuilder showDuration()
	{
		this.showDuration = true;
		return this;
	}
	
	public RequestInfoBuilder showLink()
	{
		this.showLink = true;
		return this;
	}
	
	public String apply(AudioTrack track)
	{
		AudioTrackInfo info = track.getInfo();
		StringBuilder builder = new StringBuilder();
		
		// Bold
		// Sanitize title
		builder.append((bold ? "**" : "") + MarkdownSanitizer.sanitize(info.title) + (bold ? "**" : ""));
		
		// Duration / position
		// If tbis isn't a stream
		if(!info.isStream)
		{
			builder.append(showDuration ? " (**" + (showPosition ? AudioHandler.parseDuration(track.getPosition()) + " / " : "") + AudioHandler.parseDuration(info.length) + "**)" : "");
		}
		
		// Show link
		builder.append(showLink ? " [(link)](<" + info.uri + ">)" : "");
		
		return builder.toString();
	}
}
