package org.abitoff.discord.sushirole.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * An object which contains {@link ReentrantLock locks} with associated names. Used for managing a large set of locks for more
 * complex concurrent operations.
 * 
 * @author Steven Fontaine
 */
public class LockManager
{
	/** Map of locks by name */
	private final Map<String,ReentrantLock> locks = new ConcurrentHashMap<String,ReentrantLock>();

	/**
	 * Creates an empty LockManager
	 */
	public LockManager()
	{
	}

	/**
	 * Creates a LockManager and populates it with new locks with the given list of names
	 */
	public LockManager(String...names)
	{
		if (names != null && names.length > 0)
			for (String name: names)
				addLock(name);
	}

	/**
	 * Adds and returns a lock with the given name to this manager, or returns the lock with the given name if one already
	 * exists.<br />
	 * <strong>{@code lockName} must not be {@code null}!</strong>
	 * 
	 * @param lockName
	 *            the name associated with the lock
	 * @return the lock associated with the given name.
	 */
	public ReentrantLock addLock(String lockName)
	{
		Objects.requireNonNull(lockName, "lockName must not be null!");
		return locks.putIfAbsent(lockName, new ReentrantLock(true));
	}

	/**
	 * Retrieves the lock with the given name from this manager.
	 * 
	 * @param lockName
	 *            the lock to retrieve
	 * @return the retrieved lock
	 */
	public ReentrantLock getLock(String lockName)
	{
		return locks.get(lockName);
	}

	/**
	 * Checks if this manager currently holds a lock with the given name.
	 * 
	 * @param lockName
	 *            the name of the lock to check for
	 * @return true if there is a lock in the manager with the associated name, false otherwise
	 */
	public boolean lockExists(String lockName)
	{
		return locks.containsKey(lockName);
	}

	/**
	 * If this manager contains {@code lock}, returns the name associated with it. Otherwise, returns {@code null}.
	 * 
	 * @param lock
	 *            the lock whose name to retrieve
	 * @return {@code lock}'s name
	 */
	public String getName(ReentrantLock lock)
	{
		for (Entry<String,ReentrantLock> entry: locks.entrySet())
			if (entry.getValue() == lock)
				return entry.getKey();
		return null;
	}

	/**
	 * Performs {@code supplier} under lock of {@code lockName}
	 * 
	 * @return the result of {@code supplier}
	 */
	public <T> T synchronize(Supplier<T> supplier, String lockName)
	{
		ReentrantLock lock = getLock(lockName);
		Objects.requireNonNull(lock, lockName + " is not a lock managed by this LockManager!");
		lock.lock();
		T ret;
		try
		{
			ret = supplier.get();
		} finally
		{
			lock.unlock();
		}
		return ret;
	}

	/**
	 * Runs {@code runnable} under lock of {@code lockName}
	 */
	public void synchronize(Runnable runnable, String name)
	{
		synchronize(() ->
		{
			runnable.run();
			return null;
		}, name);
	}

	/**
	 * Perform {@code supplier} under lock of all locks with the given names.
	 * 
	 * @return the result of {@code supplier}
	 */
	public <T> T synchronize(Supplier<T> supplier, String...names)
	{
		List<ReentrantLock> locks = new ArrayList<ReentrantLock>(names.length);
		T ret;

		try
		{
			// attempt to do a single pass rather than one verification pass and one locking pass to improve performance. we have
			// to be careful to unlock any locks which were successfully locked, however.
			for (String name: names)
			{
				ReentrantLock lock = getLock(name);
				Objects.requireNonNull(lock, name + " is not a lock managed by this LockManager!");
				// add before locking, so that if an error occurs while locking, an unlock is attempted rather than leaving the
				// lock in an unknown state. (I have no evidence this is possible, so some testing might be useful here.)
				locks.add(lock);
				lock.lock();
			}

			ret = supplier.get();
		} finally
		{
			// finally unlock the locks which were successfully locked.
			for (ReentrantLock lock: locks)
			{
				try
				{
					lock.unlock();
				} catch (IllegalMonitorStateException e)
				{
					// this will happen if a lock in the list isn't held by this thread. this might be caused by adding locks to
					// the list before successfully locking them. regardless, it doesn't matter if we don't hold the lock, as
					// we're attempting to release all the locks anyway, so we just ignore it.
				}
			}
		}

		return ret;
	}

	/**
	 * Runs {@code runnable} under lock of all locks with the given names.
	 */
	public void synchronize(Runnable runnable, String...names)
	{
		synchronize(() ->
		{
			runnable.run();
			return null;
		}, names);
	}
}
