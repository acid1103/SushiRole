package org.abitoff.discord.sushirole.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.exceptions.FatalException;

import picocli.CommandLine;

/**
 * A utility class for interacting with {@link picocli.CommandLine picocli} commands.
 * 
 * @author Steven Fontaine
 *
 */
public class CommandUtils
{
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
				LoggingUtils.infof("Added the CLI command \"%s\".", commandName);
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
}
