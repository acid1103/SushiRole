package org.abitoff.discord.sushirole.commands.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import javax.security.auth.login.LoginException;

import org.abitoff.discord.sushirole.SushiRole;
import org.abitoff.discord.sushirole.commands.Command;
import org.abitoff.discord.sushirole.config.SushiRoleConfig;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.BotConfig;
import org.abitoff.discord.sushirole.events.GlobalEventListener;
import org.abitoff.discord.sushirole.exceptions.ExceptionHandler;
import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.exceptions.ParameterException;
import org.abitoff.discord.sushirole.utils.CommandUtils;
import org.abitoff.discord.sushirole.utils.IOUtils;
import org.abitoff.discord.sushirole.utils.LoggingUtils;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadFactory;

import ch.qos.logback.classic.Level;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.TextChannel;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.RunLast;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public abstract class CLICommand extends Command
{
	/** TODO */
	private static final CommandLine cl = CommandUtils.generateCommands(CLICommand.class,
			ResourceBundle.getBundle("locale.cli", Locale.US));

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	@CommandLine.Command(
			name = "run",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class RunCommand extends CLICommand
	{
		@Option(
				names = {"-d", "--dev"})
		private boolean dev = false;

		@Option(
				names = {"-s", "--shards"},
				paramLabel = "<total shards>")
		private int shards = 1;

		@Option(
				names = {"-v", "--verbose"})
		private boolean[] verbosity = new boolean[0];

		private int verbosityValue = 2;

		@Override
		protected void verifyParameters() throws ParameterException
		{
			// TODO
			if (shards < 1)
				throw new ParameterException("total shards must be at least 1!");

			for (boolean b: verbosity)
				verbosityValue += b ? 1 : -1;
			verbosityValue = Integer.min(Integer.max(verbosityValue, 0), 6);
		}

		@Override
		protected void executeCommand() throws FatalException
		{
			// TODO
			Level[] verbosityLevels = new Level[] {Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE,
					Level.ALL};
			Level verbosityLevel = verbosityLevels[verbosityValue];

			LoggingUtils.infof("Setting log verbosity to %s.", verbosityLevel.toString());
			SushiRole.LOG.setLevel(verbosityLevel);

			File keys = new File("./sushiroleconfig/config");
			SushiRoleConfig config = SushiRoleConfig.create(keys);

			BotConfig botAccount = this.dev ? config.discord_bot_dev : config.discord_bot;

			try
			{
				JDABuilder jdaBuilder = new JDABuilder(AccountType.BOT).setAudioEnabled(false).setToken(botAccount.token)
						.addEventListener(GlobalEventListener.listener);
				LoggingUtils.infof("Building %d %s.", shards, shards > 1 ? "shards" : "shard");
				List<CompletableFuture<Void>> exceptionHandlerWaitingFutures = new LinkedList<CompletableFuture<Void>>();
				for (int i = 0; i < shards; i++)
				{
					jdaBuilder = jdaBuilder.useSharding(i, shards);
					JDA shard = jdaBuilder.build();
					CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
					{
						try
						{
							shard.awaitReady();
						} catch (InterruptedException e)
						{
							LoggingUtils.warnf(
									"InteruptedException while awaiting shard! ExceptionHandler might not initiate properly! Stack trace:\n%s",
									ExceptionHandler.throwableToString(e));
						}
						TextChannel errorChannel = shard.getTextChannelById(config.discord_dev_guild.error_channel_id);
						if (errorChannel != null)
						{
							ExceptionHandler.initiate(config.pastebin, config.discord_dev_guild,
									new File("./sushiroleconfig/error_encryption_key"), errorChannel);
						}
					});
					exceptionHandlerWaitingFutures.add(future);
				}
				for (CompletableFuture<Void> future: exceptionHandlerWaitingFutures)
				{
					future.join();
				}
			} catch (LoginException e)
			{
				throw new FatalException("Exception while starting bot!", e);
			}
		}
	}

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	@CommandLine.Command(
			name = "decrypt",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class DecryptCommand extends CLICommand
	{
		@Parameters(
				index = "0",
				paramLabel = "<input file>")
		private File in;
		@Parameters(
				index = "1",
				paramLabel = "<output file>")
		private File out;

		boolean overwrite = false;

		@Override
		protected void verifyParameters() throws ParameterException
		{
			// verify the input file exists
			if (!in.exists())
			{
				System.err.println("Input file does not exist.");
				System.exit(-1);
			}
			// verify we have read access to the input file
			if (!in.canRead())
			{
				System.err.println("You do not have read access to the input file.");
				System.exit(-1);
			}
			// if the output file exists, get user permission before overwriting it
			if (out.exists())
			{
				Scanner scan = new Scanner(System.in);
				// loop until we get an appropriate answer from the user
				while (true)
				{
					System.out.print(String.format("%s already exists. Overwrite it? y/n: ", out.getName()));
					String response = scan.nextLine();
					// check the response
					if (response.trim().equalsIgnoreCase("n"))
					{
						// if they don't want to overwrite, abort
						System.err.println("Aborting.");
						scan.close();
						System.exit(0);
					} else if (response.trim().equalsIgnoreCase("y"))
					{
						// if they do, exit the loop
						break;
					} else
					{
						// otherwise, tell them we didn't understand.
						System.out.println("Unrecognized response.");
					}
				}
				// close the scanner
				scan.close();
				// set overwrite flag to true for later
				overwrite = true;
				// verify we have write access to the output file
				if (!out.canWrite())
				{
					System.err.println("You do not have write access to the output file.");
					System.exit(-1);
				}
			}
		}

		@Override
		protected void executeCommand() throws FatalException
		{
			// read the file into memory
			LoggingUtils.infof("Reading file...");
			String enc;
			try
			{
				enc = IOUtils.readAll(in);
			} catch (Exception e)
			{
				throw new FatalException("Error while trying to read input file.", e);
			}

			// remove the base64 encoding from the file
			LoggingUtils.infof("Decoding file...");
			byte[] data;
			try
			{
				data = Base64.getUrlDecoder().decode(enc.getBytes(StandardCharsets.UTF_8));
			} catch (IllegalArgumentException e)
			{
				throw new FatalException("Error while decoding the Base64 encoded encryption. "
						+ "Ensure the input file hasn't been tampered with.", e);
			}

			// initiate google tink's AEAD services
			LoggingUtils.infof("Starting decryption service...");
			try
			{
				AeadConfig.register();
			} catch (GeneralSecurityException e)
			{
				throw new FatalException("Something went wrong while starting the decryption service... Good luck.", e);
			}

			// read the encryption key from file
			LoggingUtils.infof("Reading encryption key...");
			KeysetHandle keysetHandle;
			try
			{
				keysetHandle = CleartextKeysetHandle
						.read(JsonKeysetReader.withFile(new File("./sushiroleconfig/error_encryption_key")));
			} catch (GeneralSecurityException | IOException e)
			{
				throw new FatalException("Error while trying to read decryption key. "
						+ "Ensure the key file is in the correct location and format.", e);
			}

			// decrypt the input file
			LoggingUtils.infof("Decrypting file...");
			String plaintext;
			try
			{
				plaintext = new String(AeadFactory.getPrimitive(keysetHandle).decrypt(data, null), StandardCharsets.UTF_8);
			} catch (GeneralSecurityException e)
			{
				throw new FatalException("Something went wrong while starting the decrypting the file... Good luck.", e);
			}

			// if we're not overwriting, create a new file
			if (!overwrite)
			{
				LoggingUtils.infof("Creating output file...");
				try
				{
					out.createNewFile();
				} catch (IOException e)
				{
					throw new FatalException(String.format("Error while creating output file: %s", out.getAbsolutePath()), e);
				}
			}

			// write to the output file
			LoggingUtils.infof("Writing decrypted output file...");
			try (FileWriter fw = new FileWriter(out);)
			{
				fw.write(plaintext);
				fw.flush();
			} catch (IOException e)
			{
				throw new FatalException("Error while writing output file.", e);
			}

			LoggingUtils.infof("Done.");
		}
	}

	/**
	 * TODO
	 * 
	 * @author Steven Fontaine
	 */
	@CommandLine.Command(
			name = "",
			mixinStandardHelpOptions = true,
			version = SushiRole.VERSION)
	public static final class DefaultCommand extends CLICommand
	{
		@Override
		protected void verifyParameters() throws ParameterException
		{
		}

		@Override
		protected void executeCommand() throws FatalException
		{
			cl.usage(System.out);
		}
	}

	@Override
	public final Class<DefaultCommand> defaultCommand()
	{
		return DefaultCommand.class;
	}

	/**
	 * TODO
	 * 
	 * @param args
	 */
	public static void executeCommand(String...args)
	{
		cl.parseWithHandler(new RunLast().useOut(System.out).useAnsi(Help.Ansi.AUTO), args);
	}
}
