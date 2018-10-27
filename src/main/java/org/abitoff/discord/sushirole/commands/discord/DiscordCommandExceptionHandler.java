package org.abitoff.discord.sushirole.commands.discord;

import java.util.List;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IExceptionHandler2;
import picocli.CommandLine.ParseResult;

class DiscordCommandExceptionHandler implements IExceptionHandler2<List<Object>>
{
	private final GuildMessageReceivedEvent event;
	
	public DiscordCommandExceptionHandler(GuildMessageReceivedEvent event)
	{
		this.event = event;
	}

	@Override
	public List<Object> handleParseException(picocli.CommandLine.ParameterException ex, String[] args)
	{
		return null;
	}

	@Override
	public List<Object> handleExecutionException(ExecutionException ex, ParseResult parseResult)
	{
		return null;
	}
}
