package org.abitoff.discord.sushirole;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

public class CLICommands
{
	@Parameters(commandDescription = "Runs the bot")
	public static class RunCommand
	{
		@Parameter(names = { "-dev", "--developer_mode" },description = "Boolean value used to "
				+ "indicate which bot to launch. If present, the bot launches in developer mode. Otherwise, the public bot launches.")
		public boolean dev;
	}
}
