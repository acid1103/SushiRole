package org.abitoff.discord.sushirole.config;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public class SushiRoleConfig
{
	public PastebinConfig pastebin;
	public BotConfig discord_bot;
	public BotConfig discord_bot_dev;
	public DiscordErrorReportingInfo discord_error_reporting;

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

	public static class DiscordErrorReportingInfo
	{
		public String channel_id;
	}
}
