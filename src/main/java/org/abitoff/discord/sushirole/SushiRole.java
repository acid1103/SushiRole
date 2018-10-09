package org.abitoff.discord.sushirole;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.abitoff.discord.sushirole.commands.cli.CLICommand;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

public class SushiRole
{
	public static final LoggerContext LOGGER_CONTEXT = ((LoggerContext) LoggerFactory.getILoggerFactory());
	public static final Logger LOG = LOGGER_CONTEXT.getLogger(SushiRole.class.getSimpleName());
	public static final String VERSION = "SushiRole v0.0.1a";

	public static void main(String[] args) throws FatalException,GeneralSecurityException,IOException
	{
		LOGGER_CONTEXT.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ALL);
		args = "run -dvvvvs1".split(" ");
		CLICommand.executeCommand(args);
	}
}
