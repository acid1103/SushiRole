package org.abitoff.discord.sushirole;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.abitoff.discord.sushirole.utils.Utils;
import org.junit.Test;

public class SushiRoleTest
{
	// Utils.java testing --------------------------------------------------------------------------------------------------------
	@Test
	public void UtilsMergeNull()
	{
		byte[] merged = Utils.merge((byte[][]) null);
		byte[] expected = null;
		assertArrayEquals(merged, expected);
	}

	@Test
	public void UtilsMergeNullEmptyAndPopulated()
	{
		byte[] merged = Utils.merge(null, new byte[] {1}, null, new byte[] {1, 2}, new byte[0], new byte[] {1, 2, 3}, null);
		byte[] expected = new byte[] {1, 1, 2, 1, 2, 3};
		assertArrayEquals(merged, expected);
	}

	@Test
	public void UtilsDivideCeilIntInt()
	{
		assertEquals(Utils.divideCeil(5, 2), 3);
		assertEquals(Utils.divideCeil(-5, 2), -3);
		assertEquals(Utils.divideCeil(5, -2), -3);
		assertEquals(Utils.divideCeil(-5, -2), 3);
	}

	@Test
	public void UtilsDivideCeilLongLong()
	{
		assertEquals(Utils.divideCeil(5l, 2l), 3);
		assertEquals(Utils.divideCeil(-5l, 2l), -3);
		assertEquals(Utils.divideCeil(5l, -2l), -3);
		assertEquals(Utils.divideCeil(-5l, -2l), 3);
	}

	@Test
	public void UtilsJoinNull()
	{
		String merged = Utils.join((String[]) null, null);
		String[] expected = null;
		assertEquals(merged, expected);
	}

	@Test
	public void UtilsJoinNullEmptyAndPopulated()
	{
		String merged = Utils.join(new String[] {null, "abc", "", null, "def", null, "hij"}, "\r\n");
		String expected = "abc\r\n\r\ndef\r\nhij";
		assertEquals(merged, expected);
	}

	@Test
	public void UtilsJoinNullEmptyAndPopulatedNullJoinString()
	{
		String merged = Utils.join(new String[] {null, "abc", "", null, "def", null, "hij"}, null);
		String expected = "abcdefhij";
		assertEquals(merged, expected);
	}
}
