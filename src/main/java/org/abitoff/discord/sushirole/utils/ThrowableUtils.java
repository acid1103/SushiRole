package org.abitoff.discord.sushirole.utils;

public final class ThrowableUtils
{
	private ThrowableUtils()
	{
	}

	public static void throwUnknownThrowable(Throwable t)
	{
		if (t instanceof RuntimeException)
			throw (RuntimeException) t;
		else if (t instanceof Error)
			throw (Error) t;
		else
			throw new RuntimeException(t);
	}
}
