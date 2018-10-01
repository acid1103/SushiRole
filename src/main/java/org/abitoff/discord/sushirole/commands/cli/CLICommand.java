package org.abitoff.discord.sushirole.commands.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.security.auth.login.LoginException;

import org.abitoff.discord.sushirole.SushiRole;
import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.config.SushiRoleConfig;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.BotConfig;
import org.abitoff.discord.sushirole.events.GlobalEventListener;
import org.abitoff.discord.sushirole.exceptions.ExceptionHandler;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;
import org.abitoff.discord.sushirole.utils.CommandUtils;
import org.abitoff.discord.sushirole.utils.LoggingUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ch.qos.logback.classic.Level;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Option;
import picocli.CommandLine.RunLast;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public abstract class CLICommand extends Command
{
	private static final CommandLine cl = CommandUtils.generateCommands(CLICommand.class,
			ResourceBundle.getBundle("locale.cli", Locale.US));

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	@CommandLine.Command(
			name = "run",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class RunCommand extends CLICommand
	{
		@Option(
				names = {"-d", "--dev"},
				defaultValue = "false")
		private boolean dev;

		@Option(
				names = {"-s", "--shards"},
				paramLabel = "Total Shards")
		private int shards = 1;

		@Option(
				names = {"-v", "--verbose"})
		private boolean[] verbosity = new boolean[0];

		private int verbosityValue = 2;

		@Override
		protected void verifyParameters() throws ParameterException
		{
			// TODO
			if (shards < 1)
				throw new ParameterException("--shards must be at least 1!");

			for (boolean b: verbosity)
				verbosityValue += b ? 1 : -1;
			verbosityValue = Integer.min(Integer.max(verbosityValue, 0), 6);
		}

		// OFF
		// ERROR
		// WARN
		// INFO
		// DEBUG
		// TRACE
		// ALL

		@Override
		protected void executeCommand() throws FatalException
		{
			// TODO
			Level[] verbosityLevels = new Level[] {Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE,
					Level.ALL};
			Level verbosityLevel = verbosityLevels[verbosityValue];

			LoggingUtils.infof("Setting log verbosity to %s.", verbosityLevel.toString());
			SushiRole.LOG.setLevel(verbosityLevel);

			File keys = new File("./sushiroleconfig/config");

			GsonBuilder gsonBuilder = new GsonBuilder();
			Gson gson = gsonBuilder.create();

			SushiRoleConfig config;
			try (BufferedReader br = new BufferedReader(new FileReader(keys)))
			{
				config = gson.fromJson(br, SushiRoleConfig.class);
			} catch (IOException e)
			{
				throw new FatalException("Exception while reading config file!", e);
			}

			BotConfig botAccount = this.dev ? config.discord_bot_dev : config.discord_bot;
			ExceptionHandler.initiate(config.pastebin, config.discord_error_reporting);

			try
			{
				JDABuilder jdaBuilder = new JDABuilder(AccountType.BOT).setAudioEnabled(false).setToken(botAccount.token)
						.addEventListener(GlobalEventListener.listener);
				LoggingUtils.infof("Building %d %s.", shards, shards > 1 ? "shards" : "shard");
				for (int i = 0; i < shards; i++)
				{
					jdaBuilder = jdaBuilder.useSharding(i, shards);
					jdaBuilder.build();
				}
			} catch (LoginException e)
			{
				throw new FatalException("Exception while starting bot!", e);
			}
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
	public static final class DefaultCommand extends CLICommand
	{
		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		@Override
		protected void executeCommand() throws FatalException
		{
			cl.usage(System.out);
		}
	}

	@Override
	public final Class<DefaultCommand> defaultCommand()
	{
		return DefaultCommand.class;
	}

	/**
	 * TODO
	 * 
	 * @param args
	 */
	public static void executeCommand(String...args)
	{
		cl.parseWithHandler(new RunLast().useOut(System.out).useAnsi(Help.Ansi.AUTO), args);
	}
}
