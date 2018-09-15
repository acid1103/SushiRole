package org.abitoff.discord.sushirole.events;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class GlobalEventListener extends ListenerAdapter
{
	public final static GlobalEventListener listener = new GlobalEventListener();

	private GlobalEventListener()
	{
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event)
	{
		System.out.println("In " + event.getGuild().getName() + "#" + event.getChannel().getName() + ", "
				+ event.getAuthor().getName() + " said:\n\"" + event.getMessage().getContentDisplay() + "\"");
	}
}
