package org.abitoff.discord.sushirole.exceptions;

import net.dv8tion.jda.core.entities.Message;

public class DiscordUserException extends Exception
{
	private static final long serialVersionUID = 6862757518549700345L;

	/** The {@link Message message} that caused this exception */
	public final Message context;

	/**
	 * Constructs a new exception with {@code null} as its detail message. The cause is not initialized, and may subsequently be
	 * initialized by a call to {@link #initCause}.
	 * 
	 * @param context
	 *            The {@link Message message} that caused this exception
	 */
	public DiscordUserException(Message context)
	{
		this.context = context;
	}

	/**
	 * Constructs a new exception with the specified detail message. The cause is not initialized, and may subsequently be
	 * initialized by a call to {@link #initCause}.
	 *
	 * @param context
	 *            The {@link Message message} that caused this exception
	 * @param message
	 *            the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
	 */
	public DiscordUserException(Message context, String message)
	{
		super(message);
		this.context = context;
	}

	/**
	 * Constructs a new exception with the specified cause and a detail message of {@code (cause==null ? null : cause.toString())}
	 * (which typically contains the class and detail message of {@code cause}). This constructor is useful for exceptions that
	 * are little more than wrappers for other throwables (for example, {@link java.security.PrivilegedActionException}).
	 *
	 * @param context
	 *            The {@link Message message} that caused this exception
	 * @param cause
	 *            the cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null} value is
	 *            permitted, and indicates that the cause is nonexistent or unknown.)
	 */
	public DiscordUserException(Message context, Throwable cause)
	{
		super(cause);
		this.context = context;
	}

	/**
	 * Constructs a new exception with the specified detail message and cause.
	 * <p>
	 * Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in this exception's
	 * detail message.
	 *
	 * @param context
	 *            The {@link Message message} that caused this exception
	 * @param message
	 *            the detail message (which is saved for later retrieval by the {@link #getMessage()} method).
	 * @param cause
	 *            the cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null} value is
	 *            permitted, and indicates that the cause is nonexistent or unknown.)
	 */
	public DiscordUserException(Message context, String message, Throwable cause)
	{
		super(message, cause);
		this.context = context;
	}

	/**
	 * Constructs a new exception with the specified detail message, cause, suppression enabled or disabled, and writable stack
	 * trace enabled or disabled.
	 *
	 * @param context
	 *            The {@link Message message} that caused this exception
	 * @param message
	 *            the detail message.
	 * @param cause
	 *            the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
	 * @param enableSuppression
	 *            whether or not suppression is enabled or disabled
	 * @param writableStackTrace
	 *            whether or not the stack trace should be writable
	 */
	public DiscordUserException(Message context, String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
		this.context = context;
	}
}
