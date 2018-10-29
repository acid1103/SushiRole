package org.abitoff.discord.sushirole.commands.discord;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;
import org.abitoff.discord.sushirole.utils.CommandUtils;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public abstract class DiscordCommand extends Command
{
	private static final CommandLine cl = CommandUtils.generateCommands(DiscordCommand.class,
			ResourceBundle.getBundle("locale.discord", Locale.US));
	static
	{
		cl.setExpandAtFiles(false);
	}
	private static final ConcurrentHashMap<String,Message> helpMessages = CommandUtils.generateHelpMessages(cl);

	@CommandLine.Command(
			name = "test")
	public static final class TestCommand extends DiscordCommand
	{
		@Option(
				names = {"-h", "--help"},
				usageHelp = true)
		private boolean helpFlag = false;

		@Option(
				names = {"-f", "--file"},
				description = "File(s) to process.")
		private boolean inputFiles;

		@Option(
				names = {"-F", "--file2"},
				arity = "2",
				description = "File(s) to process.",
				required = false)
		private File inputFiles2;

		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		@Override
		protected void executeCommand(Event event, Object...args)
		{
			try
			{
				int tab = 0;
				Field[] fields = getClass().getDeclaredFields();
				String[] names = new String[fields.length];
				String[] values = new String[fields.length];
				for (int i = 0; i < fields.length; i++)
				{
					Field f = fields[i];
					names[i] = f.getName();
					if (names[i].length() > tab)
						tab = names[i].length();
					Object obj = f.get(this);
					if (obj == null)
					{
						values[i] = "null";
					} else if (obj.getClass().isArray())
					{
						Object[] objAr = new Object[Array.getLength(obj)];
						for (int j = 0; j < objAr.length; j++)
							objAr[j] = Array.get(obj, j);
						values[i] = Arrays.deepToString(objAr);
					} else
					{
						values[i] = obj.toString();
					}
				}
				for (int i = 0; i < names.length; i++)
				{
					int nameLength = names[i].length();
					int toTab = tab - nameLength;
					String tabStr = "";
					for (int j = 0; j < toTab; j++)
						tabStr += " ";
					System.out.println(names[i] + ": " + tabStr + values[i]);
				}
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	@CommandLine.Command(
			name = "help")
	public static final class HelpCommand extends DiscordCommand
	{
		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		@Override
		protected void executeCommand(Event event, Object...args) throws FatalException
		{

		}
	}

	@CommandLine.Command(
			name = "run")
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

		@Option(
				names = {"-h", "--help"},
				usageHelp = true)
		private boolean helpFlag = false;

		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		@Override
		protected void executeCommand(Event event, Object...args) throws FatalException
		{
			if (event instanceof GuildMessageReceivedEvent)
			{
				GuildMessageReceivedEvent evnt = (GuildMessageReceivedEvent) event;
				evnt.getChannel()
						.sendMessage("Dev: " + dev + "\nShards: " + shards + "\nVerbosity: " + Arrays.toString(verbosity))
						.queue();
			} else
			{
				System.out.println("Dev: " + dev);
				System.out.println("Shards: " + shards);
				System.out.println("Verbosity: " + Arrays.toString(verbosity));
			}
		}
	}

	@CommandLine.Command(
			name = "decrypt")
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

		@Option(
				names = {"-h", "--help"},
				usageHelp = true)
		private boolean helpFlag = false;

		@Override
		protected void verifyParameters() throws ParameterException
		{

		}

		@Override
		protected void executeCommand(Event event, Object...args) throws FatalException
		{
		}
	}

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	@CommandLine.Command(
			name = "")
	public static final class DefaultCommand extends DiscordCommand
	{
		@Option(
				names = {"-h", "--help"},
				usageHelp = true)
		private boolean helpFlag = false;

		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		@Override
		protected void executeCommand(Event event, Object...args) throws FatalException
		{
		}
	}

	@Override
	public Class<DefaultCommand> defaultCommand()
	{
		return DefaultCommand.class;
	}

	protected void executeCommand(Object...args) throws FatalException
	{
		assert (args != null && args.length > 0 && args[0] instanceof Event);
		if (args.length > 1)
		{
			Object[] argz = new Object[args.length - 1];
			System.arraycopy(args, 1, argz, 0, argz.length);
			executeCommand((Event) args[0], argz);
		} else
		{
			executeCommand((Event) args[0]);
		}

	}

	protected abstract void executeCommand(Event event, Object...args);

	/**
	 * TODO
	 * 
	 * @param args
	 * @return
	 */
	public static void executeCommand(GuildMessageReceivedEvent event, String...args)
	{
		cl.parseWithHandlers(new DiscordCommandParseHandler(event), new DiscordCommandExceptionHandler(event), args);
	}

	public static Message getHelpMessage(CommandLine command)
	{
		return helpMessages.get(command.getCommandName());
	}
}
