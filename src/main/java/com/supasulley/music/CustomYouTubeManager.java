package com.supasulley.music;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParserException;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.supasulley.main.Main;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.http.RefreshTokenQueryResponse;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class CustomYouTubeManager extends YoutubeAudioSourceManager {
	
	// OAuth information
	private long lastOauthCheck, interval;
	private String userCode, deviceCode;
	
	public CustomYouTubeManager(@NotNull Client... clients)
	{
		super(clients);
	}
	
	@Override
	public @Nullable AudioItem loadItem(@NotNull AudioPlayerManager manager, @NotNull AudioReference reference)
	{
		// Return null if this isn't a YouTube request
		try(HttpInterface httpInterface = httpInterfaceManager.getInterface())
		{
			Router router = getRouter(httpInterface, reference.identifier);
			
			if(router == null)
			{
				Main.log.debug("YouTube can't handle this audio reference, exiting");
				return null;
			}
		} catch(IOException e)
		{
			throw ExceptionTools.toRuntimeException(e);
		}
		
		// First check if we are authenticated with YouTube
		// If we don't have a refresh token yet
		if(getOauth2RefreshToken() == null)
		{
			// If we never logged in before
			if(this.userCode == null)
			{
				Main.log.info("Starting oauth");
				
				// Start OAuth
				JsonObject object = this.getOauth2Handler().fetchDeviceCode();
				this.userCode = object.getString("user_code");
				this.deviceCode = object.getString("device_code");
				this.interval = object.getLong("interval") * 1000; // to ms
			}
			else
			{
				// Check if it came in the mail today
				// If we are already in a state of checking and we can check again
				if(System.currentTimeMillis() - lastOauthCheck > (interval == 0 ? 5000 : interval))
				{
					this.lastOauthCheck = System.currentTimeMillis();
					
					// Check if authenticated
					try
					{
						Main.log.info("Checking if oauth is completed");
						RefreshTokenQueryResponse response = this.getOauth2Handler().getRefreshTokenByDeviceCode(deviceCode);
						String error = response.getError();
						
						// If we don't have the code yet
						if(error != null)
						{
							switch(error)
							{
								case "authorization_pending":
								case "slow_down":
									// Still waiting
									Main.log.info("Still waiting on response");
									break;
								default:
									// Error occurred. Restart login
									throw new IOException(error);
							}
						}
						else
						{
							// Success!
							Main.log.info("Linked with YouTube");
							JsonObject json = response.getJsonObject();
							this.getOauth2Handler().updateTokens(json);
							
							// Manually set the refresh token to toggle enable flag
							this.getOauth2Handler().setRefreshToken(json.getString("refresh_token"), true);
						}
					} catch(IOException | JsonParserException e)
					{
						Main.log.warn("Failed to link with Google");
						Main.log.warn(e.getMessage());
						this.userCode = null;
						this.deviceCode = null;
					}
				}
			}
			
			// Check again if we have code
			if(getOauth2RefreshToken() == null)
			{
				throw new NoCredentialsException((event) -> event.sendMessage("__To start using **YouTube**__:\nLink " + Main.getBotName() + " with a burner Google account. Go to <https://www.google.com/device> and enter code **" + userCode + "**.\n*You only need to do this once!*").addActionRow(Button.link("https://www.google.com/device", "Link")));
			}
		}
		
		return super.loadItem(manager, reference);
	}
	
	/**
	 * @return true if YouTube has the appropriate credentials to start handling requests, false otherwise
	 */
	public boolean canHandle()
	{
		return getOauth2RefreshToken() != null;
	}
}
