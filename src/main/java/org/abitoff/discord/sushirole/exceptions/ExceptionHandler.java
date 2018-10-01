package org.abitoff.discord.sushirole.exceptions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;

import org.abitoff.discord.sushirole.config.SushiRoleConfig.DiscordErrorReportingInfo;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.PastebinConfig;
import org.abitoff.discord.sushirole.utils.LoggingUtils;

import com.github.kennedyoliveira.pastebin4j.AccountCredentials;
import com.github.kennedyoliveira.pastebin4j.Paste;
import com.github.kennedyoliveira.pastebin4j.PasteBin;
import com.github.kennedyoliveira.pastebin4j.PasteExpiration;
import com.github.kennedyoliveira.pastebin4j.PasteHighLight;
import com.github.kennedyoliveira.pastebin4j.PasteVisibility;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.TextChannel;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public class ExceptionHandler
{
	/**
	 * TODO
	 */
	private static PasteBin pastebin;

	/**
	 * TODO
	 */
	private static String discordErrorChannel;

	/**
	 * TODO
	 * 
	 * @param pbconfig
	 */
	public static final void initiate(PastebinConfig pbconfig, DiscordErrorReportingInfo discordErrorReporting)
	{
		if (pastebin != null)
			throw new FatalException("ExceptionHandler has already been initiated!");
		pastebin = new PasteBin(new AccountCredentials(pbconfig.dev_key, pbconfig.username, pbconfig.password));
		discordErrorChannel = discordErrorReporting.channel_id;
	}

	public static final void reportThrowable(Throwable t, JDA jda)
	{
		CompletableFuture<String> future = uploadThrowableToPastebin(t);
		future.handleAsync((String url, Throwable futureException) -> reportToDiscord(jda, url, futureException, t));
	}

	/**
	 * TODO
	 * 
	 * @param t
	 * @return
	 */
	public static final CompletableFuture<String> uploadThrowableToPastebin(Throwable t)
	{
		if (pastebin == null)
			throw new FatalException("ExceptionHandler has not been initiated!");

		String content = throwableToString(t);
		String title = generatePasteTitle(content);

		Paste paste = new Paste();
		paste.setTitle(title);
		paste.setExpiration(PasteExpiration.NEVER);
		paste.setHighLight(PasteHighLight.TEXT);
		paste.setVisibility(PasteVisibility.PRIVATE);
		paste.setContent(content);

		CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> pastebin.createPaste(paste));

		return future;
	}

	public static final Void reportToDiscord(JDA jda, String url, Throwable futureException, Throwable originalException)
	{
		TextChannel channel = jda.getTextChannelById(discordErrorChannel);
		if (futureException == null)
		{
			channel.sendMessage(url).queue(null, (Throwable t) -> LoggingUtils.errorf(
					"An error has occured! It has been uploaded to Pastebin, but reporting it to Discord has failed! It can be "
							+ "found here: %s\nThe Discord error: \n%s\n",
					url, throwableToString(t)));
		} else
		{
			LoggingUtils.warnf("An error has occured, and Pastebin is also throwing an error!");
			String message = "```\nAn error has occured. Pastebin is also throwing an error, so the errors will be uploaded here."
					+ "\n\nThe original error:\n%s\n\nThe Pastebin error:\n%s\n(See the console for full error details)\n```";
			String original = throwableToString(originalException);
			String pastebin = throwableToString(futureException);
			// -4 for the 2 "%s"s that will be replaced
			int messageLength = message.length() - 4;
			int originalLength = original.length();
			int pastebinLength = pastebin.length();
			int totalLength = messageLength + originalLength + pastebinLength;
			if (totalLength > 2000)
			{
				String append = "\nTrimmed to meet 2000 character limit.";
				int appendLength = append.length();
				// (totalLength + appendLength) - delta1 = 2000
				int delta1 = totalLength + appendLength - 2000;

				if (originalLength - delta1 >= pastebinLength)
					original = original.substring(0, originalLength - delta1) + append;
				else if (pastebinLength - delta1 >= originalLength)
					pastebin = pastebin.substring(0, pastebinLength - delta1) + append;
				else
				{
					// (2000 - messageLength - 2*appendLength) / 2
					int newLength = 1000 - messageLength / 2 - appendLength;
					original = original.substring(0, newLength) + append;
					pastebin = pastebin.substring(0, newLength) + append;
				}
			}

			message = String.format(message, original, pastebin);
			channel.sendMessage(message).queue(null, (Throwable t) -> LoggingUtils.errorf(
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
	 * @param t
	 * @return
	 */
	private static final String throwableToString(Throwable t)
	{
		StringWriter writer = new StringWriter();
		t.printStackTrace(new PrintWriter(writer));
		String stackTrace = writer.toString();
		stackTrace = stackTrace.replaceAll("\t", "  ");
		return stackTrace;
	}

	/**
	 * TODO
	 * 
	 * @param pasteContent
	 * @return
	 */
	private static final String generatePasteTitle(String pasteContent)
	{
		return "SushiRoleErr:" + System.currentTimeMillis() + "-" + pasteContent.hashCode();
	}
}
