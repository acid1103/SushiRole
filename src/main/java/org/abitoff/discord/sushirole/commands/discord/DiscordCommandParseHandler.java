package org.abitoff.discord.sushirole.commands.discord;

import java.util.List;

import org.abitoff.discord.sushirole.exceptions.ExceptionHandler;
import org.abitoff.discord.sushirole.exceptions.MessageHandlingException;

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
		if (parseResult == null)
		{
			// this should never ever happen, but just in case, we handle it.
			MessageHandlingException e = new MessageHandlingException(event.getMessage(), "Exception while executing this "
					+ "command! DiscordCommandParseHandler#handleParseResult(ParseResult) was passed a null ParseResult!");
			ExceptionHandler.reportThrowable(e);
			event.getChannel().sendMessage("<:error:410903384586059776> An unexpected error has occurred while executing that "
					+ "command. The developers have been notified and are working to resolve the error.");
			return null;
		}

		// get the last subcommand that was parsed.
		CommandLine command;
		{
			List<CommandLine> commands = parseResult.asCommandLineList();
			if (commands == null)
			{
				// this should also never ever happen, but we handle it just in case.
				MessageHandlingException e = new MessageHandlingException(event.getMessage(), "Exception while executing this "
						+ "command! In DiscordCommandParseHandler#handleParseResult(ParseResult), ParseResult#asCommandLineList()"
						+ " returned a null list!");
				ExceptionHandler.reportThrowable(e);
				event.getChannel()
						.sendMessage("<:error:410903384586059776> An unexpected error has occurred while executing that "
								+ "command. The developers have been notified and are working to resolve the error.");
				return null;
			}
			// get the last subcommand
			command = commands.get(commands.size() - 1);
		}

		try
		{
			// get the org.abitoff.discord.sushirole.commands.Command instance that was constructed by the parser.
			DiscordCommand dc = (DiscordCommand) command.getCommand();

			// if the help flag is present, ignore any other flags and display the help message for the command
			if (command.isUsageHelpRequested() || dc instanceof DiscordCommand.DefaultCommand)
			{
				Message helpMessage = DiscordCommand.getHelpMessage(command);
				event.getChannel().sendMessage(helpMessage).complete();
			} else
			{
				dc.execute(event);
			}
			return null;
		} catch (Throwable t)
		{
			// there's no need to send a response to the discord event here, because this exception will be caught and handled in
			// DiscordCommandExceptionHandler#handleExecutionException(ExecutionException, ParseResult).
			ExecutionException e = new ExecutionException(command, "Exception while executing " + parseResult.originalArgs());
			// init the cause here. functionally equivalent to passing the throwable in the constructor, except ExecutionException
			// only excepts Exceptions, rather than Throwables.
			e.initCause(t);
			throw e;
		}
	}
}
