package org.abitoff.discord.sushirole.commands;

import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;
import org.abitoff.discord.sushirole.utils.LoggingUtils;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public abstract class Command implements Runnable
{
	/**
	 * TODO
	 * 
	 * @return
	 */
	public abstract Class<? extends Command> defaultCommand();

	/**
	 * TODO
	 * 
	 * @throws ParameterException
	 */
	protected abstract void verifyParameters() throws ParameterException;

	/**
	 * TODO
	 * 
	 * @throws FatalException
	 */
	protected abstract void executeCommand(Object...args) throws FatalException;

	@Override
	public final void run()
	{
		execute();
	}

	public final void execute(Object...args)
	{
		LoggingUtils.tracef("Verifying parameters");
		verifyParameters();
		LoggingUtils.tracef("Verified");
		LoggingUtils.tracef("Beginning execution");
		executeCommand(args);
		LoggingUtils.tracef("Finished execution");
	}
}
