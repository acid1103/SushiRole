package org.abitoff.discord.sushirole.commands.discord;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;
import org.abitoff.discord.sushirole.utils.CommandUtils;
import org.abitoff.discord.sushirole.utils.LoggingUtils;
import org.abitoff.discord.sushirole.utils.Utils;

import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.requests.restaction.MessageAction;
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
			name = "list",
			aliases = {"listroles"})
	public static final class ListCommand extends DiscordCommand
	{
		@Override
		protected void executeCommand(Event event, Object...args)
		{
			GuildMessageReceivedEvent evnt = (GuildMessageReceivedEvent) event;
			List<Role> roles = evnt.getGuild().getRoles();

			String[] entries = new String[roles.size()];
			int i = 0;
			for (Role role: roles)
			{
				if (role.equals(evnt.getGuild().getPublicRole()))
					continue;
				String name = Utils.escapeMessageFormatting(role.getName());
				String entry = "\n__**" + name + ":**__\n" + role.getId();
				entries[i] = entry;
				i++;
			}

			Integer[] splits = new Integer[entries.length];
			i = 0;
			for (int j = 0, sum = 0; j < entries.length; j++)
			{
				if (entries[j] == null)
					continue;
				sum += entries[j].length();
				if (sum > Message.MAX_CONTENT_LENGTH)
				{
					splits[i++] = j;
					sum = entries[j].length();
				}
			}

			String[] contents = new String[i];
			i = 0;
			while (i < splits.length && splits[i] != null)
			{
				String[] content = new String[splits[i]];
				int startPos = i == 0 ? 0 : splits[i - 1];
				int length = splits[i] - startPos;
				System.arraycopy(entries, startPos, content, 0, length);
				contents[i] = Utils.join(content, "");
				i++;
			}

			TextChannel channel = evnt.getChannel();
			channel.sendMessage(contents[0]).queue(new ListMessageQueue(channel, contents, 1), t ->
			{
				throw new FatalException(t);
			});
		}

		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		private static final class ListMessageQueue implements Consumer<Message>
		{
			private final TextChannel channel;
			private final String[] contents;
			private final int i;

			private ListMessageQueue(TextChannel channel, String[] contents, int index)
			{
				this.channel = channel;
				this.contents = contents;
				this.i = index;
			}

			@Override
			public void accept(Message m)
			{
				if (i < contents.length)
					channel.sendMessage(contents[i]).queue(new ListMessageQueue(channel, contents, i + 1), t ->
					{
						throw new FatalException(t);
					});
			}
		}
	}

	@CommandLine.Command(
			name = "duplicate",
			aliases = {"dup"})
	public static final class DuplicateCommand extends DiscordCommand
	{
		@Parameters(
				index = "0",
				paramLabel = "<roles>",
				descriptionKey = "roles",
				arity = "1..*")
		private List<Long> roles;

		@Option(
				names = {"-n", "--names"},
				arity = "1..*")
		private List<Long> names;

		@Override
		protected void verifyParameters() throws ParameterException
		{
			if (roles == null || roles.size() == 0)
				throw new ParameterException("Must specify at least one role to duplicate!");
		}

		@Override
		protected void executeCommand(Event event, Object...args)
		{
			System.out.println(roles);
			System.out.println(names);
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
