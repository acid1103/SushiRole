package org.abitoff.discord.sushirole.commands.discord;

import java.util.List;

import org.abitoff.discord.sushirole.exceptions.ExceptionHandler;
import org.abitoff.discord.sushirole.exceptions.MessageHandlingException;

import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IExceptionHandler2;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.OverwrittenOptionException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.UnmatchedArgumentException;

class DiscordCommandExceptionHandler implements IExceptionHandler2<Void>
{
	private final GuildMessageReceivedEvent event;

	public DiscordCommandExceptionHandler(GuildMessageReceivedEvent event)
	{
		this.event = event;
	}

	@Override
	public Void handleParseException(picocli.CommandLine.ParameterException ex, String[] args)
	{
		Message msg;
		String content;
		boolean appendHelp = true;
		// if (ex instanceof MaxValuesExceededException){/*never used internally in picocli*/} else
		if (ex instanceof MissingParameterException)
		{
			MissingParameterException e = (MissingParameterException) ex;
			List<ArgSpec> missing = e.getMissing();
			content = "<:error:410903384586059776> The following ";
			if (missing.size() > 1)
				content += "arguments were missing: ";
			else
				content += "argument was missing: ";
			for (int i = 0; i < missing.size(); i++)
			{
				ArgSpec spec = missing.get(i);

				if (spec.isOption())
					content += "`" + ((OptionSpec) spec).longestName() + "`";
				else
					content += "`" + spec.paramLabel() + "`";

				if (i != missing.size() - 1)
					content += ", ";
			}
		}
		// else if (ex instanceof MissingTypeConverterException){\*caused by misconfigured source. should never happen in
		// published bot*\}
		else if (ex instanceof OverwrittenOptionException)
		{
			// picocli provides no nice way of retrieving the argument which was overwritten, so we have to hard code some of
			// this... the desired functionality will be added in 3.8. (see https://github.com/remkop/picocli/pull/532)
			OverwrittenOptionException e = (OverwrittenOptionException) ex;
			String exmsg = e.getMessage();
			exmsg = exmsg.substring(8);
			exmsg = exmsg.substring(0, exmsg.indexOf(' ') - 1);
			content = "<:error:410903384586059776> The `" + e.getCommandLine().getCommandName() + "` command only takes one `"
					+ exmsg + "` option!";
		} else if (ex instanceof UnmatchedArgumentException)
		{
			UnmatchedArgumentException e = (UnmatchedArgumentException) ex;

			if (e.getCommandLine().getCommand() instanceof DiscordCommand.DefaultCommand)
			{
				content = "<:error:410903384586059776> Unrecognized command: `" + e.getUnmatched().get(0) + "`";
			} else
			{
				content = "<:error:410903384586059776> The following argument(s) are unrecognized: ";
				List<String> unmatched = e.getUnmatched();
				for (int i = 0; i < unmatched.size(); i++)
				{
					content += "`" + unmatched.get(i) + "`";
					if (i != unmatched.size() - 1)
						content += ", ";
				}
			}
		} else
		{
			ExceptionHandler.reportThrowable(new MessageHandlingException(event.getMessage(), ex));
			content = "<:error:410903384586059776> An unexpected error has occurred while processing that command. The developers "
					+ "have been notified and are working to resolve the error.";
		}

		Message help = DiscordCommand.getHelpMessage(ex.getCommandLine());
		MessageBuilder builder = new MessageBuilder();
		builder.setContent(content);
		if (appendHelp)
			builder.setEmbed(help.getEmbeds().get(0));
		msg = builder.build();
		event.getChannel().sendMessage(msg).queue();
		return null;
	}

	@Override
	public Void handleExecutionException(ExecutionException ex, ParseResult parseResult)
	{
		ExceptionHandler.reportThrowable(new MessageHandlingException(event.getMessage(), ex));
		event.getChannel().sendMessage("<:error:410903384586059776> An unexpected error has occurred while executing that "
				+ "command. The developers have been notified and are working to resolve the error.").queue();
		return null;
	}
}
