package org.abitoff.discord.sushirole.utils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;

public final class IOUtils
{
	public static final <T> T readJSON(Gson gson, File f, Class<T> clazz) throws IOException
	{
		try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE))
		{
			try (Reader r = Channels.newReader(channel, "UTF-8"))
			{
				return gson.fromJson(r, clazz);
			}
		}
	}
}
