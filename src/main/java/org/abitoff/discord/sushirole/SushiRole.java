package org.abitoff.discord.sushirole;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.abitoff.discord.sushirole.cli.CLICommand;
import org.abitoff.discord.sushirole.cli.CLICommand2;

import picocli.CommandLine;

public class SushiRole
{
	public static final Logger LOG = Logger.getLogger("SushiRole");

	public static void main(String[] args) throws Exception
	{
		CLICommand2.RunCommand cmd = new CLICommand2.RunCommand();
		new CommandLine(cmd).parse("--dev --shard 5 10".split(" "));
		System.out.println(Arrays.toString(cmd.shard));
		System.exit(0);
		LOG.setLevel(Level.ALL);
		args = "run -dev true -v 8".split(" ");
		CLICommand command = CLICommand.parseCommand(args);
		command.runCommand();
	}
}
