package org.abitoff.discord.sushirole.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.abitoff.discord.sushirole.SushiRole;
import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.exceptions.FatalException;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * A utility class for interacting with {@link picocli.CommandLine picocli} commands.
 * 
 * @author Steven Fontaine
 *
 */
public class CommandUtils
{
	private CommandUtils()
	{
	}

	/**
	 * Creates a {@link CommandLine} and adds all the sub-commands contained in {@code superClass}.
	 * 
	 * @param superClass
	 *            The class containing all the commands
	 * @param bundle
	 *            The {@link ResourceBundle} containing the default localization for the {@link CommandLine}
	 * @param <T>
	 *            The type of {@code superClass}
	 * @return the {@link CommandLine} that was created
	 */
	public static final <T extends Command> CommandLine generateCommands(Class<T> superClass, ResourceBundle bundle)
	{
		// instantiate the commands map we're going to fill and return
		Map<String,T> commands = new HashMap<String,T>();
		T defaultCommand = null;
		for (Class<?> clazz: superClass.getClasses())
		{
			String className = clazz.getName();
			// check if the class is defined to be a command
			if (!clazz.isAnnotationPresent(CommandLine.Command.class))
			{
				LoggingUtils.infof("%s does not have the %s annotation. Skipping.", className,
						CommandLine.Command.class.getName());
				continue;
			}

			// ensure we can cast the class to the command super class
			if (!superClass.isAssignableFrom(clazz))
			{
				LoggingUtils.warnf("%s does not extend/implement %s! Skipping.", className, superClass.getName());
				continue;
			}

			// try to get the default constructor, catching errors where appropriate
			Constructor<?> constructor;
			try
			{
				constructor = clazz.getConstructor();
			} catch (NoSuchMethodException e)
			{
				LoggingUtils.warnf("%s does not contain a default constructor! Skipping.", className);
				continue;
			} catch (SecurityException e)
			{
				LoggingUtils.warnf("Access to the default constructor in %s is not allowed! Skipping.", className);
				continue;
			}

			// get the command name
			String commandName = clazz.getAnnotation(CommandLine.Command.class).name();

			// construct the parameter class, catching appropriate errors. some of the errors are redundant because we checked
			// them above, but we have to catch them again anyway.
			T command;
			try
			{
				// use a temp variable here so we can suppress unchecked on it, rather than the entire method
				@SuppressWarnings("unchecked")
				T temp = (T) constructor.newInstance();
				command = temp;
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
			{
				LoggingUtils.warnf("Unable to instantiate %s! Skipping.", className);
				continue;
			}

			// we don't need to add the command if it's the default command
			if (command.defaultCommand() != clazz)
			{
				// add the command parameters class to our map so we can easily get it later
				commands.put(commandName, command);
				LoggingUtils.infof("Added the command \"%s\".", commandName);
			} else
			{
				// set the default command
				defaultCommand = command;
				LoggingUtils.infof("Default command found: %s", className);
			}
		}

		// if we didn't find the default command, we can't continue
		if (defaultCommand == null)
			throw new FatalException(String.format("Couldn't find the default command for %s!", superClass.getName()));

		// create the CommandLine instance, using the default command
		CommandLine cl = new CommandLine(defaultCommand);

		// set the resource bundle
		cl.setResourceBundle(bundle);

		// add all the sub-commands
		for (Entry<String,T> e: commands.entrySet())
		{
			cl.addSubcommand(e.getKey(), e.getValue());
		}

		return cl;
	}

	public static final ConcurrentHashMap<String,MessageBuilder> generateHelpMessages(CommandLine defaultCommand)
	{
		// it's very very very important that this is concurrent, because many threads will potentially be accessing this map
		// simultaneously.
		ConcurrentHashMap<String,MessageBuilder> messages = new ConcurrentHashMap<String,MessageBuilder>();
		EmbedBuilder builder = new EmbedBuilder();
		builder.setColor(0x50b0ff);
		builder.setTitle("SushiRole");
		builder.setDescription("The bot dedicated to sophisticated Discord role management and automation. Created, developed, "
				+ "and maintained by Steven Fontaine (acid#0001) on [github](https://github.com/ABitOff/SushiRole).");
		for (Entry<String,CommandLine> entry: defaultCommand.getSubcommands().entrySet())
		{
			CommandLine command = entry.getValue();
			String description = Utils.join(command.getCommandSpec().usageMessage().description(), "\n");
			String synopsis = new CommandLine.Help(command.getCommand()).synopsis(0);
			builder.addField(entry.getKey(), "__" + description + "__\nUsage: " + synopsis, false);
			messages.put(command.getCommandName(), generateCommandHelpMessage(command));
		}
		builder.setFooter(SushiRole.VERSION, null);
		messages.put(defaultCommand.getCommandName(), new MessageBuilder().setEmbed(builder.build()));
		return messages;
	}

	private static final MessageBuilder generateCommandHelpMessage(CommandLine command)
	{
		EmbedBuilder builder = new EmbedBuilder();
		builder.setColor(0x50b0ff);
		builder.setTitle(command.getCommandName());
		String description = Utils.join(command.getCommandSpec().usageMessage().description(), "\n");
		String synopsis = new CommandLine.Help(command.getCommand()).synopsis(0);
		builder.setDescription("__" + description + "__\nUsage: " + synopsis);
		for (OptionSpec spec: command.getCommandSpec().options())
		{
			String names = Utils.join(spec.names(), ", ");
			String desc = Utils.join(spec.description(), "\n");
			builder.addField(names, desc, false);
		}
		for (PositionalParamSpec spec: command.getCommandSpec().positionalParameters())
		{
			String name = spec.paramLabel();
			String desc = Utils.join(spec.description(), "\n");
			builder.addField(name, desc, false);
		}
		return new MessageBuilder().setEmbed(builder.build());
	}
}
