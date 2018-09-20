package org.abitoff.discord.sushirole.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.abitoff.discord.sushirole.SushiRole;

/**
 * TODO
 * 
 * @author Steven Fontaine
 *
 */
public class LoggingUtils
{
	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void severef(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.SEVERE, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void severef(Logger log, String format, Object...args)
	{
		logf(log, Level.SEVERE, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void warningf(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.WARNING, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void warningf(Logger log, String format, Object...args)
	{
		logf(log, Level.WARNING, format, args);
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
	public static final void configf(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.CONFIG, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void configf(Logger log, String format, Object...args)
	{
		logf(log, Level.CONFIG, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void finef(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.FINE, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void finef(Logger log, String format, Object...args)
	{
		logf(log, Level.FINE, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void finerf(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.FINER, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void finerf(Logger log, String format, Object...args)
	{
		logf(log, Level.FINER, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param format
	 * @param args
	 */
	public static final void finestf(String format, Object...args)
	{
		logf(SushiRole.LOG, Level.FINEST, format, args);
	}

	/**
	 * TODO
	 * 
	 * @param log
	 * @param format
	 * @param args
	 */
	public static final void finestf(Logger log, String format, Object...args)
	{
		logf(log, Level.FINEST, format, args);
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
		String[] classMethod = getCallingClassMethod();
		log.logp(level, classMethod[0], classMethod[1], String.format(format, args));
	}

	/**
	 * TODO
	 * 
	 * @return
	 */
	private static final String[] getCallingClassMethod()
	{
		// derived from https://stackoverflow.com/a/11306854

		StackTraceElement[] trace = Thread.currentThread().getStackTrace();
		for(int i = 1; i < trace.length; i++)
		{
			StackTraceElement element = trace[i];
			if (!element.getClassName().equals(LoggingUtils.class.getName())
					&& element.getClassName().indexOf("java.lang.Thread") != 0)
				return new String[] { element.getClassName(), element.getMethodName() };
		}
		return new String[] { "Unknown Class", "Unknown Method" };
	}
}
