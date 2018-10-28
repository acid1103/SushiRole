package org.abitoff.discord.sushirole.commands.discord;

import java.util.List;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import picocli.CommandLine;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IParseResultHandler2;
import picocli.CommandLine.ParseResult;

class DiscordCommandParseHandler implements IParseResultHandler2<Void>
{
	private final GuildMessageReceivedEvent event;

	public DiscordCommandParseHandler(GuildMessageReceivedEvent event)
	{
		this.event = event;
	}

	@Override
	public Void handleParseResult(ParseResult parseResult) throws ExecutionException
	{
		CommandLine command;
		{
			List<CommandLine> commands = parseResult.asCommandLineList();
			command = commands.get(commands.size() - 1);
		}
		DiscordCommand dc = (DiscordCommand) command.getCommand();

		if (dc.getHelpFlag())
		{
			Message helpMessage = DiscordCommand.getHelpMessage(command);
			event.getChannel().sendMessage(helpMessage).complete();
		} else
		{
			dc.execute(event);
		}
		return null;
	}
}
