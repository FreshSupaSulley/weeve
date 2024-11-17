package com.supasulley.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.ICommandReference;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.internal.JDAImpl;

public class Main {
	
	public static final Logger log = (Logger) LoggerFactory.getLogger(Main.class);
	public static final File LOGS_DIR = new File("logs");
	public static final File LOG_CURRENT = new File(LOGS_DIR.getAbsolutePath() + "/log-current.log");
	public static final int MAX_LOGS = 5;
	
	public static final String ERROR_MESSAGE = "[**INTERNAL ERROR**] An unknown error has occured. Try again later.";
	private static List<Command> commands;
	
	private static JDA jda;
	
	/** True to use the test bot, false for production */
	private static boolean TEST_BOT = true;
	
	// Error handling when --notify_errors is enabled
	private static final int MAX_CONSECUTIVE_ERRORS = 10;
	private static final int CONSECUTIVE_INTERVAL = 1000;
	private static final int DECREASE_RATE = CONSECUTIVE_INTERVAL * 100;
	
	private static long lastError = System.currentTimeMillis();
	private static int consecutiveErrors;
	private static String ownerID = null;
	private static boolean notifyErrors = false;
	
	static
	{
		// If running from jar, do not use test bot
		String resource = Main.class.getResource("Main.class").toString();
		if(resource.startsWith("jar:") || resource.startsWith("rsrc:"))
			TEST_BOT = false;
		
		// Print uncaught exceptions to the logs instead of the console
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
		{
			log.error(thread.getName(), throwable);
		});
		
		log.info("PROGRAM START at " + System.currentTimeMillis());
	}
	
	/**
	 * Initializes the bot.
	 * @param botToken required bot token. Assumes not null
	 * @param testBotToken test bot token, entirely for development purposes. Can be null when not used
	 * @param ownerID Discord user ID, used for notifying when online (and allows forks to make custom admin commands). Can be null
	 */
	public Main(String botToken, String ownerID, boolean notifyErrors)
	{
		Main.notifyErrors = notifyErrors;
		Main.ownerID = ownerID;
		
		if(notifyErrors && ownerID == null)
		{
			throw new IllegalStateException("notify_errors enabled, but no owner_id provided.");
		}
		
		// JDA will reconnect after a very long period of downtime (I tested up to 3-4 hours)
		// JDA will immediately fail if you try to create the bot when the internet is unavailable
		JDABuilder builder = JDABuilder.createDefault(botToken).setAutoReconnect(true).setContextMap(null);
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
		
		Main.jda = jda;
		
		try {
			initialize(jda, ownerID);
			
			// Notify if an owner was provided
			if(ownerID != null)
			{
				// If DM wasn't successful (hold the program until finished)
				if(!sendToOperator(Main.getBotName() + " is online (attempts == **" + attempts + "**) running Java " + System.getProperty("java.version")).get())
				{
					System.err.println("Can't send messages to the owner yet. You need to interact with the bot first. This is a non-fatal error.");
				}
				
				if(notifyErrors)
				{
					sendToOperator("`notify_errors` enabled. Full details in the logs created in the running directory (`" + LOGS_DIR.getAbsolutePath() + "`)");
				}
			}
		} catch(Throwable t) {
			System.err.println("An error occured initializing InputListener");
			t.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * Logs an error and sends to operator if enabled.
	 * 
	 * @param throwable the error to log
	 */
	public static void error(Throwable throwable)
	{
		Main.error(throwable.getMessage(), throwable);
	}
	
	/**
	 * Logs an error and sends to operator if enabled.
	 * 
	 * @param summary   summary of the error
	 * @param throwable the error to log
	 */
	public static void error(String summary, Throwable error)
	{
		// Log the error
		Main.log.error(Thread.currentThread().getName(), error);
		
		// Don't send to operator if notify errors aren't enabled
		if(!notifyErrors) return;
		
		// Get time between errors
		long current = System.currentTimeMillis();
		long distance = current - Main.lastError;
		
		// If this error occurred too soon after the last
		if(distance < CONSECUTIVE_INTERVAL)
		{
			// If the consecutive errors have reached the maximum allowed
			if(++Main.consecutiveErrors == MAX_CONSECUTIVE_ERRORS)
			{
				// Warn the owner that something is definitely wrong
				sendToOperator("Too many consecutive errors. Check logs.");
			}
		}
		// If we haven't had an error in a while
		else
		{
			// 100 seconds needs to pass to decrease consecutive errors by 1
			Main.consecutiveErrors = Math.max(0, Main.consecutiveErrors = (int) (distance / DECREASE_RATE));
		}
		
		// Only DM the user if we're under the max
		if(Main.consecutiveErrors < MAX_CONSECUTIVE_ERRORS)
		{
			sendToOperator("**Error** (" + Main.consecutiveErrors + "/" + MAX_CONSECUTIVE_ERRORS + " consecutive) - " + summary);
			Main.lastError = System.currentTimeMillis();
		}
	}
	
	/**
	 * Sends a DM to the operator for error logging. Requires owner ID to be set.
	 * 
	 * @param text message to send
	 * @return {@linkplain CompletableFuture} promise returning true if successful, false otherwise
	 */
	private static CompletableFuture<Boolean> sendToOperator(String text)
	{
		// Notfy owner if enabled
		if(Main.ownerID != null)
		{
			PrivateChannel channel = jda.retrieveUserById(ownerID).complete().openPrivateChannel().complete();
			
			return channel.sendMessage(text).submit().handleAsync((message, throwable) ->
			{
				return throwable == null;
			});
		}
		
		return CompletableFuture.completedFuture(false);
	}
	
	/**
	 * Sets up the InputListener.
	 * 
	 * @param jda     connected JDA instance
	 * @param ownerID optional ID of owner
	 */
	private void initialize(JDA jda, String ownerID)
	{
		jda.getPresence().setPresence(Activity.of(ActivityType.PLAYING, "music"), false);
		
		SubcommandData list = new SubcommandData("list", "Lists all log files");
		SubcommandData get = new SubcommandData("get", "Gets a log file").addOption(OptionType.STRING, "file", "Log file name", true);
		SubcommandData clear = new SubcommandData("clear", "Deletes old log files");
		
		OptionData source = new OptionData(OptionType.STRING, "source", "Audio source");
		
		for(AudioSource sample : AudioSource.values())
		{
			source.addChoice(sample.getFancyName(), sample.name());
		}
		
		// Public slash commands
		CommandData[] commands = new CommandData [] {
			// Public
			Commands.slash("play", "Play a song").addOption(OptionType.STRING, "query", "Search term or link", true).addOptions(source).addOption(OptionType.BOOLEAN, "next", "Plays this track next").setGuildOnly(true),
//			Commands.slash("ytcookies", "Supply YouTube cookies").addOption(OptionType.STRING, "cookies", "Exported YouTube cookies", true).setGuildOnly(true),
//			Commands.slash("next", "Forces a song to play next").addOptions(songRequest).setGuildOnly(true),
			Commands.slash("skip", "Skip the song").addOptions(new OptionData(OptionType.INTEGER, "amount", "Number of songs to skip").setRequiredRange(1, 250)).setGuildOnly(true),
			Commands.slash("forward", "Fast-forward the song").addOptions(new OptionData(OptionType.INTEGER, "hours", "Number of hours to skip", false).setMinValue(1), new OptionData(OptionType.INTEGER, "minutes", "Number of minutes to skip", false).setMinValue(1), new OptionData(OptionType.INTEGER, "seconds", "Number of seconds to skip", false).setMinValue(1)).setGuildOnly(true),
			Commands.slash("loop", "Control looping").addOptions(new OptionData(OptionType.BOOLEAN, "loop", "Whether to turn looping on or off", true)).setGuildOnly(true),
			Commands.slash("queue", "See queued songs").setGuildOnly(true),
			Commands.slash("stop", "Stops playback").setGuildOnly(true),
			Commands.slash("leave", "Leaves the call").setGuildOnly(true),
			
			// Private
			Commands.slash("logs", "Manage log files").setDefaultPermissions(DefaultMemberPermissions.DISABLED).addSubcommands(list, get, clear),
			
			// Both
			Commands.slash("clean-up", "Deletes commands")
		};
		
		// Update public commands
		// "When a command is not listed in this request, it will be deleted." Don't need to worry about old commands
		Main.log.info("Updating slash commands");
		Main.commands = jda.updateCommands().addCommands(commands).complete();
		
		// Create InputListener
		jda.addEventListener(new InputListener(jda, ownerID));
	}
	
	public static ICommandReference getCommandByName(String commandName)
	{
		for(Command command : Main.commands)
		{
			for(Subcommand subcommand : command.getSubcommands())
			{
				if(subcommand.getFullCommandName().equals(commandName))
				{
					return subcommand;
				}
			}
			
			if(command.getName().equals(commandName))
			{
				return command;
			}
		}
		
		Main.log.error("Failed to find command by name " + commandName);
		return null;
	}
	
	public static String getBotName()
	{
		return jda.getSelfUser().getName();
	}
	
	public static String getBotID()
	{
		return jda.getSelfUser().getId();
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
		
		for(String arg : args)
		{
			String[] split = arg.split("=");
			
			// If there is an equals sign
			if(split.length > 1)
			{
				for(String name : names)
				{
					if(arg.startsWith(name))
					{
						map.put(split[0], split[1]);
					}
				}
			}
			// Otherwise, it's a flag
			else
			{
				map.put(arg, "true");
			}
		}
		
		return map;
	}
	
	public static void main(String[] args)
	{
		// Read command line arguments
		String[] names = {"--token", "--test_token", "--owner_id", "--notify_errors", "--file"};
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
			// It's only an error if there wasn't a token provided in the command line either
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
		
		String token = !TEST_BOT ? argsMap.get("--token") : argsMap.get("--test_token");
		
		// Require bot token
		if(token == null)
		{
			System.err.println("You need to provide a bot token. You can provide one by creating a tokens.json file, or by passing --token=insert_token_here as a program argument.");
			return;
		}
		
		new Main(token, argsMap.get("--owner_id"), Boolean.parseBoolean(argsMap.get("--notify_errors")));
	}
}
