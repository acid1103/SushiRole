package org.abitoff.discord.sushirole.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.security.auth.login.LoginException;

import org.abitoff.discord.sushirole.SushiRole;
import org.abitoff.discord.sushirole.config.SushiRoleConfig;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.BotConfig;
import org.abitoff.discord.sushirole.events.GlobalEventListener;
import org.abitoff.discord.sushirole.exceptions.ExceptionHandler;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.utils.JCommanderUtils;
import org.abitoff.discord.sushirole.utils.JCommanderUtils.JCommanderPackage;
import org.abitoff.discord.sushirole.utils.LoggingUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;

public abstract class CLICommand
{
	private static final JCommanderPackage<CLICommand> parsePackage = JCommanderUtils
			.generateJCommanderPackage(CLICommand.class);

	@Parameters(commandDescription = "Runs the bot",commandNames = { "run" })
	public static class RunCommand extends CLICommand
	{
		private static final boolean defaultDev = false;
		private static final List<Integer> defaultShard = new ArrayList<Integer>(Arrays.asList(0, 1));
		private static final int defaultVerbosity = 3;

		@Parameter(names = { "-dev", "--developer_mode" },description = "Boolean value used to indicate which bot to "
				+ "launch. True launches the dev mode bot, and false launches the public bot. Usage: '-dev flag' "
				+ "Example: '-dev false'",arity = 1)
		private boolean dev = defaultDev;

		@Parameter(names = { "-shard" },description = "Two integers, used to determine the shard ID for this instance "
				+ "of the bot. Usage: '-shard shardId totalShards' Example: '-shard 5 10' For further information, see "
				+ "https://discordapp.com/developers/docs/topics/gateway#sharding",arity = 2)
		private List<Integer> shard = defaultShard;

		@Parameter(names = { "-v", "-verbose" },description = "An integer describing the verbosity of the messages to "
				+ "log. Values range from 0-8, with 0 meaning that no messages should be logged, and 8 meaning that all "
				+ "should be logged. More specifically, 0 turns logging off, 1 corresponds to severe logs, 2 corresponds "
				+ "to warning logs and up, 3 corresponds to info logs and up, 4 corresponds to config logs and up, 5-7 "
				+ "correspond to logs of respectively increasing granularity and up, and 8 corresponds to all logs.")
		private int verbosity = defaultVerbosity;

		void run()
		{
			Level[] verbosityLevels = new Level[] { Level.OFF, Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG,
					Level.FINE, Level.FINER, Level.FINEST, Level.ALL };
			Level verbosityLevel = verbosityLevels[verbosity];

			LoggingUtils.infof("Setting log verbosity to %s.", verbosityLevel.getName());
			SushiRole.LOG.setLevel(verbosityLevel);

			File keys = new File("./sushiroleconfig/config");

			GsonBuilder builder = new GsonBuilder();
			Gson gson = builder.create();

			SushiRoleConfig config;
			try (BufferedReader br = new BufferedReader(new FileReader(keys)))
			{
				config = gson.fromJson(br, SushiRoleConfig.class);
			} catch (IOException e)
			{
				throw new FatalException("Exception while reading config file!", e);
			}

			BotConfig botAccount = this.dev ? config.discord_bot_dev : config.discord_bot;
			ExceptionHandler.initiate(config.pastebin);

			try
			{
				new JDABuilder(AccountType.BOT).setToken(botAccount.token)
						.addEventListener(GlobalEventListener.listener).useSharding(shard.get(0), shard.get(1)).build()
						.awaitReady();
			} catch (LoginException | InterruptedException e)
			{
				throw new FatalException("Exception while starting bot!", e);
			}
		}

		void validateParameters()
		{
			if (0 > verbosity || verbosity > 8)
				throw new FatalException(
						String.format("Verbose value must be in the range [0,8]! (Got %d)", verbosity));
			if (0 > shard.get(0) || shard.get(0) >= shard.get(1))
				throw new FatalException(String.format(
						"shardId must be greater than 0, and totalShards must be greater than shardId! "
								+ "(Got: shardId:%d, totalShards:%d) "
								+ "See https://discordapp.com/developers/docs/topics/gateway#sharding",
						shard.get(0), shard.get(1)));
		}
	}

	abstract void run();

	abstract void validateParameters() throws FatalException;

	public final void runCommand() throws FatalException
	{
		validateParameters();
		run();
	}

	public static final CLICommand parseCommand(String...args) throws FatalException
	{
		Map<String,CLICommand> commands = parsePackage.commands;
		JCommander jc = parsePackage.commandParser;
		CLICommand cmd;
		String cmdString = null;
		try
		{
			jc.parseWithoutValidation(args);
			cmdString = jc.getParsedCommand();
			if (cmdString == null || (cmd = commands.get(cmdString)) == null)
			{
				throw new UnknownCommandException(
						"Unrecognized command. Valid commands: " + commands.keySet().toString());
			}
		} catch (UnknownCommandException | ParameterException e)
		{
			StringBuilder sb = new StringBuilder();
			// displays nicely in discord code blocks on monitors with resolutions as low as 720p, assuming no strange edge cases
			jc.setColumnSize(70);
			if (cmdString == null || e instanceof UnknownCommandException)
				jc.usage(sb);
			else
				jc.usage(cmdString, sb);
			System.err.println(sb.toString());
			throw new FatalException("Error while parsing command", e);
		}
		return cmd;
	}

	/**
	 * @author Steven Fontaine
	 */
	private static final class UnknownCommandException extends Exception
	{
		private static final long serialVersionUID = 8816226238588379400L;

		private UnknownCommandException()
		{
			super();
		}

		private UnknownCommandException(String s)
		{
			super(s);
		}
	}
}
