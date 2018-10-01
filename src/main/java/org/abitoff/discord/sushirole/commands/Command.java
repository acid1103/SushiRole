package org.abitoff.discord.sushirole.commands;

import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;

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
	protected abstract void executeCommand() throws FatalException;

	@Override
	public final void run()
	{
		verifyParameters();
		executeCommand();
	}
}
