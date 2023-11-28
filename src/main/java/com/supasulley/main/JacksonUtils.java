package com.supasulley.main;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates HTTP requests for interacting with third-parties.
 */
public class JacksonUtils {
	
	public static final TypeReference<Map<String, Object>> MAP = new TypeReference<Map<String, Object>>() {};
	private static final ObjectMapper mapper = new ObjectMapper();
	
	/**
	 * Parses JSON data into a provided format.<br><br>
	 * Used when JSON is too deeply nested to resolve with simple methods.
	 * @param <T> returned object
	 * @param raw raw JSON
	 * @param type determines how to deserialize the data
	 * @return new object (defined by TypeReference)
	 */
	public static <T> T parseJSON(TypeReference<T> type, String raw) throws JsonProcessingException
	{
		return mapper.readValue(raw, type);
	}
	
	/**
	 * Creates an ArrayNode object to build JSON arrays.
	 * @return new ArrayNode
	 */
	public static ArrayNode createArrayNode()
	{
		return mapper.createArrayNode();
	}
	
	/**
	 * Creates an ObjectNode object to build JSON objects.
	 * @return new ArrayNode
	 */
	public static ObjectNode createObjectNode()
	{
		return mapper.createObjectNode();
	}
}
