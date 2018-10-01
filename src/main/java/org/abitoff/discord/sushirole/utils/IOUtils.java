package org.abitoff.discord.sushirole.utils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * A static utility class for convenience IO functions
 * 
 * @author Steven Fontaine
 */
public final class IOUtils
{
	/**
	 * Reads JSON data from {@code file}, and constructs an instance of the type provided by {@code clazz}, filling that instance
	 * with the data parsed from {@code file}.
	 * 
	 * @param gson
	 *            The {@link Gson} instance used to parse the contents of {@code file}
	 * @param file
	 *            The file from which to read the JSON data
	 * @param clazz
	 *            The class type to fill with the JSON data
	 * @param <T>
	 *            The type of {@code clazz}
	 * @return The instance of {@code T}, filled with the data parsed from {@code file}
	 * @throws IOException
	 *             if an error is encountered while trying to access {@code file} or the data therein
	 * @throws JsonParseException
	 *             if an error is encountered while trying to parse the JSON data contained within {@code file}, or if the data
	 *             can't be matched to the {@code T} class
	 * @see Gson#fromJson(Reader, Class)
	 */
	public static final <T> T readJSON(Gson gson, File file, Class<T> clazz) throws IOException,JsonParseException
	{
		try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
				Reader r = Channels.newReader(channel, "UTF-8"))
		{
			return gson.fromJson(r, clazz);
		}
	}
}
