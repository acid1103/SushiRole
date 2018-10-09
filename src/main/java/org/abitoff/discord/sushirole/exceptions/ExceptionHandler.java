package org.abitoff.discord.sushirole.exceptions;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import org.abitoff.discord.sushirole.config.SushiRoleConfig.ErrorReportingConfig;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.PastebinConfig;
import org.abitoff.discord.sushirole.pastebin.ConcurrentPastebinApi;
import org.abitoff.discord.sushirole.utils.LoggingUtils;

import com.github.kennedyoliveira.pastebin4j.AccountCredentials;
import com.github.kennedyoliveira.pastebin4j.Paste;
import com.github.kennedyoliveira.pastebin4j.PasteBin;
import com.github.kennedyoliveira.pastebin4j.PasteExpiration;
import com.github.kennedyoliveira.pastebin4j.PasteHighLight;
import com.github.kennedyoliveira.pastebin4j.PasteVisibility;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadFactory;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public class ExceptionHandler
{
	/** TODO */
	private static boolean initialized = false;

	/** TODO */
	private static final Object initLock = new Object();

	/** TODO */
	private static PasteBin pastebin;

	/** TODO */
	private static TextChannel errorChannel;

	/** TODO */
	private static Aead encryptor;

	/** TODO */
	private static final Charset UTF_8 = StandardCharsets.UTF_8;

	/**
	 * TODO
	 * 
	 * @param pbconfig
	 * @param discordErrorReporting
	 * @param errorEncryptionKey
	 * @param devGuildShard
	 * @throws FatalException
	 * @throws NullPointerException
	 */
	public static final void initiate(@Nonnull PastebinConfig pbconfig, @Nonnull ErrorReportingConfig discordErrorReporting,
			@Nonnull File errorEncryptionKey, @Nonnull TextChannel errorChannel) throws FatalException,NullPointerException
	{
		if (initialized)
		{
			throw new FatalException("ExceptionHandler has already been initialized!");
		} else
		{
			synchronized (initLock)
			{
				if (initialized)
					throw new FatalException("ExceptionHandler has already been initialized!");
				else
					initialized = true;
			}
		}

		Objects.requireNonNull(pbconfig, "pbconfig");
		Objects.requireNonNull(discordErrorReporting, "discordErrorReporting");
		Objects.requireNonNull(errorEncryptionKey, "errorEncryptionKey");
		Objects.requireNonNull(errorChannel, "errorChannel");

		ExceptionHandler.pastebin = new PasteBin(new AccountCredentials(pbconfig.dev_key, pbconfig.username, pbconfig.password),
				ConcurrentPastebinApi.API);
		ExceptionHandler.errorChannel = errorChannel;
		if (!errorChannel.canTalk())
		{
			throw new FatalException("We don't have permission to talk in the dev error channel!");
		}

		try
		{
			AeadConfig.register();
			KeysetHandle keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withFile(errorEncryptionKey));
			ExceptionHandler.encryptor = AeadFactory.getPrimitive(keysetHandle);
		} catch (GeneralSecurityException | IOException e)
		{
			throw new FatalException("Error initiating encryption service!", e);
		}
	}

	/**
	 * TODO
	 * 
	 * @param t
	 * @param jda
	 */
	public static final void reportThrowable(@Nonnull Throwable t)
	{
		checkInitialized();
		Objects.requireNonNull(t, "t");
		long timestamp = System.currentTimeMillis();
		CompletableFuture<String> future = uploadThrowableToPastebin(t, timestamp);
		future.handleAsync((String url, Throwable futureException) -> reportToDiscord(url, futureException, t, timestamp));
	}

	/**
	 * TODO
	 * 
	 * @param t
	 * @return
	 */
	private static final CompletableFuture<String> uploadThrowableToPastebin(Throwable t, long timestamp)
	{
		CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> encrypt(throwableToString(t))).thenApplyAsync(
				(String encrypted) -> uploadContentToPastebin(generatePasteTitle(encrypted, timestamp), encrypted));

		return future;
	}

	/**
	 * TODO
	 * 
	 * @param message
	 * @return
	 */
	private static final String encrypt(String message)
	{
		byte[] nonencrypted = message.getBytes(UTF_8);
		byte[] encrypted;
		try
		{
			// synchronize encryption to ensure the internal state of the encryptor remains valid
			synchronized (encryptor)
			{
				encrypted = encryptor.encrypt(nonencrypted, null);
			}
		} catch (GeneralSecurityException e)
		{
			throw new FatalException("Error while trying to encrypt message!", e);
		}
		// base 64 encoding is actually thread safe, so there's no need to synchronize.
		byte[] encoded = Base64.getEncoder().encode(encrypted);
		String fin = new String(encoded, UTF_8);
		return fin;
	}

	/**
	 * TODO
	 * 
	 * @param title
	 * @param content
	 * @return
	 */
	private static final String uploadContentToPastebin(String title, String content)
	{
		Paste paste = new Paste();
		paste.setTitle(title);
		paste.setExpiration(PasteExpiration.NEVER);
		paste.setHighLight(PasteHighLight.TEXT);
		paste.setVisibility(PasteVisibility.PRIVATE);
		paste.setContent(content);
		return pastebin.createPaste(paste);
	}

	/**
	 * TODO
	 * 
	 * @param jda
	 * @param url
	 * @param futureException
	 * @param originalException
	 * @param timestamp
	 * @return
	 */
	private static final Void reportToDiscord(String url, Throwable futureException, Throwable originalException, long timestamp)
	{
		Message message = new MessageBuilder().setEmbed(buildErrorEmbed(originalException, futureException, url, timestamp))
				.build();
		if (futureException == null)
		{
			errorChannel.sendMessage(message).queue(null, (Throwable t) -> LoggingUtils.errorf(
					"An error has occured! It has been uploaded to Pastebin, but reporting it to Discord has failed! It can be "
							+ "found here: %s\nThe Discord error: \n%s\n",
					url, throwableToString(t)));
		} else
		{
			String original = throwableToString(originalException);
			String pastebin = throwableToString(futureException);
			String title = generatePasteTitle(original, timestamp);
			byte[] file = String.format("Original:\n%s\n\nPastebin:\n%s", original, pastebin).getBytes();

			errorChannel.sendFile(file, title, message).queue(null, (Throwable t) -> LoggingUtils.errorf(
					"We've encountered many errors. Perhaps there's no internet connection. Upon receiving an original error, an "
							+ "attempt was made to upload the error to Pastebin. This failed. An attempt was then made to notify "
							+ "the developer Discord. This also failed.\n\nThe original error:\n%s\n\nThe Pastebin error:\n%s\n\nThe "
							+ "Discord error:\n%s\n",
					throwableToString(originalException), throwableToString(futureException), throwableToString(t)));
		}
		return null;
	}

	/**
	 * TODO
	 * 
	 * @param original
	 * @param pastebin
	 * @param url
	 * @param timestamp
	 * @return
	 */
	private static MessageEmbed buildErrorEmbed(Throwable original, Throwable pastebin, String url, long timestamp)
	{
		String title = "Error Encountered!";
		int color = 0xff0000;
		Instant time = Instant.ofEpochMilli(timestamp);
		EmbedBuilder builder = new EmbedBuilder().setTitle(title, url).setColor(color).setTimestamp(time);
		if (original instanceof DiscordUserException)
		{
			Message m = ((DiscordUserException) original).context;
			String authorName = String.format("%s>%s>%s#%s", m.getGuild().getName(), m.getChannel().getName(),
					m.getAuthor().getName(), m.getAuthor().getDiscriminator());
			String authorIconURL = m.getAuthor().getEffectiveAvatarUrl();
			builder.setAuthor(authorName, null, authorIconURL);
		}
		if (pastebin == null)
		{
			String orig = String.format("**%s**\n%s", original.getClass().getSimpleName(),
					snipMessageToLength(original.getMessage(), 128));
			builder.addField("Exception:", orig, false);
		} else
		{
			String past = String.format("**%s**\n%s", pastebin.getClass().getSimpleName(),
					snipMessageToLength(pastebin.getMessage(), 128));
			String orig = String.format("**%s**\n%s", original.getClass().getSimpleName(),
					snipMessageToLength(original.getMessage(), 128));
			builder.addField("Pastebin:", past, false);
			builder.addField("Original:", orig, false);
		}
		Throwable t = original;
		int i = 0;
		while (t.getCause() != null && i < 3)
		{
			t = t.getCause();
			i++;
			String value = String.format("**%s**\n%s", t.getClass().getSimpleName(), snipMessageToLength(t.getMessage(), 128));
			builder.addField("Caused by:", value, false);
		}
		if (t.getCause() != null)
		{
			builder.addField("Caused by:", "**Etc...**", false);
		}
		return builder.build();
	}

	/**
	 * TODO
	 * 
	 * @param message
	 * @param length
	 * @return
	 */
	private static final String snipMessageToLength(String message, int length)
	{
		if (message.length() > length)
		{
			return message.substring(0, Math.max(0, length - 3)) + "...";
		}
		return message;
	}

	/**
	 * TODO
	 * 
	 * @param t
	 * @return
	 * @throws NullPointerException
	 */
	public static final String throwableToString(@Nonnull Throwable t) throws NullPointerException
	{
		Objects.requireNonNull(t, "t");
		StringWriter writer = new StringWriter();
		t.printStackTrace(new PrintWriter(writer));
		String stackTrace = writer.toString();
		return stackTrace;
	}

	/**
	 * TODO
	 * 
	 * @param pasteContent
	 * @return
	 */
	private static final String generatePasteTitle(String pasteContent, long timestamp)
	{
		return "SushiRoleErr:" + timestamp + "-" + pasteContent.hashCode();
	}

	/**
	 * TODO
	 * 
	 * @throws FatalException
	 */
	private static void checkInitialized() throws FatalException
	{
		if (!initialized)
			throw new FatalException("ExceptionHandler has not been initiated!");
	}
}
