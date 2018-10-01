package org.abitoff.discord.sushirole.exceptions;

import net.dv8tion.jda.core.entities.Message;

public class DiscordUserException extends Exception
{
	public final Message context;

	public DiscordUserException(Message context)
	{
		this.context = context;
	}

	public DiscordUserException(Message context, String message)
	{
		super(message);
		this.context = context;
	}

	public DiscordUserException(Message context, Throwable cause)
	{
		super(cause);
		this.context = context;
	}

	public DiscordUserException(Message context, String message, Throwable cause)
	{
		super(message, cause);
		this.context = context;
	}

	public DiscordUserException(Message context, String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
		this.context = context;
	}
}
