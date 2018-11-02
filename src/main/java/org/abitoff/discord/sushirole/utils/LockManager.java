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

import javax.annotation.Nonnull;

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
	public LockManager(@Nonnull Class<T> clazz)
	{
		Objects.requireNonNull(clazz, "clazz must not be null!");
	}

	/**
	 * Creates a LockManager and populates it with new locks with the given list of keys
	 */
	public LockManager(@Nonnull EnumSet<T> keys)
	{
		Objects.requireNonNull(keys, "The EnumSet must not be null!");
		keys.forEach(key -> locks.put(key, new ReentrantLock()));
	}

	/**
	 * Adds and returns a lock with the given key to this manager, or returns the lock with the given key if one already exists.
	 * 
	 * @param key
	 *            the key associated with the lock
	 * @return the lock associated with the given key.
	 */
	public ReentrantLock addLock(@Nonnull T key)
	{
		Objects.requireNonNull(key, "LockManager does not accept null keys!");

		ReentrantLock lock = locks.get(key);
		// check rather than locks.putIfAbsent to prevent instantiating a new ReentrantLock if not necessary
		if (lock == null)
			lock = locks.put(key, new ReentrantLock());
		return lock;
	}

	/**
	 * Retrieves the lock with the given key from this manager.
	 * 
	 * @param key
	 *            the lock to retrieve
	 * @return the retrieved lock
	 */
	public ReentrantLock getLock(T key)
	{
		if (key == null)
			return null;

		return locks.get(key);
	}

	/**
	 * Checks if this manager currently holds a lock with the given key.
	 * 
	 * @param key
	 *            the key of the lock to check for
	 * @return true if there is a lock in the manager with the associated key, false otherwise
	 */
	public boolean lockExists(T key)
	{
		if (key == null)
			return false;

		return locks.containsKey(key);
	}

	/**
	 * If this manager contains {@code lock}, returns the key associated with it. Otherwise, returns {@code null}.
	 * 
	 * @param lock
	 *            the lock whose key to retrieve
	 * @return {@code lock}'s key
	 */
	public T getKey(ReentrantLock lock)
	{
		if (lock == null)
			return null;

		for (Entry<T,ReentrantLock> entry: locks.entrySet())
			if (entry.getValue() == lock)
				return entry.getKey();
		return null;
	}

	/**
	 * TODO
	 * 
	 * @param keys
	 */
	public void lockAll(@Nonnull EnumSet<T> keys)
	{
		Objects.requireNonNull(keys, "The EnumSet must not be null!");

		// we verify the list first, so that we don't have to waste time unlocking any locks that we locked prior to reaching an
		// invalid key.
		List<ReentrantLock> locks = new ArrayList<ReentrantLock>(keys.size());
		for (T key: keys)
		{
			ReentrantLock lock = requireNonNullKey(key);
			locks.add(lock);
		}

		try
		{
			for (ReentrantLock lock: locks)
				lock.lock();
		} catch (Throwable t)
		{
			// should never happen, but just in case it does and is somehow handled along the way, we need to unlock these.
			// we catch anything that might happen here and suppress it with our original Throwable to ensure that everything gets
			// thrown.
			Throwable suppressed = null;
			try
			{
				unlockAll(keys);
			} catch (Throwable t2)
			{
				suppressed = t2;
			}

			if (suppressed != null)
				t.addSuppressed(suppressed);

			throw t;
		}
	}

	/**
	 * TODO
	 * 
	 * @param keys
	 */
	public void unlockAll(@Nonnull EnumSet<T> keys)
	{
		Objects.requireNonNull(keys, "The EnumSet must not be null!");

		// we're very aggressive with unlocking because we don't want to block other threads indefinitely because of a simple
		// uncaught exception
		Throwable suppressed = null;
		for (T key: keys)
		{
			try
			{
				ReentrantLock lock = requireNonNullKey(key);
				lock.unlock();
			} catch (Throwable t)
			{
				if (suppressed != null)
					t.addSuppressed(suppressed);
				suppressed = t;
			}
		}

		if (suppressed != null)
			throwSuppressedThrowable(suppressed);
	}

	/**
	 * Performs {@code supplier} under lock of {@code key}
	 * 
	 * @return the result of {@code supplier}
	 */
	public <U> U synchronize(@Nonnull Supplier<U> supplier, T key)
	{
		Objects.requireNonNull(supplier, "The Supplier must not be null!");

		if (key == null)
			return supplier.get();

		ReentrantLock lock = requireNonNullKey(key);
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
	 * Runs {@code runnable} under lock of {@code key}
	 */
	public void synchronize(@Nonnull Runnable runnable, T key)
	{
		Objects.requireNonNull(runnable, "The Runnable must not be null!");

		if (key == null)
			runnable.run();
		else
			synchronize(() ->
			{
				runnable.run();
				return null;
			}, key);
	}

	/**
	 * Perform {@code supplier} under lock of all locks with the given keys.
	 * 
	 * @return the result of {@code supplier}
	 */
	public <U> U synchronize(@Nonnull Supplier<U> supplier, EnumSet<T> keys)
	{
		Objects.requireNonNull(supplier, "The Supplier must not be null!");

		if (keys == null || keys.size() == 0)
			return supplier.get();

		U ret = null;
		Throwable suppressed = null;
		try
		{
			lockAll(keys);
			ret = supplier.get();
		} catch (Throwable t)
		{
			suppressed = t;
		} finally
		{
			try
			{
				unlockAll(keys);
			} catch (Throwable t)
			{
				if (suppressed != null)
					t.addSuppressed(suppressed);
				throw t;
			}

			if (suppressed != null)
				throwSuppressedThrowable(suppressed);
		}

		return ret;
	}

	/**
	 * Runs {@code runnable} under lock of all locks with the given keys.
	 */
	public void synchronize(@Nonnull Runnable runnable, EnumSet<T> keys)
	{
		Objects.requireNonNull(runnable, "The Runnable must not be null!");

		if (keys == null || keys.size() == 0)
			runnable.run();
		else
			synchronize(() ->
			{
				runnable.run();
				return null;
			}, keys);
	}

	/**
	 * TODO
	 * 
	 * @param key
	 * @return
	 */
	private ReentrantLock requireNonNullKey(T key)
	{
		ReentrantLock lock = this.locks.get(key);
		return Objects.requireNonNull(lock, "Lock with the key \"" + key.name() + "\" is not managed by this LockManager!");
	}

	/**
	 * TODO
	 * 
	 * @param suppressed
	 */
	private void throwSuppressedThrowable(Throwable suppressed)
	{
		if (suppressed instanceof RuntimeException)
			throw (RuntimeException) suppressed;
		else if (suppressed instanceof Error)
			throw (Error) suppressed;
		else
			throw new RuntimeException(suppressed);
	}
}
