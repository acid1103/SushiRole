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
import org.abitoff.discord.sushirole.exceptions.ThrowableReporter.ErrorFileUtils;
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
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.requests.restaction.MessageAction;

/**
 * Static class for reporting exceptions to a Discord channel
 * 
 * @author Steven Fontaine
 */
public class ExceptionHandler
{
	/** Whether or not {@link ExceptionHandler} has been initialized (or is currently being initialized) */
	private static boolean initialized = false;
	/**
	 * A lock object used to synchronize
	 * {@link ExceptionHandler#initialize(PastebinConfig, ErrorReportingConfig, File, TextChannel) initialization}. Prevents
	 * {@link ExceptionHandler} from being erroneously initialized more than once.
	 */
	private static final Object initLock = new Object();

	/**
	 * The {@link PasteBin} instance used to upload errors to <a href="https://pastebin.com">Pastebin</a>. Holds important
	 * information, such as account credentials.
	 */
	private static PasteBin pastebin;
	/** TODO */
	private static boolean doPastebin;
	/** The {@link TextChannel} to which errors are reported */
	private static TextChannel errorChannel;
	/** TODO */
	private static boolean doDiscord;
	/** The {@link Aead} interface to use for encryption. Holds important information, such as algorithm and key. */
	private static Aead encryptor;

	/**
	 * The number of seconds to delay additional reports of encryption trouble. (See
	 * {@link ExceptionHandler#encrypt(ThrowablePacket) encrypt()})
	 */
	private static final int reportEncryptionTroubleDelay = 300;
	/**
	 * Whether or not we've reported an encryption error within the past {@link ExceptionHandler#reportEncryptionTroubleDelay
	 * reportEncryptionTroubleDelay} seconds. (See {@link ExceptionHandler#encrypt(ThrowablePacket) encrypt()})
	 */
	private static boolean reportedEncryptionTroubles = false;
	/**
	 * A lock object used to synchronize reporting of encryption errors. (See {@link ExceptionHandler#encrypt(ThrowablePacket)
	 * encrypt()})
	 */
	private static final Object reportEncryptionLock = new Object();
	/**
	 * The scheduled thread pool used to schedule resets to {@link ExceptionHandler#reportedEncryptionTroubles
	 * reportedEncryptionTroubles} (See {@link ExceptionHandler#encrypt(ThrowablePacket) encrypt()})
	 */
	private static final ScheduledExecutorService reportedEncryptionTroubleReenablePool = Executors.newScheduledThreadPool(1);

	/** The color to use for error embeds */
	private static final int errorEmbedColor = 0xff0000;
	/**
	 * The max length of an error message to display in a Discord embed field. (See
	 * {@link ExceptionHandler#generateFieldMessage(Throwable) generateFieldMessage()})
	 */
	private static final int maxFieldMessageLength = 128;
	/**
	 * The max number of Throwable {@link Throwable#getCause() causes} to append to a Discord embed. (See
	 * {@link ExceptionHandler#appendCauses(Throwable, EmbedBuilder) appendCauses()})
	 */
	private static final int maxCausesPerEmbed = 3;
	/**
	 * Format string which gets converted to the author name of an {@link MessageEmbed embed} in
	 * {@link ExceptionHandler#appendAuthorInformation(MessageHandlingException, EmbedBuilder) appendAuthorInformation()}
	 */
	private static final String embedAuthorFormat = "%guild%>%channel%>%user%";

	/**
	 * Initializes the static {@link ExceptionHandler} class
	 * 
	 * @param pbconfig
	 *            object containing login information for pastebin
	 * @param errorEncryptionKey
	 *            file containing the AEAD encryption key used to encrypt content uploaded to pastebin
	 * @param errorChannel
	 *            the TextChannel that exceptions are reported to
	 * @throws FatalException
	 *             if an error is encountered while initializing
	 */
	public static final void initialize(PastebinConfig pbconfig, File errorEncryptionKey, TextChannel errorChannel)
			throws FatalException
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

		if (errorEncryptionKey == null)
		{
			LoggingUtils.warnf("The encryption key passed to ExceptionHandler was null. Exceptions handled by ExceptionHandler "
					+ "will not be uploaded to Pastebin!");
			doPastebin = false;
		} else if (pbconfig == null)
		{
			LoggingUtils.warnf("The Pastebin configuration passed to ExceptionHandler was null. Exceptions handled by "
					+ "ExceptionHandler will not be uploaded to Pastebin!");
			doPastebin = false;
		}
		if (!(doDiscord = errorChannel != null))
		{
			String warn = "The Discord error reporting channel passed to ExceptionHandler was null. Exceptions handled by "
					+ "ExceptionHandler will only be ";
			if (doPastebin)
				warn += "uploaded to Pastebin and ";
			warn += "printed to console!";
			LoggingUtils.warnf(warn);
		}

		if (doPastebin)
		{
			try
			{
				ExceptionHandler.pastebin = new PasteBin(
						new AccountCredentials(pbconfig.dev_key, pbconfig.username, pbconfig.password),
						ConcurrentPastebinApi.API);
			} catch (Exception e)
			{
				LoggingUtils.warnf(
						"An error occurred while trying to initialize the Pastebin account. Exceptions handled by "
								+ "ExceptionHandler will not be encrypted or uploaded to Pastebin!\nInitialization error:\n%s",
						ExceptionHandler.throwableToString(e));
				doPastebin = false;
				ExceptionHandler.pastebin = null;
			}
		}
		if (doDiscord)
		{
			ExceptionHandler.errorChannel = errorChannel;
			if (!errorChannel.canTalk())
			{
				String warn = "Permission to talk in %s is not granted! Exceptions handled by ExceptionHandler will only be ";
				if (doPastebin)
					warn += "uploaded to Pastebin and ";
				warn += "printed to console!";
				LoggingUtils.warnf(warn, errorChannel.getName());
				ExceptionHandler.errorChannel = null;
			}
		}

		if (doPastebin)
		{
			try
			{
				AeadConfig.register();
				KeysetHandle keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withFile(errorEncryptionKey));
				ExceptionHandler.encryptor = AeadFactory.getPrimitive(keysetHandle);
			} catch (GeneralSecurityException | IOException e)
			{
				LoggingUtils.warnf(
						"An error occurred while trying to initialize the encryption service! Exceptions handled by "
								+ "ExceptionHandler will not be encrypted or uploaded to Pastebin!\nInitialization error:\n%s",
						ExceptionHandler.throwableToString(e));
				doPastebin = false;
				ExceptionHandler.encryptor = null;
			}
		}
	}

	/**
	 * Reports the given Throwable to Discord, after first encrypting it and uploading it to the Pastebin Pro account configured
	 * in {@link ExceptionHandler#initialize(PastebinConfig, ErrorReportingConfig, File, TextChannel) initialize()}.
	 * 
	 * @param t
	 *            the Throwable to report
	 * @return A CompletableFuture that evaluates to true if the throwable was successfully reported to discord, false otherwise.
	 *         <br />
	 *         <em>Note this only evaluates to false if the Discord reporting fails. If the encryption or Pastebin upload fail,
	 *         the CompletableFuture will still evaluate to true.</em>
	 */
	public static final CompletableFuture<ThrowablePacket> reportThrowable(@Nonnull Throwable t)
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
	private static CompletableFuture<ThrowablePacket> processPacket(ThrowablePacket packet)
	{
		// we pass the packet around rather than continuously referencing the packet passed to this function because, based on my
		// recollection, referencing a parameter that's outside an anonymous class can be pretty expensive. so we limit it to
		// places where it's strictly necessary.
		CompletableFuture<ThrowablePacket> future = CompletableFuture.supplyAsync(() ->
		{
			if (doPastebin)
			{
				try
				{
					encrypt(packet);
					packet.uploadData = packet.encrypted;
				} catch (EncryptionException e)
				{
					packet.encryptionThrowable = e.getCause();
					packet.uploadData = packet.plaintextBytes;
				} catch (EncodingException e)
				{
					packet.encodingThrowable = e.getCause();
					packet.uploadData = packet.plaintextBytes;
				}
				try
				{
					// only upload the data to pastebin if it's been successfully encrypted. otherwise we resort to discord
					if (packet.flags.contains(ErrorFileUtils.HeaderFlag.ENCRYPTED))
					{
						generatePasteTitle(packet);
						uploadToPastebin(packet);
					}
				} catch (Exception e)
				{
					packet.pastebinThrowable = e;
				}
			}
			if (doDiscord)
			{
				generateDiscordMessageAction(packet);
				try
				{
					packet.action.complete();
				} catch (RuntimeException e)
				{
					packet.discordThrowable = e;
				}
			}

			return packet;
		});

		return future;
	}

	/**
	 * Encrypts the contents of {@link ThrowablePacket#plaintext packet.plaintext}, using the AEAD algorithm and key described in
	 * the key file configured in {@link ExceptionHandler#initialize(PastebinConfig, ErrorReportingConfig, File, TextChannel)
	 * initialize()}.
	 * 
	 * @param packet
	 *            the packet whose content to encrypt
	 * @return the same packet with an updated {@link ThrowablePacket#encrypted encrypted field}
	 * @throws EncryptionException
	 *             if encryption fails
	 * @throws EncodingException
	 *             if base64 encoding fails
	 */
	private static final void encrypt(ThrowablePacket packet) throws EncryptionException,EncodingException
	{
		// get the bytes we need to encrypt
		byte[] plaintext = packet.plaintextBytes;
		// the final data after the encryption process (including a failed encryption)
		byte[] data;
		try
		{
			// synchronize encryption to ensure the internal state of the encryptor remains valid
			synchronized (encryptor)
			{
				// encrypt the text
				data = encryptor.encrypt(plaintext, null);
			}
			// we've finished encrypting. set the encrypted flag
			packet.flags.add(ErrorFileUtils.HeaderFlag.ENCRYPTED);
		} catch (Exception e)
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
					reportThrowable(new EncryptionException("Error while trying to encrypt message!", e));
					// set a timer to re-enable encryption error reporting after 5 minutes
					reportedEncryptionTroubleReenablePool.schedule(() -> reportedEncryptionTroubles = false,
							reportEncryptionTroubleDelay, TimeUnit.SECONDS);
				}
			throw new EncryptionException(e);
		}

		try
		{
			// encode the encrypted bytes to Base64 so the resulting data is easier for humans to interact with
			data = Base64.getUrlEncoder().encode(data);

			// generate the header for the data, encoding whether or not the data was encrypted
			byte[] header = ErrorFileUtils.generateHeader(packet.flags);

			// merge the header and the data
			data = Utils.merge(header, ErrorFileUtils.HEADER_SEPARATOR, data);

			// update the packet
			packet.encrypted = data;
		} catch (Exception e)
		{
			// should never happen, but we catch it just in case.
			throw new EncodingException(e);
		}
	}

	/**
	 * Uploads {@link ThrowablePacket#uploadData packet.uploadData} to the {@link ExceptionHandler#pastebin Pastebin Pro account}
	 * configured in {@link ExceptionHandler#initialize(PastebinConfig, ErrorReportingConfig, File, TextChannel) initialize()},
	 * using {@link ThrowablePacket#title packet.title} as the title for the paste. The paste is configured to never expire and
	 * have private visibility.
	 * 
	 * @param packet
	 *            the packet whose contents to upload
	 * @return the same packet with an updated {@link ThrowablePacket#url url field}
	 */
	private static final void uploadToPastebin(ThrowablePacket packet)
	{
		Paste paste = new Paste();
		paste.setTitle(packet.title);
		paste.setExpiration(PasteExpiration.NEVER);
		paste.setHighLight(PasteHighLight.TEXT);
		paste.setVisibility(PasteVisibility.PRIVATE);
		paste.setContent(new String(packet.uploadData, StandardCharsets.UTF_8));
		packet.url = pastebin.createPaste(paste);
	}

	/**
	 * Reports the original throwable contained within ThrowablePacket to the {@link ExceptionHandler#errorChannel development
	 * discord channel} configured in {@link ExceptionHandler#initialize(PastebinConfig, ErrorReportingConfig, File, TextChannel)
	 * initialize()}.
	 * 
	 * @param packet
	 *            the packet to report
	 * @param pastebinThrowable
	 *            any exception that may have occurred while uploading the packet to pastebin
	 * @return a {@link CompletableFuture} that evaluates to true if the packet was successfully reported to discord
	 *
	 * @see ExceptionHandler#reportThrowable(Throwable)
	 */
	private static final void generateDiscordMessageAction(ThrowablePacket packet)
	{
		// we need these later...
		Message message;

		if (doPastebin)
		{

		} else
		{
			String throwableString = packet.plaintext;
			byte[] data = String.format("Exception:\n%s", throwableString).getBytes();
			packet.uploadData = data;
			generatePasteTitle(packet);
			message = new MessageBuilder().setEmbed(buildErrorEmbed(packet)).build();
			packet.action = errorChannel.sendFile(data, packet.title, message);
		}

		// check if encryption succeeded
		boolean encrypted = packet.flags.contains(ErrorFileUtils.HeaderFlag.ENCRYPTED);
		if (packet.pastebinThrowable == null && encrypted)
		{
			// if encryption succeeded and pastebin didn't error, simply build and send the message to discord.
			message = new MessageBuilder().setEmbed(buildErrorEmbed(packet)).build();

			// build the send action
			packet.action = errorChannel.sendMessage(message);
			// CompletableFuture.supplyAsync(() ->
			// {
			// try
			// {
			// packet.action.complete();
			// } catch (RuntimeException e)
			// {
			// // catch any error that might have occurred while trying to send the message. Since discord isn't playing
			// // nice, print all errors out to console
			// LoggingUtils.errorf(
			// "An error has occurred! It has been uploaded to Pastebin, but reporting it to Discord has failed! It "
			// + "can be found here: %s\nThe Discord error: \n%s\n",
			// packet.url, throwableToString(e));
			// packet.discordThrowable = e;
			// }
			// return packet;
			// });
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
				message = new MessageBuilder().setEmbed(buildErrorEmbed(packet)).build();

				// build the send action
				packet.action = errorChannel.sendFile(data, packet.title, message);
				// CompletableFuture.supplyAsync(() ->
				// {
				// try
				// {
				// packet.action.complete();
				// } catch (RuntimeException e)
				// {
				// // catch any error that might have occurred while trying to send the message. Since discord isn't playing
				// // nice, print all errors out to console
				// LoggingUtils.errorf("An error has occurred! It hasn't been uploaded to Pastebin due to an encryption "
				// + "issue, and an error reporting it to Discord has occurred as well! The original error: %s\nThe "
				// + "Discord error: \n%s\n", throwableString, throwableToString(e));
				// packet.discordThrowable = e;
				// }
				// return packet;
				// });
			} else
			{
				// if pastebin threw an error, we need the stack trace of that as well as the original exception we're trying to
				// upload in the first place. generate those, pack them into a byte array (which will become out new upload data),
				// and update the title with the correct hash of uploadData
				String originalString = packet.plaintext;
				String pastebinString = throwableToString(packet.pastebinThrowable);
				byte[] data = String.format("Original:\n%s\n\nPastebin:\n%s", originalString, pastebinString).getBytes();
				packet.uploadData = data;
				generatePasteTitle(packet);

				// build the message
				message = new MessageBuilder().setEmbed(buildErrorEmbed(packet)).build();

				// build the send action
				packet.action = errorChannel.sendFile(data, packet.title, message);
				// CompletableFuture.supplyAsync(() ->
				// {
				// try
				// {
				// packet.action.complete();
				// } catch (RuntimeException e)
				// {
				// // catch any error that might have occurred while trying to send the message. Since discord isn't playing
				// // nice, print all errors out to console
				// LoggingUtils.errorf("We've encountered many errors. Perhaps there's no internet connection. Upon "
				// + "receiving an original error, an attempt was made to upload the error to Pastebin. This "
				// + "failed. An attempt was then made to notify the developer Discord. This also failed.\n\nThe "
				// + "original error:\n%s\n\nThe Pastebin error:\n%s\n\nThe Discord error:\n%s\n", originalString,
				// pastebinString, throwableToString(e));
				// packet.discordThrowable = e;
				// }
				// return packet;
				// });
			}
		}
	}

	/**
	 * Builds the {@link MessageEmbed embed} which is sent to the configured {@link ExceptionHandler#errorChannel development
	 * discord error reporting channel}
	 * 
	 * @param packet
	 *            the packet from which to derive most of the information for the embed
	 * @param pastebin
	 *            any exception that occurred during the {@link ExceptionHandler#uploadToPastebin(ThrowablePacket) pastebin step},
	 *            or {@code null} if none
	 * @return a built embed
	 */
	private static MessageEmbed buildErrorEmbed(ThrowablePacket packet)
	{
		int color = errorEmbedColor;
		Instant time = Instant.ofEpochMilli(packet.timestamp);
		EmbedBuilder builder = new EmbedBuilder().setTitle(packet.title, packet.url).setColor(color).setTimestamp(time);

		if (packet.originalThrowable instanceof MessageHandlingException)
			appendAuthorInformation((MessageHandlingException) packet.originalThrowable, builder);
		appendErrorInformation(packet.originalThrowable, packet.pastebinThrowable, builder);
		appendCauses(packet.originalThrowable, builder);

		return builder.build();
	}

	/**
	 * Generates the {@link EmbedBuilder#setAuthor(String, String, String) author} information for the embed, using
	 * {@link ExceptionHandler#embedAuthorFormat embedAuthorFormat} to format the author name
	 * 
	 * @param exception
	 *            the {@link MessageHandlingException} from which to derive the information about the author
	 * @param builder
	 *            the embed builder to append to
	 */
	private static void appendAuthorInformation(MessageHandlingException exception, EmbedBuilder builder)
	{
		Message msg = exception.context;
		User author = msg.getAuthor();

		String name = embedAuthorFormat;
		name = name.replace("%guild%", msg.getGuild().getName());
		name = name.replace("%channel%", msg.getChannel().getName());
		name = name.replace("%user%", author.getName() + "#" + author.getDiscriminator());

		String authorIconURL = author.getEffectiveAvatarUrl();
		builder.setAuthor(name, null, authorIconURL);
	}

	/**
	 * Appends information about the throwables to the given embed. Specifically, creates a new {@link MessageEmbed.Field field},
	 * {@link ExceptionHandler#generateFieldMessage(Throwable) generates} its contents, then appends the field onto the given
	 * embed.
	 * 
	 * @param original
	 *            the original exception that we're reporting to Discord
	 * @param pastebin
	 *            any exception that occurred during the {@link ExceptionHandler#uploadToPastebin(ThrowablePacket) pastebin step},
	 *            or {@code null} if none
	 * @param builder
	 *            the embed builder to append to
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
	 * Appends up to {@link ExceptionHandler#maxCausesPerEmbed maxCausesPerEmbed} throwable {@link Throwable#getCause() causes} to
	 * the embed
	 * 
	 * @param t
	 *            the throwable whose causes to append
	 * @param builder
	 *            the embed builder to append the causes to
	 */
	private static void appendCauses(Throwable t, EmbedBuilder builder)
	{
		int i = 0;
		while (t.getCause() != null && i < maxCausesPerEmbed)
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
	 * @return the string representation of the throwable and stack trace, or "null" if {@code t} is {@code null}
	 */
	public static final String throwableToString(Throwable t) throws NullPointerException
	{
		if (t == null)
			return "null";
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
	private static final void generatePasteTitle(ThrowablePacket packet)
	{
		String twiddledTimestamp = longToUnsignedPaddedString(bitTwiddle(packet.timestamp));
		int hashcode = Arrays.hashCode(packet.uploadData);
		packet.title = String.format("SushiRoleErr:%s-%010d", twiddledTimestamp, Integer.toUnsignedLong(hashcode));
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
	 * Ensures that {@link ExceptionHandler} has been successfully initialized
	 * 
	 * @throws FatalException
	 *             if {@link ExceptionHandler} has not been initialized
	 */
	private static void checkInitialized() throws FatalException
	{
		if (!initialized)
			throw new FatalException("ExceptionHandler has not been initialized!");
	}

	/**
	 * Contains all the relevant information to report a Throwable to the appropriate service
	 * 
	 * @author Steven Fontaine
	 */
	public static final class ThrowablePacket
	{
		/**
		 * The <a href="https://en.wikipedia.org/wiki/Unix_time">Unix time</a> at which {@link ThrowablePacket#originalThrowable
		 * throwable} was first passed to {@link ExceptionHander}
		 */
		private long timestamp;
		/**
		 * The plaintext of this packet (i.e. {@link ThrowablePacket#originalThrowable throwable} converted to a string using
		 * {@link ExceptionHandler#throwableToString(Throwable) throwableToString()})
		 */
		private String plaintext;
		/** The UTF-8 encoded bytes of {@link ThrowablePacket#plaintext plaintext} */
		private byte[] plaintextBytes;
		/** The encrypted data of this packet, or null if encryption failed */
		private byte[] encrypted;
		/**
		 * The data (be it plaintext or encrypted) which is to be uploaded to the appropriate service (i.e. discord for plaintext
		 * or pastebin for encrypted)
		 */
		private byte[] uploadData;
		/** Contains all the {@link ErrorFileUtils.HeaderFlag header flags} of this packet */
		private EnumSet<ErrorFileUtils.HeaderFlag> flags;
		/** The title of the pastebin file or discord file */
		private String title;
		/** The pastebin url to which this ThrowablePacket has been uploaded, or null if there was an error with the upload */
		private String url;
		/** The original exception which is to be reported to the {@link ExceptionHandler#errorChannel error discord channel} */
		private Throwable originalThrowable;
		/** TODO */
		private MessageAction action;
		/** TODO */
		private Throwable encryptionThrowable;
		/** TODO */
		private Throwable encodingThrowable;
		/** TODO */
		private Throwable pastebinThrowable;
		/** TODO */
		private Throwable discordThrowable;

		/**
		 * Instantiates a {@link ThrowablePacket} with the given Throwable and timestamp
		 * 
		 * @param t
		 *            the Throwable
		 * @param timestamp
		 *            the timestamp
		 */
		private ThrowablePacket(Throwable t, long timestamp)
		{
			this.originalThrowable = t;
			this.timestamp = timestamp;
			this.plaintext = throwableToString(t);
			this.plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
			flags = EnumSet.noneOf(ErrorFileUtils.HeaderFlag.class);
		}

		/**
		 * @return the originalThrowable
		 */
		public Throwable getOriginalThrowable()
		{
			return originalThrowable;
		}

		/**
		 * @return the encryptionThrowable
		 */
		public Throwable getEncryptionThrowable()
		{
			return encryptionThrowable;
		}

		/**
		 * @return the encodingThrowable
		 */
		public Throwable getEncodingThrowable()
		{
			return encodingThrowable;
		}

		/**
		 * @return the pastebinThrowable
		 */
		public Throwable getPastebinThrowable()
		{
			return pastebinThrowable;
		}

		/**
		 * @return the discordThrowable
		 */
		public Throwable getDiscordThrowable()
		{
			return discordThrowable;
		}
	}

	private static final class EncryptionException extends Exception
	{
		private static final long serialVersionUID = -3803339601525692053L;

		public EncryptionException(String message, Throwable cause)
		{
			super(message, cause);
		}

		private EncryptionException(Throwable cause)
		{
			super(cause);
		}
	}

	private static final class EncodingException extends Exception
	{
		private static final long serialVersionUID = -6710469345577076228L;

		private EncodingException(Throwable cause)
		{
			super(cause);
		}

	}

	// class is static
	private ExceptionHandler()
	{
	}
}
