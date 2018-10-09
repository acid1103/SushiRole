package org.abitoff.discord.sushirole.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;

/**
 * A static utility class for convenience IO functions
 * 
 * @author Steven Fontaine
 */
public final class IOUtils
{
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	// private constructor, as this class is never meant to be constructed.
	private IOUtils()
	{
	}

	/**
	 * Reads the entirety of a given {@link File file}, returning the result as a String. This method uses UTF-8 encoding to
	 * convert the data to text.
	 * 
	 * @param f
	 *            The file to read
	 * @return The contents of {@code f}, converted to a String
	 * @throws InvalidPathException
	 *             if {@code f} is unable to be converted to a path. (See {@link File#toPath()})
	 * @throws IOException
	 *             if an I/O error occurs while reading the data from the file
	 * @throws OutOfMemoryError
	 *             if a buffer large enough to read {@code f} is unable to be allocated due to insufficient available memory
	 * @throws SecurityException
	 *             if read access to {@code f} is not permitted
	 */
	public static final String readAll(File f) throws InvalidPathException,IOException,OutOfMemoryError,SecurityException
	{
		return readAll(f, UTF_8);
	}

	/**
	 * Reads the entirety of a given {@link File file}, and returns the result as a String, using the given {@link Charset
	 * charset} to convert the data to text.
	 * 
	 * @param f
	 *            The file to read
	 * @param charset
	 * @return The contents of {@code f}, converted to a String
	 * @throws InvalidPathException
	 *             if {@code f} is unable to be converted to a path. (See {@link File#toPath()})
	 * @throws IOException
	 *             if an I/O error occurs while reading the data from the file
	 * @throws OutOfMemoryError
	 *             if a buffer large enough to read {@code f} is unable to be allocated due to insufficient available memory
	 * @throws SecurityException
	 *             if read access to {@code f} is not permitted
	 */
	public static final String readAll(File f, Charset charset)
			throws InvalidPathException,IOException,OutOfMemoryError,SecurityException
	{
		// piggyback off of nio libraries
		return new String(Files.readAllBytes(f.toPath()), charset);
	}
}
