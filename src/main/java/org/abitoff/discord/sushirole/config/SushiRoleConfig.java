package org.abitoff.discord.sushirole.config;

public class SushiRoleConfig
{
	public PastebinConfig pastebin;
	public BotConfig discord_bot;
	public BotConfig discord_bot_dev;

	public static class PastebinConfig
	{
		public String dev_key;
		public String username;
		public String password;
	}

	public static class BotConfig
	{
		public String client_id;
		public String client_secret;
		public String token;
		public long permissions;
	}
}
