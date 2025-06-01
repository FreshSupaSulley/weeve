package io.github.freshsupasulley.main;

import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class JsonUtils {
	
	private static final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
	
	/**
	 * Converts text to a {@link JsonObject}.
	 * 
	 * @param string string to parse
	 * @return new {@link JsonObject}
	 */
	public static JsonObject parse(String string) throws JsonSyntaxException
	{
		return JsonParser.parseString(string).getAsJsonObject();
	}
	
	/**
	 * Fires the resolver if the {@linkplain JsonElement} is present, or returns the fallback if not.
	 * 
	 * @param <T>      fallback type
	 * @param json     {@link JsonElement} object
	 * @param fallback object to return if failed
	 * @param resolver fired if the value is present
	 * @return value returned by the resolver if present, or the fallback if not
	 */
	public static <T> T getOrDefault(JsonElement json, T fallback, Function<JsonElement, T> resolver)
	{
		if(json == null)
			return fallback;
		return resolver.apply(json);
	}
	
	/**
	 * Serializes an object into a string.
	 * 
	 * @param object object to serialize
	 * @return string representing the serialized object
	 */
	public static String serialize(Object object)
	{
		return gson.toJson(object);
	}
	
	/**
	 * Deserializes a string into an object.
	 * 
	 * @param <T>   object type
	 * @param clazz the class of T
	 * @param json  JSON string representing an instance of T
	 * @return an object of type T from the string
	 */
	public static <T> T deserialize(Class<T> clazz, String json)
	{
		return gson.fromJson(json, clazz);
	}
}
