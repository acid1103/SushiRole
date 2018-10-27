package org.abitoff.discord.sushirole.commands.discord;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.abitoff.discord.sushirole.SushiRole;
import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.commands.cli.CLICommand;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;
import org.abitoff.discord.sushirole.utils.CommandUtils;
import org.abitoff.discord.sushirole.utils.Utils;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public abstract class DiscordCommand extends Command
{
	private static final CommandLine cl = CommandUtils.generateCommands(DiscordCommand.class,
			ResourceBundle.getBundle("locale.discord", Locale.US));

	@CommandLine.Command(
			name = "run",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class RunCommand extends DiscordCommand
	{
		@Option(
				names = {"-d", "--dev"})
		private boolean dev = false;

		@Option(
				names = {"-s", "--shards"},
				paramLabel = "<total shards>")
		private int shards = 1;

		@Option(
				names = {"-v", "--verbose"})
		private boolean[] verbosity = new boolean[0];

		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		@Override
		protected void executeCommand() throws FatalException
		{
			System.out.println("Dev: " + dev);
			System.out.println("Shards: " + shards);
			System.out.println("Verbosity: " + Arrays.toString(verbosity));
		}

		@Override
		String since()
		{
			return "1.0";
		}
	}

	@CommandLine.Command(
			name = "decrypt",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class DecryptCommand extends DiscordCommand
	{
		@Parameters(
				index = "0",
				paramLabel = "<input file>",
				descriptionKey = "in")
		private File in;
		@Parameters(
				index = "1",
				paramLabel = "<output file>",
				descriptionKey = "out")
		private File out;

		boolean overwrite = false;

		@Override
		protected void verifyParameters() throws ParameterException
		{

		}

		@Override
		String since()
		{
			return null;
		}

		@Override
		protected void executeCommand() throws FatalException
		{
		}
	}

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	@CommandLine.Command(
			name = "",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class DefaultCommand extends DiscordCommand
	{
		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		@Override
		protected void executeCommand() throws FatalException
		{
		}

		@Override
		String since()
		{
			return "1.0";
		}
	}

	abstract String since();

	@Override
	public Class<DefaultCommand> defaultCommand()
	{
		return DefaultCommand.class;
	}

	/**
	 * TODO
	 * 
	 * @param args
	 * @return
	 */
	public static List<Object> executeCommand(GuildMessageReceivedEvent event, String...args)
	{
		return cl.parseWithHandlers(new DiscordCommandParseHandler(event), new DiscordCommandExceptionHandler(event), args);
	}

	public static Message generateHelpMessage(DiscordCommand dc, CommandLine command)
	{
		if (dc == null || dc instanceof DefaultCommand)
		{
			// return generic help message
		} else
		{
			System.out.println(command.getCommandName());
			EmbedBuilder builder = new EmbedBuilder();
			builder.setTitle(command.getCommandName());
			CommandLine.Help help = new CommandLine.Help(command.getCommand());
			UsageMessageSpec spec = command.getCommandSpec().usageMessage();
			String description = Utils.join(spec.description(), "\n");
			System.out.println(description);
			String synopsis = help.synopsis(0);
			System.out.println(synopsis);
			builder.setDescription("__" + description + "__\nUsage: " + synopsis);
			for (OptionSpec ospec: command.getCommandSpec().options())
			{
				String names = Utils.join(ospec.names(), ", ");
				System.out.println(names);
				String desc = "\t" + Utils.join(ospec.description(), "\n\t");
				System.out.println(desc);
				builder.addField(names, desc, false);
			}
			for (PositionalParamSpec pspec: command.getCommandSpec().positionalParameters())
			{
				String name = pspec.paramLabel();
				System.out.println(name);
				String desc = "\t" + Utils.join(pspec.description(), "\n\t");
				System.out.println(desc);;
				builder.addField(name, desc, false);
			}
			return new MessageBuilder().setEmbed(builder.build()).build();
		}
		return null;
	}

	public static Message generateVersionMessage(DiscordCommand dc, CommandLine command)
	{
		if (dc == null || dc instanceof DefaultCommand)
		{
			// return generic version message
		} else
		{
			// return version message including the version the given command was added
		}
		return null;
	}
}
