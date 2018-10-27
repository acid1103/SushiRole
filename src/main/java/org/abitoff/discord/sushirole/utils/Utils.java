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

	public static String join(String[] strings, String joinString)
	{
		if (strings == null || strings.length == 0)
			return "";
		int jslen = joinString.length();
		// -1 to subtract the final new line
		int length = -jslen;
		for (String s: strings)
		{
			// +1 for new lines
			length += s.length() + jslen;
		}
		char[] join = new char[length];
		int offset = 0;
		for (int i = 0; i < strings.length - 1; i++)
		{
			String s = strings[i];
			s.getChars(0, s.length(), join, offset);
			offset += s.length();
			joinString.getChars(0, jslen, join, offset);
			offset += joinString.length();
		}
		String s = strings[strings.length - 1];
		s.getChars(0, s.length(), join, offset);
		return new String(join);
	}

}
