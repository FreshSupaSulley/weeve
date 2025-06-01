package io.github.freshsupasulley.music;

import java.util.function.Function;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;

public class NoCredentialsException extends RuntimeException {
	
	private static final long serialVersionUID = 7097178438576540541L;
	private Function<InteractionHook, RestAction<Message>> action;
	
	public NoCredentialsException(Function<InteractionHook, RestAction<Message>> action)
	{
		this.action = action;
	}
	
	public void fire(InteractionHook hook)
	{
		action.apply(hook).queue();
	}
}
