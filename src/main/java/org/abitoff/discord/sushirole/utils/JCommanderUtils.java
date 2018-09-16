package org.abitoff.discord.sushirole.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.JCommander.Builder;

/**
 * A utility class for constructing JCommander command parsers given a class containing command classes annotated with
 * {@link Parameters}
 * 
 * @author Steven Fontaine
 *
 */
public class JCommanderUtils
{
	/**
	 * TODO
	 * 
	 * @param clazz
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static final <T> JCommanderPackage<T> generateJCommanderPackage(Class<T> clazz)
	{
		// get a builder for the JCommander we're going to build and return
		Builder commandBuilder = JCommander.newBuilder();
		// instantiate the commands map we're going to fill and return
		Map<String,T> commands = new HashMap<String,T>();
		for(Class<?> c: clazz.getClasses())
		{
			// check if the class is defined to be a command
			if (!c.isAnnotationPresent(Parameters.class))
			{
				LoggingUtils.infof("%s does not have the %s annotation. Skipping.", c.getName(),
						Parameters.class.getName());
				continue;
			}

			// ensure we can cast the class to the command super class
			if (!clazz.isAssignableFrom(c))
			{
				LoggingUtils.warningf("%s does not extend/implement %s! Skipping.", c.getName(), clazz.getName());
				continue;
			}

			// try to get the default constructor, catching errors where appropriate
			Constructor<?> constructor;
			try
			{
				constructor = c.getConstructor();
			} catch (NoSuchMethodException e)
			{
				LoggingUtils.warningf("%s does not contain a default constructor! Skipping.", c.getName());
				continue;
			} catch (SecurityException e)
			{
				LoggingUtils.warningf("Access to the default constructor in %s is not allowed! Skipping.", c.getName());
				continue;
			}

			// get the command parameters for this class
			Parameters p = c.getAnnotation(Parameters.class);
			// get the command name and aliases
			String[] commandNames = p.commandNames();
			// ensure that the command has at least one name
			assert commandNames != null && commandNames.length != 0;
			// set the main name to the first name in the list
			String name = commandNames[0];
			// construct a new array containing the remaining aliases
			String[] aliases = new String[commandNames.length - 1];
			if (commandNames.length > 1)
				System.arraycopy(commandNames, 1, aliases, 0, aliases.length);

			// construct the parameter class, catching appropriate errors. some of the errors are redundant because we checked
			// them above, but we have to catch them again anyway.
			T commandParams;
			try
			{
				commandParams = (T) constructor.newInstance();
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e)
			{
				LoggingUtils.warningf("Unable to instantiate %s! Skipping.", c.getName());
				continue;
			}
			// add the command parameters class to our map so we can easily get it later
			commands.put(name, commandParams);
			// add the command to JCommander
			commandBuilder.addCommand(name, commandParams, aliases);
			LoggingUtils.infof("Added the CLI command \"%s\" with the aliases %s.", name, Arrays.toString(aliases));
		}

		// build the parser
		JCommander commandParser = commandBuilder.build();

		return new JCommanderPackage<T>(commands, commandParser);
	}

	/**
	 * @author Steven Fontaine
	 *
	 * @param <T>
	 */
	public static final class JCommanderPackage<T>
	{
		public final Map<String,T> commands;
		public final JCommander commandParser;

		private JCommanderPackage(Map<String,T> commands, JCommander commandParser)
		{
			this.commands = commands;
			this.commandParser = commandParser;
		}
	}
}
