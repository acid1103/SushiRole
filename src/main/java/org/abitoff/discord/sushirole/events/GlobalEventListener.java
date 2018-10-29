package org.abitoff.discord.sushirole.events;

import org.abitoff.discord.sushirole.commands.discord.DiscordCommand;

import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public class GlobalEventListener extends ListenerAdapter
{
	public final static GlobalEventListener listener = new GlobalEventListener();

	private GlobalEventListener()
	{
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event)
	{
		if (event.getAuthor().getIdLong() != event.getJDA().getSelfUser().getIdLong())
		{
			String raw = event.getMessage().getContentRaw();
			// split anywhere there's one or more whitespace character
			String[] args = raw.split("\\s+");
			DiscordCommand.executeCommand(event, args);
		}
	}

	public void onGuildJoin(GuildJoinEvent event)
	{
		// if we don't have adequate permissions, leave the guild immediately so as not to waste our own resources.
	}

	public void onReady(ReadyEvent event)
	{
	}
}
