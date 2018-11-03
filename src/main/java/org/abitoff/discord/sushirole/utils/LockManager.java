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
 * <p>
 * A class which contains a map of {@link ReentrantLock locks} <a href="#lockAndKey">keyed</a> by a set of {@link Enum enums}.
 * Used for managing a large set of locks for more complex concurrent operations.
 * </p>
 * <p id="lockAndKey">
 * Note: In this documentation, the words {@code lock} and {@code key} are used frequently. Unless otherwise stated, {@code lock}
 * is referring to a {@link java.util.concurrent.locks.Lock lock} in the concurrent sense, and {@code key} is referring to a
 * {@link java.util.Map key} in the map data type sense.
 * </p>
 *
 * @param <T>
 *            the Enum class used to key the map of locks
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
		Objects.requireNonNull(clazz, "The Class must not be null!");
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
			if (key == null)
				continue;// ignore null keys. subject to change
			ReentrantLock lock = requireMappedKey(key);
			locks.add(lock);
		}

		try
		{
			for (ReentrantLock lock: locks)
				lock.lock();
		} catch (Throwable t)
		{
			// should never happen, but just in case it does, we need to unlock these.
			// we catch anything that might happen here and suppress it with our original Throwable to ensure that everything gets
			// thrown.
			try
			{
				unlockAll(keys);
			} catch (UnlockException t2)
			{
				// catch any throwable thrown while unlocking the keys and set it as being suppressed by t.
				t.addSuppressed(t2);
			}

			throw t;
		}
	}

	/**
	 * TODO<br />
	 * NOTE: In the event that multiple exceptions are encountered during execution, all exceptions are suppressed by an
	 * {@link UnlockException}. It's the job of the caller to handle <strong>every</strong> exception found in
	 * {@link UnlockException#getSuppressed()}.
	 * 
	 * @param keys
	 */
	public void unlockAll(@Nonnull EnumSet<T> keys) throws UnlockException
	{
		Objects.requireNonNull(keys, "The EnumSet must not be null!");

		// we're very aggressive with unlocking because we don't want to block other threads indefinitely because of a simple
		// uncaught exception
		UnlockException suppressed = new UnlockException(
				"Exceptions encountered and suppressed while unlocking the following keys: " + keys.toString());
		for (T key: keys)
		{
			try
			{
				if (key == null)
					continue;// ignore null keys. subject to change
				ReentrantLock lock = requireMappedKey(key);
				lock.unlock();
			} catch (Throwable t)
			{
				/*
				 * if key is null or key doesn't map to a lock or lock.unlock() throws an IllegalMonitorStateException, we
				 * absolutely must catch these and continue unlocking, but we should attempt to unlock all locks, even in the case
				 * of catastrophic failure such as an Error being thrown. we throw these eventually in the by way of
				 * "UnlockException suppressed", so each throwable gets propagated out in the form of suppressed throwables inside
				 * an UnlockException. If failure is so catastrophic that the code in this catch block fails to execute, the
				 * application is bound to crash, so no special care is taken to handle such a case.
				 */

				suppressed.addSuppressed(t);
			}
		}

		// if we encountered any exceptions, throw the UnlockException
		if (suppressed.getSuppressed().length != 0)
			throw suppressed;
	}

	/**
	 * TODO Runs {@code runnable} under lock of {@code key}<br />
	 * NOTE: Documented behavior with a null key is explicitly undefined. The current implementation simply executes
	 * {@code runnable}, however it is highly discouraged to pass a null key, and this behavior is likely to change without
	 * documentation of such change.
	 */
	public <U> U synchronize(@Nonnull Supplier<U> supplier, T key)
	{
		Objects.requireNonNull(supplier, "The Supplier must not be null!");

		if (key == null)
			return supplier.get();

		ReentrantLock lock = requireMappedKey(key);
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
	 * TODO Runs {@code runnable} under lock of {@code key}<br />
	 * NOTE: Documented behavior with a null key is explicitly undefined. The current implementation simply executes
	 * {@code runnable}, however it is highly discouraged to pass a null key, and this behavior is likely to change without
	 * documentation of such change.
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
	 * TODO Perform {@code supplier} under lock of all locks with the given keys.<br />
	 * NOTE: Documented behavior with a null key set is explicitly undefined. The current implementation simply executes
	 * {@code supplier}, however it is highly discouraged to pass a null key set, and this behavior is likely to change without
	 * documentation of such change.
	 * 
	 * @return the result of {@code supplier}
	 */
	public <U> U synchronize(@Nonnull Supplier<U> supplier, EnumSet<T> keys)
	{
		Objects.requireNonNull(supplier, "The Supplier must not be null!");

		if (keys == null || keys.size() == 0)
			return supplier.get();

		U ret = null;
		Throwable suppressor = null;
		try
		{
			lockAll(keys);
			ret = supplier.get();
		} catch (Throwable t)
		{
			suppressor = t;
		} finally
		{
			try
			{
				unlockAll(keys);
			} catch (Throwable t)
			{
				if (suppressor != null)
					suppressor.addSuppressed(t);
				else
					throw t;
			}
			if (suppressor != null)
				ThrowableUtils.throwUnknownThrowable(suppressor);
		}

		return ret;
	}

	/**
	 * TODO Runs {@code runnable} under lock of all locks with the given keys.<br />
	 * NOTE: Documented behavior with a null key set is explicitly undefined. The current implementation simply executes
	 * {@code runnable}, however it is highly discouraged to pass a null key set, and this behavior is likely to change without
	 * documentation of such change.
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
	private ReentrantLock requireMappedKey(T key)
	{
		ReentrantLock lock = locks.get(key);
		return Objects.requireNonNull(lock, "Lock with the key \"" + key.name() + "\" is not managed by this LockManager!");
	}

	public static final class UnlockException extends RuntimeException
	{
		private static final long serialVersionUID = -1264609241964996937L;

		private UnlockException()
		{
			super();
		}

		private UnlockException(String message)
		{
			super(message);
		}

		private UnlockException(Throwable cause)
		{
			super(cause);
		}

		private UnlockException(String message, Throwable cause)
		{
			super(message, cause);
		}

		// don't support this constructor, as disabling suppression is potentially catastrophic.
		// private UnlockException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
		// {
		// super(message, cause, enableSuppression, writableStackTrace);
		// }
	}
}
