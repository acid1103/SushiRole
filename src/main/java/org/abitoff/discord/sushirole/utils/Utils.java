package org.abitoff.discord.sushirole.utils;

public class Utils
{
	private Utils()
	{
	}

	public static byte[] merge(byte[]...bytes)
	{
		if (bytes == null)
			return null;

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
			q += Integer.signum(q);
		return q;
	}

	public static long divideCeil(long n, long d)
	{
		long q = n / d;
		if (q * d != n)
			q += Long.signum(q);
		return q;
	}

	public static String join(String[] strings, String joinString)
	{
		if (strings == null)
			return null;
		if (strings.length == 0)
			return "";
		if (joinString == null)
			joinString = "";
		int jslen = joinString.length();
		int length = -jslen;
		for (String s: strings)
		{
			if (s != null)
				length += s.length() + jslen;
		}
		char[] join = new char[length];
		int offset = 0;
		for (int i = 0; i < strings.length - 1; i++)
		{
			String s = strings[i];
			if (s != null)
			{
				s.getChars(0, s.length(), join, offset);
				offset += s.length();
				joinString.getChars(0, jslen, join, offset);
				offset += joinString.length();
			}
		}
		String s = strings[strings.length - 1];
		s.getChars(0, s.length(), join, offset);
		return new String(join);
	}

}
