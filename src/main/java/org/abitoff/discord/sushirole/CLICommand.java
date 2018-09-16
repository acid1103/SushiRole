package org.abitoff.discord.sushirole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.abitoff.discord.sushirole.utils.JCommanderUtils;
import org.abitoff.discord.sushirole.utils.JCommanderUtils.JCommanderPackage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

public abstract class CLICommand
{
	private static final JCommanderPackage<CLICommand> parsePackage = JCommanderUtils
			.generateJCommanderPackage(CLICommand.class);

	@Parameters(commandDescription = "Runs the bot",commandNames = { "run" })
	public static class RunCommand extends CLICommand
	{
		private static final boolean defaultDev = false;
		private static final List<Integer> defaultShard = new ArrayList<Integer>(Arrays.asList(0, 1));

		@Parameter(names = { "-dev", "--developer_mode" },description = "Boolean value used to indicate which bot to "
				+ "launch. True launches the dev mode bot, and false launches the public bot. Usage: '-dev flag' "
				+ "Example: '-dev false'",arity = 1)
		private boolean dev = defaultDev;

		@Parameter(names = { "-shard" },description = "Two integers, used to determine the shard ID for this instance "
				+ "of the bot. Usage: '-shard shardId totalShards' Example: '-shard 5 10' For further information, see "
				+ "https://discordapp.com/developers/docs/topics/gateway#sharding",arity = 2)
		private List<Integer> shard = defaultShard;

		public final boolean devMode()
		{
			return dev;
		}

		public final int shardId()
		{
			return shard.get(0);
		}

		public final int shardTotal()
		{
			return shard.get(1);
		}
	}

	public static final CLICommand parseCommand(String...args) throws Exception
	{
		Map<String,CLICommand> commands = parsePackage.commands;
		JCommander jc = parsePackage.commandParser;
		CLICommand cmd;
		String cmdString = null;
		try
		{
			jc.parse(args);
			cmdString = jc.getParsedCommand();
			if (cmdString == null || (cmd = commands.get(cmdString)) == null)
			{
				throw new UnknownCommandException(
						"Unrecognized command. Valid commands: " + commands.keySet().toString());
			}
		} catch (Exception e)
		{
			StringBuilder sb = new StringBuilder();
			// displays nicely in discord code blocks on monitors with resolutions as low as 720p, assuming no strange edge cases
			jc.setColumnSize(70);
			if (cmdString == null || e instanceof UnknownCommandException)
				jc.usage(sb);
			else
				jc.usage(cmdString, sb);
			System.err.println(sb.toString());
			throw e;
		}
		return cmd;
	}

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
