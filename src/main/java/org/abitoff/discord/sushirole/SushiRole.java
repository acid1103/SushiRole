package org.abitoff.discord.sushirole;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.login.LoginException;

import org.abitoff.discord.sushirole.config.SushiRoleConfig;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.BotConfig;
import org.abitoff.discord.sushirole.events.GlobalEventListener;
import org.abitoff.discord.sushirole.exceptions.ExceptionHandler;

import com.beust.jcommander.JCommander;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;

public class SushiRole
{
	public static final Logger LOG = Logger.getLogger("SushiRole");

	public static void main(String[] args) throws Exception
	{
		LOG.setLevel(Level.ALL);
		args = "run -dev tru".split(" ");
		CLICommand.parseCommand(args);
		System.exit(0);
		CLICommand.RunCommand runParams = new CLICommand.RunCommand();
		JCommander jc = JCommander.newBuilder().addCommand("run", runParams).build();
		try
		{
			jc.parse(args);
			if (jc.getParsedCommand() == null)
				throw new Exception();
		} catch (Exception e)
		{
			StringBuilder sb = new StringBuilder();
			jc.usage("run", sb);
			System.err.println(sb.toString());
			throw e;
		}

		File keys = new File("./sushiroleconfig/config");

		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();

		SushiRoleConfig config;
		try (BufferedReader br = new BufferedReader(new FileReader(keys)))
		{
			config = gson.fromJson(br, SushiRoleConfig.class);
		}

		startBot(config, runParams);
	}

	public static JDA startBot(SushiRoleConfig config, CLICommand.RunCommand runParams)
			throws LoginException,InterruptedException
	{
		BotConfig botAccount = runParams.devMode() ? config.discord_bot_dev : config.discord_bot;
		ExceptionHandler.initiate(config.pastebin);

		return new JDABuilder(AccountType.BOT).setToken(botAccount.token).addEventListener(GlobalEventListener.listener)
				.useSharding(runParams.shardId(), runParams.shardTotal()).build().awaitReady();
	}
}
