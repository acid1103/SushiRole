package org.abitoff.discord.sushirole.utils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * An object which contains a set of {@link ReentrantLock locks} keyed by a set of {@link Enum enums}. Used for managing a large
 * set of locks for more complex concurrent operations.<br />
 * Note: In this documentation, the words {@code lock} and {@code key} are used frequently. Unless otherwise stated, {@code lock}
 * is referring to a {@link java.util.concurrent.locks.Lock lock} in the concurrent sense, and {@code key} is referring to a
 * {@link java.util.Map key} in the Map data type sense.
 * 
 * @author Steven Fontaine
 */
public class LockManager<T extends Enum<T>>
{
	/** Map of locks keyed by {@code Enum<T>} */
	private final Map<T,ReentrantLock> locks = new ConcurrentHashMap<T,ReentrantLock>();

	/**
	 * Creates an empty LockManager of type {@code T}
	 */
	public LockManager(Class<T> clazz)
	{
	}

	/**
	 * Creates a LockManager and populates it with new locks with the given list of keys
	 */
	public LockManager(EnumSet<T> enms)
	{
		enms.forEach(enm -> locks.put(enm, new ReentrantLock()));
	}

	/**
	 * Adds and returns a lock with the given key to this manager, or returns the lock with the given key if one already exists.
	 * 
	 * @param enm
	 *            the key associated with the lock
	 * @return the lock associated with the given key.
	 */
	public ReentrantLock addLock(T enm)
	{
		ReentrantLock lock = locks.get(enm);
		// check rather than locks.putIfAbsent to prevent instantiating a new ReentrantLock if not necessary
		if (lock == null)
			lock = locks.put(enm, new ReentrantLock());
		return lock;
	}

	/**
	 * Retrieves the lock with the given key from this manager.
	 * 
	 * @param enm
	 *            the lock to retrieve
	 * @return the retrieved lock
	 */
	public ReentrantLock getLock(T enm)
	{
		return locks.get(enm);
	}

	/**
	 * Checks if this manager currently holds a lock with the given key.
	 * 
	 * @param enm
	 *            the key of the lock to check for
	 * @return true if there is a lock in the manager with the associated key, false otherwise
	 */
	public boolean lockExists(T enm)
	{
		return locks.containsKey(enm);
	}

	/**
	 * If this manager contains {@code lock}, returns the key associated with it. Otherwise, returns {@code null}.
	 * 
	 * @param lock
	 *            the lock whose name to retrieve
	 * @return {@code lock}'s key
	 */
	public T getKey(ReentrantLock lock)
	{
		for (Entry<T,ReentrantLock> entry: locks.entrySet())
			if (entry.getValue() == lock)
				return entry.getKey();
		return null;
	}

	/**
	 * Performs {@code supplier} under lock of {@code enm}
	 * 
	 * @return the result of {@code supplier}
	 */
	public <U> U synchronize(Supplier<U> supplier, T enm)
	{
		ReentrantLock lock = getLock(enm);
		Objects.requireNonNull(lock, enm + " is not a lock managed by this LockManager!");
		lock.lock();
		U ret;
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
	 * Runs {@code runnable} under lock of {@code enm}
	 */
	public void synchronize(Runnable runnable, T enm)
	{
		synchronize(() ->
		{
			runnable.run();
			return null;
		}, enm);
	}

	/**
	 * Perform {@code supplier} under lock of all locks with the given keys.
	 * 
	 * @return the result of {@code supplier}
	 */
	public <U> U synchronize(Supplier<U> supplier, EnumSet<T> enms)
	{
		List<ReentrantLock> locks = new ArrayList<ReentrantLock>(enms.size());
		U ret;

		try
		{
			// attempt to do a single pass rather than one verification pass and one locking pass to improve performance. we have
			// to be careful to unlock any locks which were successfully locked, however.
			for (T name: enms)
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
	 * Runs {@code runnable} under lock of all locks with the given keys.
	 */
	public void synchronize(Runnable runnable, EnumSet<T> enms)
	{
		synchronize(() ->
		{
			runnable.run();
			return null;
		}, enms);
	}
}
