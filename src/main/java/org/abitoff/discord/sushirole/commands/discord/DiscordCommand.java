package org.abitoff.discord.sushirole.commands.discord;

import java.util.Locale;
import java.util.ResourceBundle;

import org.abitoff.discord.sushirole.SushiRole;
import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;
import org.abitoff.discord.sushirole.utils.CommandUtils;

import picocli.CommandLine;

public abstract class DiscordCommand extends Command
{
	private static final CommandLine cl = CommandUtils.generateCommands(DiscordCommand.class,
			ResourceBundle.getBundle("locale.discord", Locale.US));

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	@CommandLine.Command(
			name = "",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class DefaultCommand extends DiscordCommand
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
	public Class<DefaultCommand> defaultCommand()
	{
		return DefaultCommand.class;
	}
}
