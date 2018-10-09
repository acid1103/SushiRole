package org.abitoff.discord.sushirole.pastebin;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.github.kennedyoliveira.pastebin4j.AccountCredentials;
import com.github.kennedyoliveira.pastebin4j.api.PasteBinApi;
import com.github.kennedyoliveira.pastebin4j.api.PasteBinApiImpl;

/**
 * Implementation of the {@link com.github.kennedyoliveira.pastebin4j.api.PasteBinApi Pastebin API} that is concurrent for the
 * methods required by {@link org.abitoff.discord.sushirole.SushiRole SushiRole}.
 * 
 * @author Steven Fontaine
 */
public final class ConcurrentPastebinApi extends PasteBinApiImpl
{
	/** The default {@link PasteBinApi API} to use for all concurrent uses of the API */
	public static final PasteBinApi API = new ConcurrentPastebinApi();

	/** lock used to synchronize updating the user session key */
	private static final Object userSessionKeyLock = new Object();

	/**
	 * Creates a new instance of the {@link ConcurrentPastebinApi Concurrent Pastebin API}.
	 */
	private ConcurrentPastebinApi()
	{
		super();
	}

	@Override
	public void updateUserSessionKey(@NotNull AccountCredentials accountCredentials)
	{
		// if we don't have a user session key, we need to get one.
		if (!accountCredentials.getUserSessionKey().isPresent())
		{
			// begin the synchronized block, since fetching the key twice simultaneously will very likely cause issues.
			synchronized (userSessionKeyLock)
			{
				// now that we have the lock, potentially after having execution blocked while another thread fetched a key,
				// double check that a key wasn't fetched in the time we spent waiting for the lock.
				if (!accountCredentials.getUserSessionKey().isPresent())
				{
					// no other thread has gotten a key, so we go ahead and fetch one.
					accountCredentials.setUserKey(Optional.ofNullable(fetchUserKey(accountCredentials)));
				}
			}
		}
	}
}
