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

	public static int divideCeil(int n, int d)
	{
		int q = n / d;
		if (q * d != n)
			q++;
		return q;
	}

	public static long divideCeil(long n, long d)
	{
		long q = n / d;
		if (q * d != n)
			q++;
		return q;
	}

}
