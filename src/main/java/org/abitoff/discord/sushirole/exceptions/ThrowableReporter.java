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
	volatile Logger log;
	volatile TextChannel reportingChannel;
	volatile PasteBin pastebin;
	volatile Aead encryptor;

	synchronized void setLogger(Logger log)
	{
		this.log = log;
	}

	synchronized void setReportingDiscordChannel(TextChannel channel) throws ThrowableReportingException
	{
		reportingChannel = channel;
		if (!channel.canTalk())
			throw new ThrowableReportingException("Bot must be able to talk in the error reporting channel to send reports!",
					ExceptionType.DISCORD);
	}

	synchronized void setPastebinAccountCredentials(AccountCredentials credentials) throws ThrowableReportingException
	{
		pastebin = new PasteBin(credentials, ConcurrentPastebinApi.API);
		//pastebin.fetchUserInformation().getAccountType()
	}

	void setPastebinEncryptionKey(KeysetHandle AEADEncryptionKey) throws ThrowableReportingException
	{
		try
		{
			AeadConfig.register();
			synchronized (this)
			{
				encryptor = AeadFactory.getPrimitive(AEADEncryptionKey);
			}
		} catch (Exception e)
		{
			throw new ThrowableReportingException(e, ExceptionType.ENCRYPTION);
		}
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
			DISCORD
		}
	}
}
