package org.abitoff.discord.sushirole.utils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;

/**
 * TODO
 * 
 * @author Steven Fontaine
 */
public final class IOUtils
{
	/**
	 * TODO
	 * 
	 * @param gson
	 * @param f
	 * @param clazz
	 * @return
	 * @throws IOException
	 */
	public static final <T> T readJSON(Gson gson, File f, Class<T> clazz) throws IOException
	{
		try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
				Reader r = Channels.newReader(channel, "UTF-8"))
		{
			return gson.fromJson(r, clazz);
		}
	}
}
