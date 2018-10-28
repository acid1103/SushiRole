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
			// cleanup repeated spaces and whitespace. 
			raw = raw.replaceAll("\\s", " ").replaceAll(" +", " ");
			System.out.println(raw);
			String[] args = raw.split(" ");
			DiscordCommand.executeCommand(event, args);
			// event.getJDA().shutdown();
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
