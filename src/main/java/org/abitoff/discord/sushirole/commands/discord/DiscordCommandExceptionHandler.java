package org.abitoff.discord.sushirole.commands.discord;

import java.util.List;

import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import picocli.CommandLine;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IExceptionHandler2;
import picocli.CommandLine.MaxValuesExceededException;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.MissingTypeConverterException;
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
		Message msg = null;
		// if (ex instanceof MaxValuesExceededException){/*never used internally in picocli*/} else
		if (ex instanceof MissingParameterException)
		{
			MissingParameterException e = (MissingParameterException) ex;
			List<ArgSpec> missing = e.getMissing();
			String content = "<:error:410903384586059776> The following ";
			if (missing.size() > 1)
				content += "arguments were missing: ";
			else
				content += "argument was missing: ";
			for (int i = 0; i < missing.size(); i++)
			{
				ArgSpec spec = missing.get(i);
				if (spec.isOption())
				{
					OptionSpec ospec = (OptionSpec) spec;
					String name = "";
					for (String s: ospec.names())
						if (s.length() > name.length())
							name = s;
					content += name;
				} else
					content += spec.paramLabel();
				if (i != missing.size() - 1)
					content += ", ";
			}

			MessageBuilder builder = DiscordCommand.getHelpMessage(ex.getCommandLine());
			builder.setContent(content);
			msg = builder.build();
		}
		// else if (ex instanceof MissingTypeConverterException){\*caused by misconfigured source. should never happen in
		// published bot*\}
		else if (ex instanceof OverwrittenOptionException)
		{

		} else if (ex instanceof UnmatchedArgumentException)
		{

		} else
		{

		}
		event.getChannel().sendMessage(msg).queue();
		ex.printStackTrace();
		return null;
	}

	@Override
	public Void handleExecutionException(ExecutionException ex, ParseResult parseResult)
	{
		ex.printStackTrace();
		return null;
	}
}
