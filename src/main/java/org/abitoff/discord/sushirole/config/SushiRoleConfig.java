package org.abitoff.discord.sushirole.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;

import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.utils.IOUtils;
import org.abitoff.discord.sushirole.utils.JSONUtils;
import org.json.JSONException;

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
	public ErrorReportingConfig discord_dev_guild;

	public static class PastebinConfig
	{
		public String dev_key;
		public String username;
		public String password;
	}

	public static class BotConfig
	{
		public long client_id;
		public String client_secret;
		public String token;
		public long permissions;
	}

	public static class ErrorReportingConfig
	{
		public long guild_id;
		public long error_channel_id;
		public long development_team_role_id;
	}

	public static SushiRoleConfig create(File f) throws FatalException
	{
		try
		{
			String rawJSONStr = IOUtils.readAll(f);
			return JSONUtils.populateObject(new org.json.JSONObject(rawJSONStr), SushiRoleConfig.class, false);
		} catch (InvalidPathException e)
		{
			throw new FatalException(e);
		} catch (IOException e)
		{
			throw new FatalException(e);
		} catch (OutOfMemoryError e)
		{
			throw new FatalException(e);
		} catch (SecurityException e)
		{
			throw new FatalException(e);
		} catch (JSONException e)
		{
			throw new FatalException(e);
		} catch (Exception e)
		{
			throw new FatalException(String.format("Error encountered trying to read config file. Ensure a file exists at %s. "
					+ "Ensure the file has read access. Ensure the file is valid JSON data. Ensure the file has the required "
					+ "structure.", f.getAbsolutePath()), e);
		}
	}
}
