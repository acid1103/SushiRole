package org.abitoff.discord.sushirole.commands.discord;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;
import org.abitoff.discord.sushirole.utils.CommandUtils;
import org.abitoff.discord.sushirole.utils.LoggingUtils;
import org.abitoff.discord.sushirole.utils.Utils;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Role;
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
			name = "list",
			aliases = {"listroles"})
	public static final class ListCommand extends DiscordCommand
	{
		@Override
		protected void executeCommand(Event event, Object...args)
		{
			GuildMessageReceivedEvent evnt = (GuildMessageReceivedEvent) event;
			List<Role> roles = evnt.getGuild().getRoles();
			String[] contentString = new String[roles.size()];
			int i = 0;
			for (Role role: roles)
			{
				if (role.equals(evnt.getGuild().getPublicRole()))
					continue;
				String name = Utils.escapeMessageFormatting(role.getName());
				String entry = "\n__**" + name + ":**__\n" + role.getId();
				contentString[i] = entry;
				i++;
			}
			String message = Utils.join(contentString, "");
			evnt.getChannel().sendMessage(message).queue();
		}

		@Override
		protected void verifyParameters() throws ParameterException
		{
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
