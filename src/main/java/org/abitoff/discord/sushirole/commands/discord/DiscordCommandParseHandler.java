package org.abitoff.discord.sushirole.commands.discord;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import picocli.CommandLine;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IParseResultHandler2;
import picocli.CommandLine.ParseResult;

class DiscordCommandParseHandler implements IParseResultHandler2<List<Object>>
{
	private final GuildMessageReceivedEvent event;

	public DiscordCommandParseHandler(GuildMessageReceivedEvent event)
	{
		this.event = event;
	}

	@Override
	public List<Object> handleParseResult(ParseResult parseResult) throws ExecutionException
	{
		CommandLine command;
		{
			List<CommandLine> commands = parseResult.asCommandLineList();
			command = commands.get(commands.size() - 1);
		}
		DiscordCommand dc = (DiscordCommand) command.getCommand();

		if (command.isUsageHelpRequested())
		{
			Message helpMessage = DiscordCommand.generateHelpMessage(dc, command);
			ArrayList<Object> ret = new ArrayList<Object>();
			ret.add(helpMessage);
			return ret;
		}
		if (command.isVersionHelpRequested())
		{
			Message versionMessage = DiscordCommand.generateVersionMessage(dc, command);
		}
		return null;
	}
}
