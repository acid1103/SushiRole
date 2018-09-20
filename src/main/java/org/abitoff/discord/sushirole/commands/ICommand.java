package org.abitoff.discord.sushirole.commands;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public interface ICommand
{
	/**
	 * TODO
	 * 
	 * @return
	 */
	public Class<? extends ICommand> defaultCommand();
}
