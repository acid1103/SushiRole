package org.abitoff.discord.sushirole.commands.cli;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;

import javax.security.auth.login.LoginException;

import org.abitoff.discord.sushirole.SushiRole;
import org.abitoff.discord.sushirole.commands.ICommand;
import org.abitoff.discord.sushirole.config.SushiRoleConfig;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.BotConfig;
import org.abitoff.discord.sushirole.events.GlobalEventListener;
import org.abitoff.discord.sushirole.exceptions.ExceptionHandler;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.FatalException.ParameterException;
import org.abitoff.discord.sushirole.utils.CommandUtils;
import org.abitoff.discord.sushirole.utils.LoggingUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Option;
import picocli.CommandLine.RunLast;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public abstract class CLICommand implements Runnable,ICommand
{
	/**
	 * TODO
	 * 
	 * @throws ParameterException
	 */
	abstract void verifyParameters() throws ParameterException;

	/**
	 * TODO
	 * 
	 * @throws FatalException
	 */
	abstract void executeCommand() throws FatalException;

	@Override
	public final void run()
	{
		verifyParameters();
		executeCommand();
	}

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	@Command(
			name = "run",
			description = "Runs the bot with the given options",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class RunCommand extends CLICommand
	{
		@Option(
				names = { "-d", "--dev" },
				defaultValue = "false",
				description = "Boolean flag used to indicate which bot to launch. With the flag present, the dev mode bot lanches. "
						+ "Otherwise, the public bot launches.")
		private boolean dev;

		@Option(
				names = { "-s", "--shardId" },
				paramLabel = "Shard ID",
				description = "The shard id of this instance of the bot. Used in conjuntion with --totalShards to determine the "
						+ "shard this bot should operate. See https://discordapp.com/developers/docs/topics/gateway#sharding")
		private Integer shardId = null;

		@Option(
				names = { "-S", "--totalShards" },
				paramLabel = "Total Shards",
				description = "The total number of shards running under this bot. Used in conjuntion with --shardId to determine "
						+ "the shard this bot should operate. See https://discordapp.com/developers/docs/topics/gateway#sharding")
		private Integer totalShards = null;

		@Option(
				names = { "-v", "--verbose" },
				description = "Shows increased information about the operations of the bot. Stack multiples (i.e. \"-vvv\", up to "
						+ "a maximum of 6) for increased verbosity. With 7 flags (\"-vvvvvvv\") or more, logging is turned off.")
		private boolean[] verbosity = new boolean[0];

		@Override
		void verifyParameters() throws ParameterException
		{
			// TODO
			String id = "--shardId";
			String tot = "--totalShards";
			boolean sid = shardId == null;
			boolean tots = totalShards == null;
			if (sid && tots)
			{
				shardId = 0;
				totalShards = 1;
			} else if (sid ^ tots)
			{
				tpe("When %s is declared, %s must also be declared!", sid ? tot : id, sid ? id : tot);
			} else
			{
				if (shardId < 0)
					tpe("%s must not be negative!", id);
				if (totalShards < shardId)
					tpe("%s must be greater than %s!", tot, id);
			}

			if (verbosity.length > 7)
				verbosity = new boolean[7];
		}

		@Override
		void executeCommand() throws FatalException
		{
			// TODO
			Level[] verbosityLevels = new Level[] { Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER,
					Level.FINEST, Level.ALL, Level.OFF };
			Level verbosityLevel = verbosityLevels[verbosity.length];

			LoggingUtils.infof("Setting log verbosity to %s.", verbosityLevel.getName());
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
			ExceptionHandler.initiate(config.pastebin);

			try
			{
				JDABuilder jdaBuilder = new JDABuilder(AccountType.BOT).setAudioEnabled(false)
						.setToken(botAccount.token).addEventListener(GlobalEventListener.listener);
				LoggingUtils.infof("Setting sharding. Shard id: %d. Total shards: %d.", shardId, totalShards);
				jdaBuilder.useSharding(shardId, totalShards).build().awaitReady();
			} catch (LoginException | InterruptedException e)
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
	@Command(name = "", mixinStandardHelpOptions = true, version = SushiRole.VERSION)
	public static final class DefaultCommand extends CLICommand
	{
		@Override
		void verifyParameters() throws ParameterException
		{
		}

		@Override
		void executeCommand() throws FatalException
		{
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
		CommandLine cl = CommandUtils.generateCommands(CLICommand.class);
		cl.parseWithHandler(new RunLast().useOut(System.out).useAnsi(Help.Ansi.AUTO), args);
	}

	private static final void tpe(String message, Object...args)
	{
		throw new ParameterException(String.format(message, args));
	}
}
