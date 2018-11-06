package org.abitoff.discord.sushirole.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import org.abitoff.discord.sushirole.SushiRole;

/**
 * TODO
 * 
 * @author Steven Fontaine
 *
 */
public class LoggingUtils
{
	private LoggingUtils()
	{
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void errorf(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.ERROR, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void errorf(Logger log, String format, Object...args)
	{
		logf(log, Level.ERROR, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void warnf(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.WARN, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void warnf(Logger log, String format, Object...args)
	{
		logf(log, Level.WARN, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void infof(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.INFO, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void infof(Logger log, String format, Object...args)
	{
		logf(log, Level.INFO, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void debugf(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.DEBUG, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void debugf(Logger log, String format, Object...args)
	{
		logf(log, Level.DEBUG, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void tracef(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.TRACE, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void tracef(Logger log, String format, Object...args)
	{
		logf(log, Level.TRACE, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param level
	 * @param format
	 * @param args
	 */
	public static final void logf(Level level, String format, Object...args)
	{
		logf(SushiRole.LOG, level, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param level
	 * @param format
	 * @param args
	 */
	public static final void logf(Logger log, Level level, String format, Object...args)
	{
		if (log.isEnabledFor(level))
			log.log(null, LoggingUtils.class.getName(), Level.toLocationAwareLoggerInteger(level), String.format(format, args),
					null, null);
	}
}
