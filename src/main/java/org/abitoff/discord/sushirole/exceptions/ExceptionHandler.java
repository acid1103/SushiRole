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
import java.util.concurrent.CompletionStage;
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

	/** TODO */
	private static boolean reportedEncryptionTroubles = false;
	/** TODO */
	private static final Object reportEncryptionLock = new Object();
	/** TODO */
	private static final ScheduledExecutorService reportedEncryptionTroubleReenable = Executors.newScheduledThreadPool(1);

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	public static enum HeaderFlags
	{
		ENCRYPTED;

		/**
		 * TODO
		 * 
		 * @param flags
		 * @return
		 */
		private static byte[] generateHeader(EnumSet<HeaderFlags> flags)
		{
			int size = HeaderFlags.values().length / 8;
			// +4 to append the header size
			byte[] header = new byte[size + 4];
			for (HeaderFlags flag: flags)
			{
				int shift = flag.ordinal();
				int index = shift / 8 + 4;
				int subshift = shift % 8;
				byte bit = (byte) (128 >> subshift);
				header[index] |= bit;
			}

			// encode the size
			header[0] = (byte) (size >> 24);
			header[1] = (byte) (size >> 16);
			header[2] = (byte) (size >> 8);
			header[3] = (byte) (size >> 0);

			return header;
		}
	}

	/**
	 * @param pbconfig
	 * @param discordErrorReporting
	 * @param errorEncryptionKey
	 * @param errorChannel
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
	 * Reports the given Throwable to Discord, after first encrypting it and uploading it to the Pastebin Pro account configured
	 * in {@link ExceptionHandler#initiate(PastebinConfig, ErrorReportingConfig, File, TextChannel) initiate()}.
	 * 
	 * @param t
	 *            the Throwable to report
	 * @return A CompletableFuture that evaluates to true if the throwable was successfully reported to discord, false otherwise.
	 *         <br />
	 *         <em>Note this only evaluates to false if the Discord reporting fails. If the encryption or Pastebin upload fail,
	 *         the CompletableFuture will still evaluate to true.</em>
	 */
	public static final CompletableFuture<Boolean> reportThrowable(@Nonnull Throwable t)
	{
		checkInitialized();
		Objects.requireNonNull(t, "The throwable cannot be null!");
		return processPacket(new ThrowablePacket(t, System.currentTimeMillis()));
	}

	/**
	 * Handles all the async interactions of the packet, ensuring the packet gets encrypted (or handled correctly otherwise),
	 * uploaded to pastebin, and reported to discord.
	 * 
	 * @param packet
	 *            the packet to process
	 * @return a CompletableFuture that evaluates to true if the throwable was successfully reported to discord, false otherwise
	 */
	private static CompletableFuture<Boolean> processPacket(ThrowablePacket packet)
	{
		CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
		{
			ThrowablePacket futurePacket = encrypt(packet);
			futurePacket.uploadData = futurePacket.encrypted;
			return futurePacket;
		}).thenApplyAsync(futurePacket ->
		{
			// only upload the data to pastebin if it's been successfully encrypted. otherwise we resort to discord
			if (futurePacket.flags.contains(HeaderFlags.ENCRYPTED))
				uploadToPastebin(generatePasteTitle(futurePacket));
			return futurePacket;
		}).handleAsync((futurePacket, futureException) ->
		{
			return reportToDiscord(futurePacket, futureException);
		}).thenApply((completion) ->
		{
			return CompletableFuture.completedFuture(false).thenCombine(completion, (useless, discordSuccessful) ->
			{
				return discordSuccessful;
			}).join();
		});

		return future;
	}

	/**
	 * Encrypts the contents of {@link ThrowablePacket#plaintext packet.plaintext}, using the AEAD algorithm and key described in
	 * the key file configured in {@link ExceptionHandler#initiate(PastebinConfig, ErrorReportingConfig, File, TextChannel)
	 * initiate()}.
	 * 
	 * @param packet
	 *            the packet whose content to encrypt
	 * @return the same packet with an updated {@link ThrowablePacket#encrypted encrypted field}
	 */
	private static final ThrowablePacket encrypt(ThrowablePacket packet)
	{
		// get the bytes we need to encrypt
		byte[] plaintext = packet.plaintext.getBytes(StandardCharsets.UTF_8);
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

		// generate the header for the data, encoding whether or not the data was encrypted
		byte[] header = HeaderFlags.generateHeader(packet.flags);

		// merge the header and the data
		byte[] data = Utils.merge(header, bytes);

		// encode the (hopefully encrypted) bytes to Base64 so the resulting data is easier for humans to interact with
		byte[] encoded = Base64.getUrlEncoder().encode(data);
		packet.encrypted = encoded;

		return packet;
	}

	/**
	 * Uploads {@link ThrowablePacket#uploadData packet.uploadData} to the {@link ExceptionHandler#pastebin Pastebin Pro account}
	 * configured in {@link ExceptionHandler#initiate(PastebinConfig, ErrorReportingConfig, File, TextChannel) initiate()}, using
	 * {@link ThrowablePacket#title packet.title} as the title for the paste. The paste is configured to never expire and have
	 * private visibility.
	 * 
	 * @param packet
	 *            the packet whose contents to upload
	 * @return the same packet with an updated {@link ThrowablePacket#url url field}
	 */
	private static final ThrowablePacket uploadToPastebin(ThrowablePacket packet)
	{
		Paste paste = new Paste();
		paste.setTitle(packet.title);
		paste.setExpiration(PasteExpiration.NEVER);
		paste.setHighLight(PasteHighLight.TEXT);
		paste.setVisibility(PasteVisibility.PRIVATE);
		paste.setContent(new String(packet.uploadData, StandardCharsets.UTF_8));
		packet.url = pastebin.createPaste(paste);
		return packet;
	}

	/**
	 * Reports the original throwable contained within ThrowablePacket to the {@link ExceptionHandler#errorChannel development
	 * discord channel} configured in {@link ExceptionHandler#initiate(PastebinConfig, ErrorReportingConfig, File, TextChannel)
	 * initiate()}.
	 * 
	 * @param packet
	 *            the packet to report
	 * @param pastebinException
	 *            any exception that may have occurred while uploading the packet to pastebin
	 * @return a {@link CompletionStage} that evaluates to true if the packet was successfully reported to discord
	 *
	 * @see ExceptionHandler#reportThrowable(Throwable)
	 */
	private static final CompletionStage<Boolean> reportToDiscord(ThrowablePacket packet, Throwable pastebinException)
	{
		// we need these later...
		CompletionStage<Boolean> future;
		Message message;

		// check if encryption succeeded
		boolean encrypted = packet.flags.contains(HeaderFlags.ENCRYPTED);
		if (pastebinException == null && encrypted)
		{
			// if encryption succeeded and pastebin didn't error, simply build and send the message to discord.
			message = new MessageBuilder().setEmbed(buildErrorEmbed(pastebinException, packet)).build();
			future = errorChannel.sendMessage(message).submit().handleAsync((msg, discordException) ->
			{
				// catch any error that might have occurred while trying to send the message. Since discord isn't playing nice,
				// print all errors out to console
				boolean err;
				if (err = discordException != null)
					LoggingUtils.errorf(
							"An error has occured! It has been uploaded to Pastebin, but reporting it to Discord has failed! It "
									+ "can be found here: %s\nThe Discord error: \n%s\n",
							packet.url, throwableToString(discordException));
				// return whether or not the throwable was successfully reported to discord
				return !err;
			});
		} else
		{
			// either pastebin threw an error or encryption failed
			if (!encrypted)
			{
				// if we failed to encrypt, start out by generating the file we need to upload, and regenerating the title for the
				// file, with the updated hash.
				// NOTE: we don't upload any information about the failed encryption here, because this was already done in the
				// encryption step. see ExceptionHandler#encrypt(ThrowablePacket) for more details.
				String throwableString = packet.plaintext;
				byte[] data = String.format("Exception:\n%s", throwableString).getBytes();
				packet.uploadData = data;
				generatePasteTitle(packet);

				// build the message
				message = new MessageBuilder().setEmbed(buildErrorEmbed(pastebinException, packet)).build();

				// send the message and upload the file containing the exception stack trace
				future = errorChannel.sendFile(data, packet.title, message).submit().handleAsync((msg, discordException) ->
				{
					// catch any error that might have occurred while trying to send the message. Since discord isn't playing
					// nice, print all errors out to console
					boolean err;
					if (err = discordException != null)
						LoggingUtils.errorf(
								"An error has occured! It hasn't been uploaded to Pastebin due to an encryption issue, and an "
										+ "error reporting it to Discord has occured as well! The original error: %s\nThe "
										+ "Discord error: \n%s\n",
								throwableString, throwableToString(discordException));
					// return whether or not the throwable was successfully reported to discord
					return err;
				});
			} else
			{
				// if pastebin threw an error, we need the stack trace of that as well as the original exception we're trying to
				// upload in the first place. generate those, pack them into a byte array (which will become out new upload data),
				// and update the title with the correct hash of uploadData
				String originalString = packet.plaintext;
				String pastebinString = throwableToString(pastebinException);
				byte[] data = String.format("Original:\n%s\n\nPastebin:\n%s", originalString, pastebinString).getBytes();
				packet.uploadData = data;
				generatePasteTitle(packet);

				// build the message
				message = new MessageBuilder().setEmbed(buildErrorEmbed(pastebinException, packet)).build();

				// send the message and upload the file containing the exception stack traces
				future = errorChannel.sendFile(data, packet.title, message).submit().handleAsync((msg, discordException) ->
				{
					// catch any error that might have occurred while trying to send the message. Since discord isn't playing
					// nice, print all errors out to console
					boolean err;
					if (err = discordException != null)
						LoggingUtils.errorf(
								"We've encountered many errors. Perhaps there's no internet connection. Upon receiving an "
										+ "original error, an attempt was made to upload the error to Pastebin. This failed. An "
										+ "attempt was then made to notify the developer Discord. This also failed.\n\nThe "
										+ "original error:\n%s\n\nThe Pastebin error:\n%s\n\nThe Discord error:\n%s\n",
								originalString, pastebinString, throwableToString(discordException));
					// return whether or not the throwable was successfully reported to discord
					return err;
				});
			}
		}

		return future;
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
	private static MessageEmbed buildErrorEmbed(Throwable pastebin, ThrowablePacket packet)
	{
		int color = 0xff0000;
		Instant time = Instant.ofEpochMilli(packet.timestamp);
		EmbedBuilder builder = new EmbedBuilder().setTitle(packet.title, packet.url).setColor(color).setTimestamp(time);

		if (packet.throwable instanceof DiscordUserException)
			appendMessageInformation(packet.throwable, builder);
		appendErrorInformation(packet.throwable, pastebin, builder);
		appendCauses(packet.throwable, builder);

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
	 * Generates the message value to place in a {@link MessageEmbed.Field field} in an {@link MessageEmbed embed}, given a
	 * throwable. Shortens the message if {@link Throwable#getMessage() t.getMessage()} is longer than
	 * {@link ExceptionHandler#maxFieldMessageLength maxFieldMessageLength}.
	 * 
	 * @param t
	 *            the throwable
	 * @return the message
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
	 * Converts {@code t}, stack trace and all, to a string
	 * 
	 * @param t
	 *            the throwable to convert
	 * @return the string representation of the throwable and stack trace
	 * @throws NullPointerException
	 *             if {@code t} is null
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
	 * Generates a title string unique to this ThrowablePacket
	 * 
	 * @param packet
	 *            the packet whose title to generate
	 * @return the packet with an updated {@link ThrowablePacket#title title} field
	 */
	private static final ThrowablePacket generatePasteTitle(ThrowablePacket packet)
	{
		String twiddledTimestamp = longToUnsignedPaddedString(bitTwiddle(packet.timestamp));
		int hashcode = Arrays.hashCode(packet.uploadData);
		packet.title = String.format("SushiRoleErr:%s-%010d", twiddledTimestamp, Integer.toUnsignedLong(hashcode));
		return packet;
	}

	/**
	 * Moves the bits of {@code l} around to somewhat evenly distribute them. More specifically, it orders every even bit
	 * (starting at bit 0) in reverse order, stacking them all at the "top" going "down", and does the opposite for the odd bits,
	 * from "bottom" going "up".
	 * 
	 * @param l
	 *            the long whose bits to twiddle
	 * @return the long after its bits have been twiddled
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
	 * Returns the unsigned decimal representation of {@code l} prepending '0' characters onto the String until it reaches a
	 * length of 20 characters.
	 * 
	 * @param l
	 *            the long to convert
	 * @return the decimal representation
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

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	private static final class ThrowablePacket
	{
		/** TODO */
		private Throwable throwable;
		/** TODO */
		private long timestamp;
		/** TODO */
		private String plaintext;
		/** TODO */
		private byte[] encrypted;
		/** TODO */
		private byte[] uploadData;
		/** TODO */
		private EnumSet<HeaderFlags> flags;
		/** TODO */
		private String title;
		/** TODO */
		private String url;

		/**
		 * TODO
		 * 
		 * @param t
		 * @param timestamp
		 */
		private ThrowablePacket(Throwable t, long timestamp)
		{
			this.throwable = t;
			this.timestamp = timestamp;
			this.plaintext = throwableToString(t);
			flags = EnumSet.noneOf(HeaderFlags.class);
		}
	}
}
