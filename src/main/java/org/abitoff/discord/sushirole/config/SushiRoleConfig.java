package org.abitoff.discord.sushirole.config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;

import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.abitoff.discord.sushirole.utils.JSONUtils;
import org.json.JSONException;
import org.json.JSONObject;

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
		String rawJSONStr;
		try
		{
			try
			{
				rawJSONStr = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			} catch (InvalidPathException e)
			{
				// TODO
				throw e;
			} catch (OutOfMemoryError e)
			{
				// TODO
				throw e;
			} catch (SecurityException e)
			{
				// TODO
				throw e;
			} catch (IOException e)
			{
				// TODO
				throw e;
			}
		} catch (Exception e)
		{
			throw new FatalException(String.format(
					"Error encountered trying to read config file. Ensure a file exists at %s. "
							+ "Ensure the file has read access. Ensure the file isn't too large to be read into memory.",
					f.getAbsolutePath()), e);
		}
		try
		{
			try
			{
				return JSONUtils.populateObject(new JSONObject(rawJSONStr), SushiRoleConfig.class, false);
			} catch (InstantiationException e)
			{
				// TODO
				// shouldn't happen if the class structure is set up correctly
				throw e;
			} catch (IllegalAccessException e)
			{
				// TODO
				// shouldn't happen if the class structure is set up correctly
				throw e;
			} catch (IllegalArgumentException e)
			{
				// TODO
				// shouldn't happen if the class structure is set up correctly
				throw e;
			} catch (InvocationTargetException e)
			{
				// TODO
				// shouldn't happen if the class structure is set up correctly
				throw e;
			} catch (NoSuchMethodException e)
			{
				// TODO
				// shouldn't happen if the class structure is set up correctly
				throw e;
			} catch (SecurityException e)
			{
				// TODO
				// shouldn't happen if the class structure is set up correctly
				throw e;
			} catch (JSONException e)
			{
				// TODO
				throw e;
			}
		} catch (Exception e)
		{
			throw new FatalException(String.format("Error encountered trying to read config file.  Ensure the file is valid JSON "
					+ "data. Ensure the file has the required structure.", f.getAbsolutePath()), e);
		}
	}
}
