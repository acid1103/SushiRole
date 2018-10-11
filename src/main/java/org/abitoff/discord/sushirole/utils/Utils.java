package org.abitoff.discord.sushirole.utils;

import java.lang.reflect.Array;

public class Utils
{
	private Utils()
	{
	}

	public static byte[] longToBytes(long l)
	{
		byte[] result = new byte[8];
		for (int i = 7; i >= 0; i--)
		{
			result[i] = (byte) (l & 0xFF);
			l >>= 8;
		}
		return result;
	}

	public static long bytesToLong(byte[] b)
	{
		long result = 0;
		for (int i = 0; i < 8; i++)
		{
			result <<= 8;
			result |= (b[i] & 0xFF);
		}
		return result;
	}

	public static byte[] merge(byte[]...bytes)
	{
		int length = 0;
		for (byte[] b: bytes)
			if (b != null)
				length += b.length;

		byte[] merge = new byte[length];

		int offset = 0;
		for (byte[] b: bytes)
			if (b != null)
			{
				System.arraycopy(b, 0, merge, offset, b.length);
				offset += b.length;
			}

		return merge;
	}

}
