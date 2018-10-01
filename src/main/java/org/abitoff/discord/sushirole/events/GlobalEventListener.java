package org.abitoff.discord.sushirole.events;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
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
		}
	}

	public void onGuildJoin(GuildJoinEvent event)
	{
		// if we don't have adequate permissions, leave the guild immediately so as not to waste our own resources.
	}

	public void onReady(ReadyEvent event)
	{
		event.getJDA().getTextChannelById(490425104787046400L).sendMessage(new MessageBuilder()
				.setEmbed(new EmbedBuilder().setTitle("__Guild__\\ __Channel__\\ __\\*Special User\\*__").build()).build())
				.queue((Message m) ->
				{
					System.out.println("Success");
					event.getJDA().shutdown();
				}, (Throwable e) ->
				{
					e.printStackTrace();
					event.getJDA().shutdown();
				});
	}
}
