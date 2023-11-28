package com.supasulley.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.internal.JDAImpl;

public class Main {
	
	public static final String ERROR_MESSAGE = "[**INTERNAL ERROR**] An unknown error has occured. Try again later.";
	public static String BOT_NAME, BOT_ID;
	
	/** True to use the test bot, false for production */
	private static boolean TEST_BOT = false;
	
	/**
	 * Initializes the bot.
	 * @param botToken required bot token. Assumes not null
	 * @param testBotToken test bot token, entirely for development purposes. Can be null when not used
	 * @param ownerID Discord user ID, used for notifying when online (and allows forks to make custom admin commands). Can be null
	 */
	public Main(String botToken, String testBotToken, String ownerID)
	{
		// If running from jar, do not use test bot
		String resource = Main.class.getResource("Main.class").toString();
		if(resource.startsWith("jar:") || resource.startsWith("rsrc:"))
			TEST_BOT = false;
		
		if(TEST_BOT)
		{
			System.out.println("Using test bot token");
			
			if(testBotToken == null)
			{
				throw new IllegalStateException("Test bot token is null");
			}
		}
		
		// JDA will reconnect after a very long period of downtime (I tested up to 3-4 hours)
		// JDA will immediately fail if you try to create the bot when the internet is unavailable
		JDABuilder builder = JDABuilder.createDefault(TEST_BOT ? testBotToken : botToken).setAutoReconnect(true).setContextMap(null);
		JDA jda = null;
		int attempts = 0;
		
		while(jda == null)
		{
			attempts++;
			
			try {
				jda = (JDAImpl) builder.build().awaitReady();
			} catch(ErrorResponseException t) {
				if(t.getErrorCode() != -1) {
					t.printStackTrace();
					break;
				}
				
				// Wait until we try again
				try {
					System.out.println("Failed to connect to JDA. Retrying in 30s...");
					Thread.sleep(30000);
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			} catch(Throwable t) {
				t.printStackTrace();
				break;
			}
		}
		
		// If still not connected
		if(jda == null || jda.getStatus() != Status.CONNECTED)
		{
			throw new IllegalStateException("Could not connect to JDA");
		}
		
		try {
			initialize(jda);
			
			// Notify if an owner was provided
			if(ownerID != null)
			{
				// Notify of successful launch
				PrivateChannel privateChannel = jda.retrieveUserById(ownerID).complete().openPrivateChannel().complete();
				privateChannel.sendMessage(Main.BOT_NAME + " is online (attempts == **" + attempts + "**) running Java " + System.getProperty("java.version")).complete();
			}
		} catch(Throwable t) {
			System.err.println("An error occured initializing InputListener");
			t.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * Sets up the InputListener.
	 * @param jda connected JDA instance
	 */
	private void initialize(JDA jda)
	{
		jda.getPresence().setPresence(Activity.of(ActivityType.PLAYING, "music"), false);
		BOT_NAME = jda.getSelfUser().getName();
		BOT_ID = jda.getSelfUser().getId();
		
		// Public slash commands
		OptionData songRequest = new OptionData(OptionType.STRING, "query", "Search term or link", true);
		CommandData[] publicCommands = new CommandData [] {
			Commands.slash("play", "Play a song").addOptions(songRequest),
			Commands.slash("next", "Forces a song to play next").addOptions(songRequest),
			Commands.slash("skip", "Skip the song").addOptions(new OptionData(OptionType.INTEGER, "amount", "Number of songs to skip").setRequiredRange(1, 250)),
			Commands.slash("reset", "Skips all songs"),
			Commands.slash("forward", "Fast-forward the song").addOptions(new OptionData(OptionType.INTEGER, "hours", "Number of hours to skip", false).setMinValue(1), new OptionData(OptionType.INTEGER, "minutes", "Number of minutes to skip", false).setMinValue(1), new OptionData(OptionType.INTEGER, "seconds", "Number of seconds to skip", false).setMinValue(1)),
			Commands.slash("loop", "Control looping").addOptions(new OptionData(OptionType.BOOLEAN, "loop", "Whether to turn looping on or off", true)),
			Commands.slash("queue", "See queued songs"),
			Commands.slash("leave", "Leaves the call"),
			Commands.slash("clean-up", "Deletes commands")
		};
		
		// Delete commands
//		jda.retrieveCommands().complete().forEach(hi -> hi.delete().complete());
//		System.out.println("done");
		
		// Update public commands
		System.out.println("Updating slash commands");
		jda.updateCommands().addCommands(publicCommands).complete();
		
		// Create InputListener
		jda.addEventListener(new InputListener(jda));
	}
	
	private static String loadAsString(InputStream stream) throws IOException
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		StringBuilder builder = new StringBuilder(reader.readLine());
		
		for(String line = null; (line = reader.readLine()) != null;)
		{
			builder.append("\n" + line);
		}
		
		return builder.toString();
	}
	
	private static Map<String, String> getArgs(String[] args, String... names)
	{
		Map<String, String> map = new HashMap<String, String>();
		
		for(int i = 0; i < args.length; i++)
		{
			String arg = args[i];
			
			for(String name : names)
			{
				String[] split = arg.split("=");
				
				if(arg.startsWith(name) && split.length == 2)
				{
					map.put(split[0], split[1]);
				}
			}
		}
		
		return map;
	}
	
	public static void main(String[] args)
	{
		String[] names = {"--token", "--test_token", "--owner_id", "--file"};
		Map<String, String> argsMap = getArgs(args, names);
		
		File file = new File(argsMap.computeIfAbsent("--file", key -> "tokens.json"));
		
		try {
			String raw = loadAsString(new FileInputStream(file));
			Map<String, Object> tokensMap = JacksonUtils.parseJSON(JacksonUtils.MAP, raw);
			
			// Fill optional values
			for(String name : names)
			{
				Object key = tokensMap.get(name.substring(2));
				
				// If the value exists in the tokens file and it's not already filled from environment variables
				if(key != null)
				{
					argsMap.putIfAbsent(name, key.toString());
				}
			}
		} catch(FileNotFoundException e) {
			// It's only an error if the file is required
			if(!argsMap.containsKey("--token"))
			{
				System.err.println("Failed to find tokens file at " + file.getAbsolutePath() + ". Create one and paste your bot token as a JSON name value pair {\"token\": \"insert_token_here\"}. Alternatively, pass --token=insert_token_here as a program argument.");
				return;
			}
		} catch(JsonProcessingException e) {
			System.err.println("An error occured parsing JSON from " + file.getAbsolutePath() + ". Make sure the file's JSON is properly formatted.");
			e.printStackTrace();
			return;
		} catch(IOException e) {
			System.err.println("An unknown error occurred");
			e.printStackTrace();
			return;
		}
		
		// Require bot token
		if(!argsMap.containsKey("--token"))
		{
			System.err.println("You need to provide a bot token. You can provide one by creating a tokens.json file, or by passing --token=insert_token_here as a program argument.");
			return;
		}
		
		new Main(argsMap.get("--token"), argsMap.get("--test_token"), argsMap.get("--owner_id"));
	}
}
