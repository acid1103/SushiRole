package org.abitoff.discord.sushirole;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.abitoff.discord.sushirole.commands.cli.CLICommand;
import org.abitoff.discord.sushirole.exceptions.FatalException;

public class SushiRole
{
	public static final Logger LOG = Logger.getLogger("SushiRole");
	public static final String VERSION = "SushiRole v0.1a";

	public static void main(String[] args) throws FatalException
	{
		LOG.setLevel(Level.ALL);
		args = "run -dvvvvvvs0 -S1".split(" ");
		CLICommand.executeCommand(args);
	}
}
