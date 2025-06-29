package io.github.freshsupasulley.weeve.music;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public interface ButtonAction {
	
	default void handle(ButtonInteractionEvent event, AudioHandler handler)
	{
		event.deferEdit().queue(success ->
		{
			success.deleteOriginal().queue();
		});
	}
	
	public void fire(InteractionHook hook, Button button, AudioHandler handler);
}
