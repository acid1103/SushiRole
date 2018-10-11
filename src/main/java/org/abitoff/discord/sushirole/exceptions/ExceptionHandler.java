package org.abitoff.discord.sushirole.exceptions;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.abitoff.discord.sushirole.config.SushiRoleConfig.ErrorReportingConfig;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.PastebinConfig;
import org.abitoff.discord.sushirole.pastebin.ConcurrentPastebinApi;
import org.abitoff.discord.sushirole.utils.LoggingUtils;
import org.abitoff.discord.sushirole.utils.Utils;

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

	/** The max length of an error message to display in a Discord embed field */
	private static final int maxFieldMessageLength = 128;

	private static boolean reportedEncryptionTroubles = false;
	private static final Object reportEncryptionLock = new Object();
	private static final ScheduledExecutorService reportedEncryptionTroubleReenable = Executors.newScheduledThreadPool(1);

	public static enum HeaderFlags
	{
		ENCRYPTED;
	}

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

		Objects.requireNonNull(pbconfig, "The pastebin config cannot be null!");
		Objects.requireNonNull(discordErrorReporting, "The error reporting config cannot be null!");
		Objects.requireNonNull(errorEncryptionKey, "The encryption key file cannot be null!");
		Objects.requireNonNull(errorChannel, "The error channel cannot be null!");

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
		Objects.requireNonNull(t, "The throwable cannot be null!");
		ThrowablePacket packet = new ThrowablePacket(t, System.currentTimeMillis());
		CompletableFuture<ThrowablePacket> future = uploadThrowableToPastebin(packet);
		future.handleAsync((futurePacket, futureException) -> reportToDiscord(futurePacket, futureException, t));
	}

	/**
	 * TODO
	 * 
	 * @param t
	 * @return
	 */
	private static final CompletableFuture<ThrowablePacket> uploadThrowableToPastebin(ThrowablePacket packet)
	{
		CompletableFuture<ThrowablePacket> future = CompletableFuture.supplyAsync(() -> encrypt(packet));
		future.thenApplyAsync(futurePacket ->
		{
			// only upload the data to pastebin if it's been successfully encrypted. otherwise we resort to discord
			if (futurePacket.flags.contains(HeaderFlags.ENCRYPTED))
				uploadContentToPastebin(generatePasteTitle(futurePacket));
			return futurePacket;
		});

		return future;
	}

	/**
	 * TODO
	 * 
	 * @param packet
	 * @return
	 */
	private static final ThrowablePacket encrypt(ThrowablePacket packet)
	{
		// get the bytes we need to encrypt
		byte[] plaintext = packet.plaintext;
		// the final bytes after the encryption process (including a failed encryption)
		byte[] bytes;
		try
		{
			// synchronize encryption to ensure the internal state of the encryptor remains valid
			synchronized (encryptor)
			{
				// encrypt the text
				bytes = encryptor.encrypt(plaintext, null);
			}
			// we've finished encrypting. update the packet
			packet.encrypted = bytes;
			packet.flags.add(HeaderFlags.ENCRYPTED);
		} catch (GeneralSecurityException e)
		{
			// something went wrong with the encryption
			// check if we've encrypted within the past 5 minutes
			if (!reportedEncryptionTroubles)
				NO_REPORT:
				{
					// enter a synchronized block to prevent sending an encryption error twice
					synchronized (reportEncryptionLock)
					{
						// double check that we haven't sent an encryption error in the time it took to enter the synchronized
						// block
						if (reportedEncryptionTroubles)
							// if we have, break out to the end of the NO_REPORT label, skipping the error ending. this allows the
							// synchronized block to be as short as possible.
							break NO_REPORT;
						else
							// we haven't sent an encryption error yet, so set this to true to indicate that we're about to do
							// that
							reportedEncryptionTroubles = true;
					}
					// report the error, uploading it to the dev discord
					reportThrowable(new FatalException("Error while trying to encrypt message!", e));
					// set a timer to re-enable encryption error reporting after 5 minutes
					reportedEncryptionTroubleReenable.schedule(() -> reportedEncryptionTroubles = false, 5, TimeUnit.MINUTES);
				}
			// we failed to encrypt, so just use the plaintext for the remainder of this error report
			bytes = plaintext;
		}
	
		// generate the header for the data, encoding whether or not the data was encrypted. we use a long here so we have plenty
		// of room to play with if we end up needing more header flags. future compatibility and all that.
		long headerPacked = 0;
		for (HeaderFlags flag: packet.flags)
			headerPacked |= 1l << flag.ordinal();
		byte[] header = Utils.longToBytes(headerPacked);
	
		// merge the header and the data
		byte[] data = Utils.merge(header, bytes);
	
		// encode the (hopefully encrypted) bytes to Base64 so the resulting data is easier for humans to interact with
		byte[] encoded = Base64.getUrlEncoder().encode(data);
		packet.encoded = encoded;
	
		return packet;
	}

	/**
	 * TODO
	 * 
	 * @param title
	 * @param content
	 * @return
	 */
	private static final ThrowablePacket uploadContentToPastebin(ThrowablePacket packet)
	{
		Paste paste = new Paste();
		paste.setTitle(packet.title);
		paste.setExpiration(PasteExpiration.NEVER);
		paste.setHighLight(PasteHighLight.TEXT);
		paste.setVisibility(PasteVisibility.PRIVATE);
		paste.setContent(new String(packet.encoded, StandardCharsets.UTF_8));
		packet.url = pastebin.createPaste(paste);
		return packet;
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
	private static final Void reportToDiscord(ThrowablePacket packet, Throwable futureException, Throwable originalException)
	{
		Message message = new MessageBuilder()
				.setEmbed(buildErrorEmbed(originalException, futureException, packet.url, packet.timestamp)).build();

		boolean encrypted = packet.flags.contains(HeaderFlags.ENCRYPTED);
		if (futureException == null && encrypted)
		{
			errorChannel.sendMessage(message).queue(null, (Throwable t) -> LoggingUtils.errorf(
					"An error has occured! It has been uploaded to Pastebin, but reporting it to Discord has failed! It can be "
							+ "found here: %s\nThe Discord error: \n%s\n",
					packet.url, throwableToString(t)));
		} else if (!encrypted)
		{
			// DO SOMETHING HERE!
		} else
		{
			packet.encoded = packet.plaintext;
			generatePasteTitle(packet);
			String original = throwableToString(originalException);
			String pastebin = throwableToString(futureException);
			String title = packet.title;
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
			appendMessageInformation(original, builder);
		appendErrorInformation(original, pastebin, builder);
		appendCauses(original, builder);

		return builder.build();
	}

	/**
	 * TODO
	 * 
	 * @param original
	 * @param builder
	 */
	private static void appendMessageInformation(Throwable original, EmbedBuilder builder)
	{
		Message m = ((DiscordUserException) original).context;
		String authorName = String.format("%s>%s>%s#%s", m.getGuild().getName(), m.getChannel().getName(),
				m.getAuthor().getName(), m.getAuthor().getDiscriminator());
		String authorIconURL = m.getAuthor().getEffectiveAvatarUrl();
		builder.setAuthor(authorName, null, authorIconURL);
	}

	/**
	 * TODO
	 * 
	 * @param original
	 * @param pastebin
	 * @param builder
	 */
	private static void appendErrorInformation(Throwable original, Throwable pastebin, EmbedBuilder builder)
	{
		if (pastebin == null)
		{
			String message = generateFieldMessage(original);
			builder.addField("Exception:", message, false);
		} else
		{
			String pmessage = generateFieldMessage(pastebin);
			String omessage = generateFieldMessage(original);
			builder.addField("Pastebin:", pmessage, false);
			builder.addField("Original:", omessage, false);
		}
	}

	/**
	 * TODO
	 * 
	 * @param t
	 * @param builder
	 */
	private static void appendCauses(Throwable t, EmbedBuilder builder)
	{
		int i = 0;
		while (t.getCause() != null && i < 3)
		{
			t = t.getCause();
			i++;
			String message = generateFieldMessage(t);
			builder.addField("Caused by:", message, false);
		}
		if (t.getCause() != null)
		{
			builder.addField("Caused by:", "**Etc...**", false);
		}
	}

	/**
	 * TODO
	 * 
	 * @param t
	 * @return
	 */
	private static String generateFieldMessage(Throwable t)
	{
		String message;
		if (t.getMessage() != null && !t.getMessage().equals(""))
		{
			message = t.getMessage();
			if (message.length() > maxFieldMessageLength)
				message = message.substring(0, Math.max(0, maxFieldMessageLength - 3)) + "...";
		} else
			message = "No message given.";

		return String.format("**%s**\n%s", t.getClass().getSimpleName(), message);
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
		Objects.requireNonNull(t, "The throwable cannot be null!");
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
	private static final ThrowablePacket generatePasteTitle(ThrowablePacket packet)
	{
		String twiddledTimestamp = longToUnsignedPaddedString(bitTwiddle(packet.timestamp));
		int hashcode = Arrays.hashCode(packet.encoded);
		packet.title = String.format("SushiRoleErr:%s-%010d", twiddledTimestamp, Integer.toUnsignedLong(hashcode));
		return packet;
	}

	/**
	 * TODO
	 * 
	 * @param l
	 * @return
	 */
	private static final long bitTwiddle(long l)
	{
		long q = 0;
		long bit;
		int shiftAmount;
		for (int i = 0; i < 64; i++)
		{
			bit = 1l << i & l;
			if ((i & 1) == 0)
			{
				shiftAmount = 63 - 3 * i / 2;
				if (shiftAmount > 0)
					q |= bit << shiftAmount;
				else
					q |= bit >> -shiftAmount;
			} else
			{
				shiftAmount = i / 2 + 1;
				q |= bit >> shiftAmount;
			}
		}
		return q;
	}

	/**
	 * TODO
	 * 
	 * @param l
	 * @return
	 */
	private static final String longToUnsignedPaddedString(long l)
	{
		String s = Long.toUnsignedString(l);
		byte[] prepend = new byte[20 - s.length()];
		for (int i = 0; i < prepend.length; i++)
			prepend[i] = '0';
		return new String(prepend) + s;
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

	private static final class ThrowablePacket
	{
		private long timestamp;
		private byte[] plaintext;
		private byte[] encrypted;
		private byte[] encoded;
		private EnumSet<HeaderFlags> flags;
		private String title;
		private String url;

		private ThrowablePacket(Throwable t, long timestamp)
		{
			this.timestamp = timestamp;
			plaintext = throwableToString(t).getBytes(StandardCharsets.UTF_8);
			flags = EnumSet.noneOf(HeaderFlags.class);
		}
	}
}
