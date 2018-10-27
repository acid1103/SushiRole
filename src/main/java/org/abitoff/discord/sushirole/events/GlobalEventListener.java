package org.abitoff.discord.sushirole.events;

import java.util.List;

import org.abitoff.discord.sushirole.commands.discord.DiscordCommand;
import org.abitoff.discord.sushirole.exceptions.ExceptionHandler;

import net.dv8tion.jda.core.entities.Message;
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
			String[] args = event.getMessage().getContentRaw().split(" ");
			DiscordCommand.executeCommand(event, args);
		}
	}

	public void onGuildJoin(GuildJoinEvent event)
	{
		// if we don't have adequate permissions, leave the guild immediately so as not to waste our own resources.
	}

	public void onReady(ReadyEvent event)
	{
		List<Object> obj = DiscordCommand.executeCommand(null, "decrypt -h".split(" "));
		if (obj != null && obj.get(0) != null && obj.get(0) instanceof Message)
		{
			event.getJDA().getTextChannelById(0L).sendMessage((Message) obj.get(0)).complete();
		}
		event.getJDA().shutdown();
	}
}
