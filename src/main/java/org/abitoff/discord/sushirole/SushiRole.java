package org.abitoff.discord.sushirole;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import org.abitoff.discord.sushirole.commands.cli.CLICommand;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.utils.LoggingUtils;
import org.slf4j.LoggerFactory;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.layout.TTLLLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

public class SushiRole
{
	public static final LoggerContext LOGGER_CONTEXT = ((LoggerContext) LoggerFactory.getILoggerFactory());
	public static final Logger LOG = LOGGER_CONTEXT.getLogger(SushiRole.class);
	public static final String VERSION = "SushiRole v0.0.1a";

	public static void main(String[] args) throws FatalException,GeneralSecurityException,IOException
	{
		TTLLLayout layout = new TTLLLayout();
		layout.setContext(LOGGER_CONTEXT);
		layout.start();
		LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<ILoggingEvent>();
		encoder.setLayout(layout);

		FileAppender<ILoggingEvent> fa = new FileAppender<ILoggingEvent>();
		fa.setContext(LOGGER_CONTEXT);
		fa.setEncoder(encoder);
		fa.setFile(new File("./log").getAbsolutePath());
		fa.start();
		LOG.addAppender(fa);
		LoggingUtils.infof("Start");
		LoggingUtils.infof("Finish");
		System.exit(0);

		LOGGER_CONTEXT.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ALL);
		args = "run -dvvvvs1".split(" ");
		CLICommand.executeCommand(args);
	}
}
