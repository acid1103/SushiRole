package org.abitoff.discord.sushirole;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import javax.security.auth.login.LoginException;

import org.abitoff.discord.sushirole.config.BotConfig;
import org.json.JSONPointer;

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
		File keys = new File("./sushiroleconfig/bot.json");

		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();

		BotConfig k;
		try (FileReader fr = new FileReader(keys))
		{
			k = gson.fromJson(fr, BotConfig.class);
		}

		JDA jda = new JDABuilder(AccountType.BOT).setToken(k.discord_bot.get("token"))
				.addEventListener(new EventListener()
				{
					public void onEvent(Event event)
					{
						if (event instanceof ReadyEvent)
							System.out.println("API is ready!");
					}
				}).build().awaitReady();

	}
}
