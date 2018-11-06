package org.abitoff.discord.sushirole.exceptions;

import java.io.ByteArrayOutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;

import org.abitoff.discord.sushirole.SushiRole;
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
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadFactory;

import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.requests.restaction.MessageAction;

/**
 * TODO<br>
 * TODO<br>
 * TODO<br>
 * TODO<br>
 * very much TODO
 * 
 * @author Steven Fontaine
 */
public class ThrowableReporter
{
	/**
	 * The color to use for error embeds
	 * 
	 * @see ThrowableReporter#buildErrorEmbed(ThrowablePacket)
	 */
	private static final int ERROR_EMBED_COLOR = 0xff0000;
	/**
	 * The max length of an error message to display in a Discord embed field.
	 * 
	 * @see ThrowableReporter#generateFieldMessage(Throwable)
	 */
	private static final int MAX_FIELD_MESSAGE_LENGTH = 128;
	/**
	 * The max number of Throwable {@link Throwable#getCause() causes} to append to a Discord embed.
	 * 
	 * @see ThrowableReporter#appendCauses(Throwable, EmbedBuilder)
	 */
	private static final int MAX_CAUSES_PER_EMBED = 3;
	/**
	 * Format string which gets converted to the author name of an {@link MessageEmbed embed}.
	 * 
	 * @see ThrowableReporter#appendAuthorInformation(MessageHandlingException, EmbedBuilder)
	 */
	private static final String EMBED_AUTHOR_FORMAT = "%guild%>%channel%>%user%";
	/**
	 * The default file extension to use for log files uploaded to Discord.
	 * 
	 * @see ThrowableReporter#generateDiscordMessageAction(ThrowablePacket)
	 */
	private static final String DISCORD_FILE_EXTENSION = ".txt";

	/**
	 * The number of seconds to delay additional reports of encryption trouble.
	 * 
	 * @see ThrowableReporter#encrypt(ThrowablePacket)
	 */
	private static final int REPORT_ENCRYPTION_TROUBLE_DELAY = 300;
	/**
	 * Whether or not we've reported an encryption error within the past {@link ThrowableReporter#REPORT_ENCRYPTION_TROUBLE_DELAY}
	 * seconds.
	 * 
	 * @see ThrowableReporter#encrypt(ThrowablePacket)
	 */
	private static volatile boolean reportedEncryptionTroubles = false;
	/**
	 * A lock object used to synchronize reporting of encryption errors.
	 * 
	 * @see ThrowableReporter#encrypt(ThrowablePacket)
	 */
	private static final Object REPORT_ENCRYPTION_LOCK = new Object();
	/**
	 * The scheduled thread pool used to schedule resets to {@link ThrowableReporter#reportedEncryptionTroubles}
	 * 
	 * @see ThrowableReporter#encrypt(ThrowablePacket)
	 */
	private static final ScheduledExecutorService REPORTED_ENCRYPTION_TROUBLE_REENABLE_POOL = Executors.newScheduledThreadPool(1);

	/**
	 * The {@link Logger} used to report more severe exceptions and optionally (if {@link Logger#isInfoEnabled() info is
	 * enabled}), to report every exception that gets reported this {@link ThrowableReporter}.
	 */
	private final Logger log;
	/** The {@link MessageChannel} to which errors are reported. */
	private final MessageChannel reportingChannel;
	/** The {@link PasteBin} instance used to upload errors to <a href="https://pastebin.com">Pastebin</a>. */
	private final PasteBin pastebin;
	/** The {@link Aead} interface to use for encryption. */
	private final Aead encryptor;

	/**
	 * TODO
	 * 
	 * @param fileLogger
	 * @param devGuildReportingChannel
	 * @param pastebinCredentials
	 * @param AEADEncryptionKey
	 * @throws ThrowableReportingException
	 */
	public ThrowableReporter(Logger fileLogger, MessageChannel devGuildReportingChannel, AccountCredentials pastebinCredentials,
			KeysetHandle AEADEncryptionKey)
	{
		this.log = fileLogger;
		this.reportingChannel = devGuildReportingChannel;
		if (pastebinCredentials != null && AEADEncryptionKey != null)
		{
			this.pastebin = new PasteBin(pastebinCredentials, ConcurrentPastebinApi.API);
			try
			{
				AeadConfig.register();
				this.encryptor = AeadFactory.getPrimitive(AEADEncryptionKey);
			} catch (GeneralSecurityException e)
			{
				throw new ThrowableReportingException("Exception while initiating Tink's AEAD service!", e);
			}
		} else
		{
			this.pastebin = null;
			this.encryptor = null;
		}
	}

	/**
	 * Returns the {@link Logger} associated with this {@link ThrowableReporter}.
	 * 
	 * @see ThrowableReporter#log
	 */
	public Logger getLogger()
	{
		return log;
	}

	/**
	 * Returns the {@link MessageChannel discord channel} used to notify developers of exceptions directly on Discord.
	 * 
	 * @see ThrowableReporter#reportingChannel
	 */
	public MessageChannel getReportingDiscordChannel()
	{
		return reportingChannel;
	}

	/**
	 * Checks whether or not we've been provided a {@link Logger} to report to.
	 */
	private boolean isLogEnabled()
	{
		return log != null;
	}

	/**
	 * Checks whether or not we've been provided a {@link PasteBin} to upload reports to and an {@link Aead} to encrypt those
	 * reports.
	 */
	private boolean isPastebinEnabled()
	{
		return encryptor != null && pastebin != null;
	}

	/**
	 * Checks whether or not we've been provided a {@link MessageChannel} to report to.
	 */
	private boolean isDiscordEnabled()
	{
		return reportingChannel != null;
	}

	/**
	 * TODO
	 * 
	 * @param t
	 * @return
	 */
	public CompletableFuture<ThrowablePacket> reportThrowable(@Nonnull Throwable t)
	{
		Objects.requireNonNull(t, "The throwable must not be null!");
		return CompletableFuture.supplyAsync(() -> processPacket(new ThrowablePacket(t, System.currentTimeMillis())));
	}

	/**
	 * TODO
	 * 
	 * @param packet
	 * @return
	 */
	private ThrowablePacket processPacket(ThrowablePacket packet)
	{
		if (!isDiscordEnabled())
		{
			if (isLogEnabled() && log.isErrorEnabled())
				log.error("The following error has been encountered and reported to a ThrowableReporter associated with this "
						+ "Logger: " + packet.originalThrowable);
			else if (SushiRole.LOG.isErrorEnabled())
				SushiRole.LOG.error("The following error has been encountered and reported to a ThrowableReporter, yet no "
						+ "Discord reporting channel or Logger have been supplied.", packet.originalThrowable);
		} else
		{
			if (isLogEnabled() && log.isInfoEnabled())
				log.info("Reporting the following exception:", packet.originalThrowable);
			if (isPastebinEnabled())
			{
				try
				{
					boolean success;
					try
					{
						encrypt(packet);
						success = true;
					} catch (ThrowableReportingException e)
					{
						packet.encryptionException = e;
						success = false;
					}
					if (success)
					{
						generatePasteTitle(packet);
						try
						{
							uploadToPastebin(packet);
						} catch (ThrowableReportingException e)
						{
							packet.pastebinException = e;
						}
					}
				} catch (Exception e)
				{
					packet.addUnknownException(new ThrowableReportingException(e));
				}
			}

			try
			{
				generateDiscordMessageAction(packet);
				packet.action.complete();
			} catch (Exception e)
			{
				packet.discordException = new ThrowableReportingException(e);
				prepareExceptionsForLogging(packet);
				String report = new String(packet.uploadData, StandardCharsets.UTF_8);
				if (isLogEnabled() && log.isErrorEnabled())
					log.error(report);
				else if (SushiRole.LOG.isErrorEnabled())
					SushiRole.LOG.error(report);
			}
		}

		return packet;
	}

	/**
	 * Encrypts the contents of {@link ThrowablePacket#plaintext packet.plaintext}, using {@link ThrowableReporter#encryptor
	 * this.encryptor}, and putting the encrypted data into {@link ThrowablePacket#uploadData packet.uploadData}.
	 * 
	 * @param packet
	 *            the packet whose content to encrypt
	 * @throws ThrowableReportingException
	 *             if encryption fails
	 */
	private void encrypt(ThrowablePacket packet) throws ThrowableReportingException
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
			{
				NO_REPORT:
				{
					// enter a synchronized block to prevent sending an encryption error twice
					synchronized (REPORT_ENCRYPTION_LOCK)
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
					reportThrowable(new ThrowableReportingException("Error while trying to encrypt message!", e));
					// set a timer to re-enable encryption error reporting after 5 minutes
					REPORTED_ENCRYPTION_TROUBLE_REENABLE_POOL.schedule(() -> reportedEncryptionTroubles = false,
							REPORT_ENCRYPTION_TROUBLE_DELAY, TimeUnit.SECONDS);
				}
			}
			throw new ThrowableReportingException(e);
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
			packet.uploadData = data;
		} catch (Exception e)
		{
			// should never happen, but we catch it just in case.
			throw new ThrowableReportingException(e);
		}
	}

	/**
	 * Generates a title string unique to this ThrowablePacket, placing the title into {@link ThrowablePacket#title packet.title}.
	 * 
	 * @param packet
	 *            the packet whose title to generate
	 */
	private void generatePasteTitle(ThrowablePacket packet)
	{
		String twiddledTimestamp = longToUnsignedPaddedString(bitTwiddle(packet.timestamp));
		int hashcode = Arrays.hashCode(packet.uploadData);
		packet.title = String.format("SushiRoleErr:%s-%010d", twiddledTimestamp, Integer.toUnsignedLong(hashcode));
	}

	/**
	 * Moves the bits of {@code l} around to somewhat evenly distribute them. More specifically, it orders every even bit
	 * (starting at bit 0) in reverse order, stacking them all at the "top" going "down", and does the opposite for the odd bits,
	 * from the "bottom" going "up".
	 * 
	 * @param l
	 *            the long whose bits to twiddle
	 * @return the long after its bits have been twiddled
	 */
	private long bitTwiddle(long l)
	{
		long q = 0;
		long bit;
		int shiftAmount;
		for (int i = 0; i < 64; i++)
		{
			bit = 1l << i & l;
			if ((i & 1) == 0)
			{
				shiftAmount = 63 - 3 * (i >> 1);
				if (shiftAmount > 0)
					q |= bit << shiftAmount;
				else
					q |= bit >> -shiftAmount;
			} else
			{
				shiftAmount = (i >> 1) + 1;
				q |= bit >> shiftAmount;
			}
		}
		return q;
	}

	/**
	 * Uploads {@link ThrowablePacket#uploadData packet.uploadData} to the {@link ThrowableReporter#pastebin Pastebin account},
	 * using {@link ThrowablePacket#title packet.title} as the title for the paste. The paste is configured to never expire and
	 * have private visibility (thus requiring a Pastebin Pro account.) The url of the uploaded paste is placed in
	 * {@link ThrowablePacket#url packet.url}.
	 * 
	 * @param packet
	 *            the packet whose contents to upload
	 */
	private void uploadToPastebin(ThrowablePacket packet)
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
	 * Returns the unsigned decimal representation of {@code l} prepending '0' characters onto the String until it reaches a
	 * length of 20 characters.
	 * 
	 * @param l
	 *            the long to convert
	 * @return the decimal representation
	 */
	private String longToUnsignedPaddedString(long l)
	{
		String s = Long.toUnsignedString(l);
		byte[] prepend = new byte[20 - s.length()];
		for (int i = 0; i < prepend.length; i++)
			prepend[i] = '0';
		return new String(prepend) + s;
	}

	/**
	 * @param packet
	 */
	private void generateDiscordMessageAction(ThrowablePacket packet)
	{
		prepareExceptionsForLogging(packet);
		Message message = new MessageBuilder().setEmbed(buildErrorEmbed(packet)).build();
		if (packet.uploadData == null)
		{
			packet.action = reportingChannel.sendMessage(message);
		} else
		{
			if (packet.uploadData.length > Message.MAX_FILE_SIZE)
			{
				compress(packet);
				packet.title += ".zip";
			} else
			{
				packet.title += DISCORD_FILE_EXTENSION;
			}
			if (packet.uploadData.length > Message.MAX_FILE_SIZE)
			{
				message = new MessageBuilder()
						.setContent("An exception was encountered, and it's too large to post on discord. Check the logs.")
						.build();
				packet.addUnknownException(new ThrowableReportingException(
						"The stacktraces contained herein are too large to post to Discord, despite attempts to compress."));
				packet.action = reportingChannel.sendMessage(message);
			} else
			{
				packet.action = reportingChannel.sendFile(packet.uploadData, packet.title, message);
			}
		}
	}

	private void prepareExceptionsForLogging(ThrowablePacket packet)
	{
		if (packet.url != null)
		{
			packet.uploadData = null;
		} else
		{
			if (!isPastebinEnabled())
			{
				String dataString = "The following exception has been encountered and reported. The reporting ThrowableReporter "
						+ "has no configured encryptor or pastebin api.\n\t";
				dataString += packet.plaintext.replace("\n", "\n\t");
				packet.uploadData = dataString.getBytes(StandardCharsets.UTF_8);
				generatePasteTitle(packet);
			} else
			{
				if (packet.encryptionException == null && packet.pastebinException == null && packet.unknownException == null)
					packet.unknownException = new ThrowableReportingException(
							"packet.url is null, yet there aren't any caught exceptions!");

				String dataString = "The following errors were encountered while attempting to prepare and upload an exception "
						+ "to Pastebin:\n\n";
				if (packet.encryptionException != null)
				{
					dataString += "\tThe following was encountered while attempting to encrypt the data:\n\t\t";
					String eStr = throwableToString(packet.encryptionException).replace("\n", "\n\t\t");
					dataString += eStr + "\n\n";
				}
				if (packet.pastebinException != null)
				{
					dataString += "\tThe following was encountered while attempting to upload the data:\n\t\t";
					String eStr = throwableToString(packet.pastebinException).replace("\n", "\n\t\t");
					dataString += eStr + "\n\n";
				}
				if (packet.discordException != null)
				{
					dataString += "\tThe following was encountered while attempting to report to discord:\n\t\t";
					String eStr = throwableToString(packet.discordException).replace("\n", "\n\t\t");
					dataString += eStr + "\n\n";
				}
				if (packet.unknownException != null)
				{
					dataString += "\tThe following unknown/unexpected error(s) were encountered:\n\t\t";
					String eStr = throwableToString(packet.unknownException).replace("\n", "\n\t\t");
					dataString += eStr + "\n\n";
				}
				dataString += "The preceding errors were encountered while attempting to prepare and upload the following "
						+ "exception:\n\t";
				dataString += packet.plaintext.replace("\n", "\n\t");

				packet.uploadData = dataString.getBytes(StandardCharsets.UTF_8);
				generatePasteTitle(packet);
			}
		}
	}

	/**
	 * Builds the {@link MessageEmbed embed} which is sent to {@link ThrowableReporter#reportingChannel}
	 * 
	 * @param packet
	 *            the packet from which to derive the information for the embed
	 * @return a built embed
	 */
	private MessageEmbed buildErrorEmbed(ThrowablePacket packet)
	{
		int color = ERROR_EMBED_COLOR;
		Instant time = Instant.ofEpochMilli(packet.timestamp);
		EmbedBuilder builder = new EmbedBuilder().setTitle(packet.title, packet.url).setColor(color).setTimestamp(time);

		if (packet.originalThrowable instanceof MessageHandlingException)
			appendAuthorInformation((MessageHandlingException) packet.originalThrowable, builder);
		appendErrorInformation(packet, builder);
		appendCauses(packet.originalThrowable, builder);

		return builder.build();
	}

	/**
	 * Generates the {@link EmbedBuilder#setAuthor(String, String, String) author} information for the embed, using
	 * {@link ThrowableReporter#EMBED_AUTHOR_FORMAT} to format the author name
	 * 
	 * @param exception
	 *            the {@link MessageHandlingException} from which to derive the information about the author
	 * @param builder
	 *            the embed builder to append to
	 */
	private void appendAuthorInformation(MessageHandlingException exception, EmbedBuilder builder)
	{
		Message msg = exception.context;
		User author = msg.getAuthor();

		String name = EMBED_AUTHOR_FORMAT;
		name = name.replace("%guild%", msg.getGuild().getName());
		name = name.replace("%channel%", msg.getChannel().getName());
		name = name.replace("%user%", author.getName() + "#" + author.getDiscriminator());

		String authorIconURL = author.getEffectiveAvatarUrl();
		builder.setAuthor(name, null, authorIconURL);
	}

	/**
	 * Appends information about the throwables to the given embed. Specifically, creates a new {@link MessageEmbed.Field field},
	 * {@link ThrowableReporter#generateFieldMessage(Throwable) generates} its contents, then appends the field onto the given
	 * embed.
	 * 
	 * @param packet
	 *            TODO
	 * @param builder
	 *            the embed builder to append to
	 */
	private void appendErrorInformation(ThrowablePacket packet, EmbedBuilder builder)
	{
		if (packet.pastebinException != null)
		{
			String pmessage = generateFieldMessage(packet.pastebinException);
			builder.addField("Pastebin:", pmessage, false);
		}
		if (packet.unknownException != null)
		{
			String umessage = generateFieldMessage(packet.unknownException);
			builder.addField("Unknown/Unexpected:", umessage, false);
		}
		// add the original exception last, so that the causes appended in appendCauses(Throwable, EmbedBuilder) are directly
		// beneath the original exception.
		String omessage = generateFieldMessage(packet.originalThrowable);
		builder.addField("Exception:", omessage, false);
	}

	/**
	 * Appends up to {@link ThrowableReporter#MAX_CAUSES_PER_EMBED} throwable {@link Throwable#getCause() causes} to the embed
	 * 
	 * @param t
	 *            the throwable whose causes to append
	 * @param builder
	 *            the embed builder to append the causes to
	 */
	private void appendCauses(Throwable t, EmbedBuilder builder)
	{
		int i = 0;
		while (t.getCause() != null && i < MAX_CAUSES_PER_EMBED)
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
	 * {@link ThrowableReporter#MAX_FIELD_MESSAGE_LENGTH}.
	 * 
	 * @param t
	 *            the throwable
	 * @return the message
	 */
	private String generateFieldMessage(Throwable t)
	{
		String message;
		if (t.getMessage() != null && !t.getMessage().equals(""))
		{
			message = t.getMessage();
			if (message.length() > MAX_FIELD_MESSAGE_LENGTH)
				message = message.substring(0, Math.max(0, MAX_FIELD_MESSAGE_LENGTH - 3)) + "...";
		} else
			message = "No message given.";

		return String.format("**%s**\n%s", t.getClass().getSimpleName(), message);
	}

	private void compress(ThrowablePacket packet)
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ZipEntry entry = new ZipEntry(packet.title + DISCORD_FILE_EXTENSION);
		try (ZipOutputStream compressedData = new ZipOutputStream(baos))
		{
			compressedData.putNextEntry(entry);
			compressedData.write(packet.uploadData);
		} catch (IOException e)
		{
			reportThrowable(e);
		}
		packet.uploadData = baos.toByteArray();
	}

	/**
	 * Converts {@code t}, stack trace and all, to a string
	 * 
	 * @param t
	 *            the throwable to convert
	 * @return the string representation of the throwable and stack trace, or "null" if {@code t} is {@code null}
	 */
	public static String throwableToString(Throwable t) throws NullPointerException
	{
		if (t == null)
			return "null";
		StringWriter writer = new StringWriter();
		t.printStackTrace(new PrintWriter(writer));
		String stackTrace = writer.toString();
		return stackTrace;
	}

	public static class ThrowablePacket
	{
		/**
		 * The <a href="https://en.wikipedia.org/wiki/Unix_time">Unix time</a> at which {@link ThrowablePacket#originalThrowable
		 * throwable} was first passed to {@link ExceptionHander}
		 */
		private long timestamp;
		/**
		 * The plaintext of this packet (i.e. {@link ThrowablePacket#originalThrowable throwable} converted to a string using
		 * {@link ThrowableReporter#throwableToString(Throwable) throwableToString()})
		 */
		private String plaintext;
		/** The UTF-8 encoded bytes of {@link ThrowablePacket#plaintext plaintext} */
		private byte[] plaintextBytes;
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
		/** The original exception which is to be reported to {@link ThrowableReporter#reportingChannel} */
		private Throwable originalThrowable;
		/** TODO */
		private MessageAction action;
		/** TODO */
		private ThrowableReportingException encryptionException;
		/** TODO */
		private ThrowableReportingException pastebinException;
		/** TODO */
		private ThrowableReportingException discordException;
		/** TODO */
		private ThrowableReportingException unknownException;

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

		private void addUnknownException(ThrowableReportingException t)
		{
			if (unknownException == null)
				unknownException = t;
			else
				unknownException.addSuppressed(t);
		}

		/**
		 * @return the originalThrowable
		 */
		public Throwable getOriginalThrowable()
		{
			return originalThrowable;
		}

		/**
		 * @return the encryptionException
		 */
		public ThrowableReportingException getEncryptionException()
		{
			return encryptionException;
		}

		/**
		 * @return the pastebinException
		 */
		public ThrowableReportingException getPastebinException()
		{
			return pastebinException;
		}

		/**
		 * @return the discordException
		 */
		public ThrowableReportingException getDiscordException()
		{
			return discordException;
		}

		/**
		 * @return the unknownException
		 */
		public ThrowableReportingException getUnknownException()
		{
			return unknownException;
		}
	}

	/**
	 * A class containing tools for generating and interacting with the header of an exception file.
	 * 
	 * @author Steven Fontaine
	 */
	public static final class ErrorFileUtils
	{
		/** The byte array used to separate the header data from the encrypted data in the file generated and sent to Pastebin */
		public static final byte[] HEADER_SEPARATOR = new byte[] {'.'};

		/**
		 * Generates a byte array which encodes the given HeaderFlags
		 * 
		 * @param flags
		 *            the flags to encode
		 * @return the generated byte array
		 */
		public static byte[] generateHeader(EnumSet<HeaderFlag> flags)
		{
			int size = Utils.divideCeil(HeaderFlag.values().length, 8);
			byte[] header = new byte[size];

			for (HeaderFlag flag: flags)
			{
				int shift = flag.ordinal();
				int index = shift / 8;
				int subshift = shift % 8;
				byte bit = (byte) (128 >> subshift);
				header[index] |= bit;
			}

			byte[] encoded = Base64.getUrlEncoder().encode(header);

			return encoded;
		}

		/**
		 * Reads the header flags from an encoded byte array, returning the compiled set
		 * 
		 * @param header
		 *            the encoded bytes to read the header flags from
		 * @return the set of HeaderFlags obtained from the byte array
		 * @throws IllegalArgumentException
		 *             if {@code header} is not valid Base64
		 */
		public static EnumSet<HeaderFlag> generateFlags(byte[] header) throws IllegalArgumentException
		{
			byte[] decoded = Base64.getUrlDecoder().decode(header);
			EnumSet<HeaderFlag> flags = EnumSet.noneOf(HeaderFlag.class);
			HeaderFlag[] values = HeaderFlag.values();
			for (int i = 0; i < decoded.length; i++)
			{
				for (int j = 0; j < Byte.SIZE; j++)
				{
					int flag = 128 >> j;
					if ((decoded[i] & flag) != 0)
					{
						int ordinal = i * Byte.SIZE + j;
						try
						{
							flags.add(values[ordinal]);
						} catch (IndexOutOfBoundsException e)
						{
							LoggingUtils.warnf("%d is an unrecognized header flag! Either this software is out of date or the "
									+ "file is malformed!", ordinal);
						}
					}
				}
			}
			return flags;
		}

		/**
		 * A set of flags which are prepended to files uploaded to pastebin or discord.
		 * 
		 * @author Steven Fontaine
		 */
		public static enum HeaderFlag
		{
			ENCRYPTED
		}
	}

	/**
	 * A class of Exceptions which indicate an error while processing a {@link ThrowablePacket}.
	 * 
	 * @author Steven Fontaine
	 */
	public static class ThrowableReportingException extends RuntimeException
	{
		private static final long serialVersionUID = -7333805759140993480L;

		public ThrowableReportingException()
		{
			super();
		}

		public ThrowableReportingException(String message)
		{
			super(message);
		}

		public ThrowableReportingException(Throwable cause)
		{
			super(cause);
		}

		public ThrowableReportingException(String message, Throwable cause)
		{
			super(message, cause);
		}

		public ThrowableReportingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
		{
			super(message, cause, enableSuppression, writableStackTrace);
		}
	}
}
