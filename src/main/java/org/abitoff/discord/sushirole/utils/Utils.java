package org.abitoff.discord.sushirole.utils;

public class Utils
{
	private Utils()
	{
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
