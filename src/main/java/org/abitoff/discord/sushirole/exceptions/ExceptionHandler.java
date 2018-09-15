package org.abitoff.discord.sushirole.exceptions;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.abitoff.discord.sushirole.config.SushiRoleConfig.PastebinConfig;

import com.github.kennedyoliveira.pastebin4j.AccountCredentials;
import com.github.kennedyoliveira.pastebin4j.Paste;
import com.github.kennedyoliveira.pastebin4j.PasteBin;
import com.github.kennedyoliveira.pastebin4j.PasteExpiration;
import com.github.kennedyoliveira.pastebin4j.PasteHighLight;
import com.github.kennedyoliveira.pastebin4j.PasteVisibility;

public class ExceptionHandler
{
	private static PasteBin pastebin;

	public static final void initiate(PastebinConfig pbconfig)
	{
		pastebin = new PasteBin(new AccountCredentials(pbconfig.dev_key, pbconfig.username, pbconfig.password));
	}

	private static final String throwableToString(Throwable t)
	{
		StringWriter writer = new StringWriter();
		t.printStackTrace(new PrintWriter(writer));
		String stackTrace = writer.toString();
		return stackTrace;
	}

	private static final String generatePasteTitle(String pasteContent)
	{
		return "SushiRoleErr:" + System.currentTimeMillis() + "-" + pasteContent.hashCode();
	}

	public static final String uploadThrowableToPastebin(Throwable t)
	{
		if (pastebin == null)
			throw new RuntimeException("ExceptionHandler has not been initiated!");

		String content = throwableToString(t);
		String title = generatePasteTitle(content);

		Paste paste = new Paste();
		paste.setTitle(title);
		paste.setExpiration(PasteExpiration.NEVER);
		paste.setHighLight(PasteHighLight.TEXT);
		paste.setVisibility(PasteVisibility.PRIVATE);
		paste.setContent(content);
		String url = pastebin.createPaste(paste);

		System.err.println("ERROR: " + url + " : \"" + escape(content) + "\"");

		return url;
	}

	private static final String escape(String str)
	{
		String s = str;
		s = s.replace("\\", "\\\\");
		s = s.replace("\"", "\\\"");
		s = s.replace("\t", "\\t");
		s = s.replace("\b", "\\b");
		s = s.replace("\n", "\\n");
		s = s.replace("\r", "\\r");
		s = s.replace("\f", "\\f");
		return s;
	}
}
