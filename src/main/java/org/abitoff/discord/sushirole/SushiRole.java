package org.abitoff.discord.sushirole;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SushiRole
{
	public static final Logger LOG = Logger.getLogger("SushiRole");

	public static void main(String[] args) throws Exception
	{
		LOG.setLevel(Level.ALL);
		args = "run -dev true -v 8".split(" ");
		CLICommand command = CLICommand.parseCommand(args);
		command.run();
	}
}
