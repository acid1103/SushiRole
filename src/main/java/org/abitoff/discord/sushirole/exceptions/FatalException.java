package org.abitoff.discord.sushirole.exceptions;

/**
 * {@code FatalExceptions} denote exceptions which are of a severity such that they cannot be caught. In other words,
 * {@code FatalExceptions} are errors which will cause a crash. Used to bypass the automatic exception handling/reporting in
 * {@link ExceptionHandler}.
 * 
 * @author Steven Fontaine
 */
public class FatalException extends RuntimeException
{
	private static final long serialVersionUID = 6036341493314427297L;

	/**
	 * Constructs a new fatal exception with {@code null} as its detail message. The cause is not initialized, and may
	 * subsequently be initialized by a call to {@link #initCause}.
	 */
	public FatalException()
	{
		super();
	}

	/**
	 * Constructs a new fatal exception with the specified detail message. The cause is not initialized, and may subsequently be
	 * initialized by a call to {@link #initCause}.
	 *
	 * @param message
	 *            the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
	 */
	public FatalException(String message)
	{
		super(message);
	}

	/**
	 * Constructs a new fatal exception with the specified cause and a detail message of
	 * <tt>(cause==null ? null : cause.toString())</tt> (which typically contains the class and detail message of <tt>cause</tt>).
	 *
	 * @param cause
	 *            the cause (which is saved for later retrieval by the {@link #getCause()} method). (A <tt>null</tt> value is
	 *            permitted, and indicates that the cause is nonexistent or unknown.)
	 */
	public FatalException(Throwable cause)
	{
		super(cause);
	}


	/**
	 * Constructs a new fatal exception with the specified detail message and cause.
	 * <p>
	 * Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in this runtime
	 * exception's detail message.
	 *
	 * @param message
	 *            the detail message (which is saved for later retrieval by the {@link #getMessage()} method).
	 * @param cause
	 *            the cause (which is saved for later retrieval by the {@link #getCause()} method). (A <tt>null</tt> value is
	 *            permitted, and indicates that the cause is nonexistent or unknown.)
	 */
	public FatalException(String message, Throwable cause)
	{
		super(message, cause);
	}

	/**
	 * Constructs a new fatal exception with the specified detail message, cause, suppression enabled or disabled, and writable
	 * stack trace enabled or disabled.
	 *
	 * @param message
	 *            the detail message.
	 * @param cause
	 *            the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
	 * @param enableSuppression
	 *            whether or not suppression is enabled or disabled
	 * @param writableStackTrace
	 *            whether or not the stack trace should be writable
	 */
	public FatalException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
