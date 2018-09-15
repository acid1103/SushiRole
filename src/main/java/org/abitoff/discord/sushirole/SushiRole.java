package org.abitoff.discord.sushirole;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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
	public static void main(String[] args) throws IOException,LoginException,InterruptedException
	{
		args = "run -dev".split(" ");
		CLICommands.RunCommand run = new CLICommands.RunCommand();
		JCommander jc = JCommander.newBuilder().addCommand("run", run).build();
		try
		{
			jc.parse(args);
			System.out.println(jc.getParsedCommand());
			if (jc.getParsedCommand() == null)
				throw new Exception();
		} catch (Exception e)
		{
			jc.usage();
			throw new RuntimeException("Invalid CLI arguments");
		}

		File keys = new File("./sushiroleconfig/config");

		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();

		SushiRoleConfig config;
		try (FileReader fr = new FileReader(keys))
		{
			config = gson.fromJson(fr, SushiRoleConfig.class);
		}

		BotConfig botAccount = run.dev ? config.discord_bot_dev : config.discord_bot;
		ExceptionHandler.initiate(config.pastebin);

		JDA jda = new JDABuilder(AccountType.BOT).setToken(botAccount.token).addEventListener(GlobalEventListener.listener)
				.useSharding(0, 1).build().awaitReady();

	}
}
