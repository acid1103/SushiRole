package org.abitoff.discord.sushirole.exceptions;

import java.security.GeneralSecurityException;

import org.abitoff.discord.sushirole.exceptions.ThrowableReporter.ThrowableReportingException.ExceptionType;
import org.abitoff.discord.sushirole.pastebin.ConcurrentPastebinApi;

import com.github.kennedyoliveira.pastebin4j.AccountCredentials;
import com.github.kennedyoliveira.pastebin4j.PasteBin;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadFactory;

import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.core.entities.TextChannel;

public class ThrowableReporter
{
	// private final LockManager<Locks> lockManager = new LockManager<Locks>(EnumSet.allOf(Locks.class));
	private final Logger log;
	private final TextChannel reportingChannel;
	private final PasteBin pastebin;
	private final Aead encryptor;

	public ThrowableReporter(Logger fileLogger, TextChannel devGuildReportingChannel, AccountCredentials pastebinCredentials,
			KeysetHandle AEADEncryptionKey)
	{
		this.log = fileLogger;
		this.reportingChannel = devGuildReportingChannel;
		if (pastebinCredentials != null)
			this.pastebin = new PasteBin(pastebinCredentials, ConcurrentPastebinApi.API);
		else
			this.pastebin = null;
		if (AEADEncryptionKey != null)
		{
			try
			{
				AeadConfig.register();
				this.encryptor = AeadFactory.getPrimitive(AEADEncryptionKey);
			} catch (GeneralSecurityException e)
			{
				throw new ThrowableReportingException("Exception while initiating Tink's AEAD service!", e,
						ExceptionType.ENCRYPTION);
			}
		} else
		{
			this.encryptor = null;
		}
	}

	public Logger getLogger()
	{
		return log;
	}

	public TextChannel getReportingDiscordChannel()
	{
		return reportingChannel;
	}

	/*
	 * private static enum Locks { LOG, REPORTING_CHANNEL, PASTEBIN, ENCRYPTOR, }
	 */

	public static class ThrowablePacket
	{
		Throwable t;
	}

	public static class ThrowableReportingException extends RuntimeException
	{
		private static final long serialVersionUID = -2701521343262068254L;
		public final ExceptionType type;

		public ThrowableReportingException(ExceptionType type)
		{
			super();
			this.type = type;
		}

		public ThrowableReportingException(String message, ExceptionType type)
		{
			super(message);
			this.type = type;
		}

		public ThrowableReportingException(Throwable cause, ExceptionType type)
		{
			super(cause);
			this.type = type;
		}

		public ThrowableReportingException(String message, Throwable cause, ExceptionType type)
		{
			super(message, cause);
			this.type = type;
		}

		public ThrowableReportingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace,
				ExceptionType type)
		{
			super(message, cause, enableSuppression, writableStackTrace);
			this.type = type;
		}

		public static enum ExceptionType
		{
			LOGGING,
			ENCRYPTION,
			PASTEBIN,
			DISCORD,
		}
	}
}
