package org.abitoff.discord.sushirole;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.security.auth.login.LoginException;

import org.abitoff.discord.sushirole.config.SushiRoleConfig;
import org.abitoff.discord.sushirole.config.SushiRoleConfig.BotConfig;

import com.beust.jcommander.JCommander;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.hooks.EventListener;

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

		SushiRoleConfig k;
		BotConfig botAccount;
		try (FileReader fr = new FileReader(keys))
		{
			k = gson.fromJson(fr, SushiRoleConfig.class);
			botAccount = run.dev ? k.discord_bot_dev : k.discord_bot;
		}

		JDA jda = new JDABuilder(AccountType.BOT).setToken(botAccount.token).build().awaitReady();
	}
}
