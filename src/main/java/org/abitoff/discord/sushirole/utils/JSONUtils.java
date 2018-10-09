package org.abitoff.discord.sushirole.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map.Entry;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.abitoff.discord.sushirole.exceptions.FatalException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONUtils
{
	private JSONUtils()
	{
	}

	/**
	 * <p>
	 * Constructs a new instance of {@code clazz} and populates all fields whose name matches a key in {@code json}. This method
	 * populates fields and arrays recursively. Every object which this method attempts to populate MUST have a nullary
	 * constructor, with the following exceptions:
	 * <ul>
	 * <li>{@link Class#isPrimitive() Primitives}</li>
	 * <li>{@link BigInteger BigIntegers}</li>
	 * <li>{@link BigDecimal BigDecimals}</li>
	 * <li>{@link String Strings}</li>
	 * <li>{@link Enum Enums}</li>
	 * </ul>
	 * </p>
	 * <p>
	 * This method can optionally ignore any failure to {@link JSONUtils#unwrap(Object, Class, boolean) unwrap or cast} an object
	 * properly, leaving the field {@code null}, if the {@code populateOptionally} flag is true. This can help when dealing with
	 * malformed JSON. Otherwise, a {@code FatalException} is thrown with a {@code ClassCastException} set as the root cause,
	 * outlining the unwrapping or casting issue.
	 * </p>
	 * 
	 * @param <T>
	 *            the type of object to return
	 * @param json
	 *            the root JSON object whose key/value pairs to use to populate all applicable fields in an instance of
	 *            {@code clazz}
	 * @param clazz
	 *            the class instance containing the type information for {@code T}
	 * @param populateOptionally
	 *            whether or not to throw an exception when an error is encountered while trying to unwrap or cast an object to
	 *            the proper type
	 * @return an instance of {@code T}, populated with the data derived from {@code json}
	 * @throws InstantiationException
	 *             if {@code clazz} is abstract
	 * @throws IllegalAccessException
	 *             if access to any nullary constructor to which a call is being attempted or to any field to which an attempt is
	 *             being made to set a value is not permitted for any reason
	 * @throws IllegalArgumentException
	 *             if {@code clazz} is of an Enum type
	 * @throws InvocationTargetException
	 *             if an exception is encountered while calling any object's nullary constructor
	 * @throws NoSuchMethodException
	 *             if an object is encountered which doesn't have a nullary constructor
	 * @throws SecurityException
	 *             if a security manager is present and is preventing access to a constructor or field
	 * @throws FatalException
	 *             if {@code populateOptionally} is true and an error is encountered while trying to unwrap or cast an object to
	 *             the proper type
	 */
	public static <T> T populateObject(@Nonnull JSONObject json, @Nonnull Class<T> clazz, boolean populateOptionally)
			throws InstantiationException,IllegalAccessException,IllegalArgumentException,InvocationTargetException,
			NoSuchMethodException,SecurityException,FatalException
	{
		// require non-nulls
		Objects.requireNonNull(json, "json");
		Objects.requireNonNull(clazz, "clazz");

		try
		{
			// wrap in try/catch. _populateObject is called recursively, so we wrap it outside of the recursion instead of inside
			// to prevent a massive stack trace
			return _populateObject(json, clazz, populateOptionally);
		} catch (ClassCastException e)
		{
			throw new FatalException("Error while populating JSON file!", e);
		}
	}

	/**
	 * <strong><em>See {@link JSONUtils#populateObject(JSONObject, Class, boolean)}</em></strong>
	 */
	private static <T> T _populateObject(JSONObject json, Class<T> clazz, boolean populateOptionally)
			throws InstantiationException,IllegalAccessException,IllegalArgumentException,InvocationTargetException,
			NoSuchMethodException,SecurityException,FatalException
	{
		// get a new instance of T using the nullary constructor.
		T obj = clazz.getConstructor().newInstance();
		// iterate through each key/value pair in the JSONObject
		for (Entry<String,Object> entry: json.toMap().entrySet())
		{
			// get the key of the current pair
			String key = entry.getKey();

			// ignore null keys
			if (key == null)
			{
				LoggingUtils.warnf("Encountered null key while parsing JSON. Value of null key: %s",
						entry.getValue() != null ? entry.getValue().toString() : "null");
				continue;
			}

			// get the field with the name that matches the key for the current pair. if it doesn't exist, skip this pair
			Field f;
			try
			{
				f = clazz.getField(key);
			} catch (NoSuchFieldException e)
			{
				LoggingUtils.warnf("Unmatched key \"%s\" in class %s when populating from JSON!", key, clazz.getCanonicalName());
				continue;
			}

			// get the unwrapped and casted value from the JSONObject
			Object value = unwrap(entry.getValue(), f.getType(), populateOptionally);
			// set it
			f.set(obj, value);
		}
		return obj;
	}

	/**
	 * <p>
	 * Attempts to unwrap and/or cast {@code value} to type {@code T}. Parameter {@code value} must be one of the following types:
	 * <ul>
	 * <li>{@link Class#isPrimitive() Primitive}</li>
	 * <li>{@link BigInteger}</li>
	 * <li>{@link BigDecimal}</li>
	 * <li>{@link Enum}</li>
	 * <li>{@link String}</li>
	 * <li>{@link JSONObject}</li>
	 * <li>{@link JSONArray}</li>
	 * </ul>
	 * </p>
	 * <p>
	 * Unwraps any {@code JSONObject} or {@code JSONArray} recursively. For {@code JSONObjects}, constructs a new instance of
	 * {@code clazz} and populates valid fields using the {@link JSONObject#toMap() map} contained within the {@code JSONObject}.
	 * (See {@link JSONUtils#populateObject(JSONObject, Class)}.)
	 * </p>
	 * <p>
	 * This method can optionally ignore any casting or unwrapping error encountered due to non-matching types, a failed cast, or
	 * an unsupported conversion, instead returning null (or, in the case of an error while unwrapping recursively, setting the
	 * affected field to null.)
	 * </p>
	 * 
	 * @param <T>
	 *            the type of object to return
	 * @param value
	 *            the object to unwrap and cast
	 * @param type
	 *            the class instance containing the type information for {@code T}
	 * @param optional
	 *            whether or not to ignore casting or unwrapping errors
	 * @return value after unwrapping and casting it to type {@code T}
	 * @throws InstantiationException
	 *             see {@link JSONUtils#populateObject(JSONObject, Class, boolean)}
	 * @throws IllegalAccessException
	 *             see {@link JSONUtils#populateObject(JSONObject, Class, boolean)}
	 * @throws IllegalArgumentException
	 *             see {@link JSONUtils#populateObject(JSONObject, Class, boolean)}
	 * @throws InvocationTargetException
	 *             see {@link JSONUtils#populateObject(JSONObject, Class, boolean)}
	 * @throws NoSuchMethodException
	 *             see {@link JSONUtils#populateObject(JSONObject, Class, boolean)}
	 * @throws SecurityException
	 *             see {@link JSONUtils#populateObject(JSONObject, Class, boolean)}
	 */
	private static <T> T unwrap(Object value, Class<T> type, boolean optional) throws InstantiationException,
			IllegalAccessException,IllegalArgumentException,InvocationTargetException,NoSuchMethodException,SecurityException
	{
		try
		{
			Object wrapped = JSONObject.wrap(value);
			if (type == Boolean.class || type == Boolean.TYPE)
			{
				@SuppressWarnings("unchecked")
				T b = (T) castToBoolean(wrapped);
				return b;
			} else if (type == Byte.class || type == Character.class || type == Short.class || type == Integer.class
					|| type == Long.class || type == Byte.TYPE || type == Character.TYPE || type == Short.TYPE
					|| type == Integer.TYPE || type == Long.TYPE)
			{
				@SuppressWarnings("unchecked")
				T l = (T) castToLong(wrapped);
				return l;
			} else if (type == Float.class || type == Double.class || type == Float.TYPE || type == Double.TYPE)
			{
				@SuppressWarnings("unchecked")
				T d = (T) castToDouble(wrapped);
				return d;
			} else if (type == BigInteger.class)
			{
				@SuppressWarnings("unchecked")
				T bi = (T) castToBigInteger(wrapped);
				return bi;
			} else if (type == BigDecimal.class)
			{
				@SuppressWarnings("unchecked")
				T bd = (T) castToBigDecimal(wrapped);
				return bd;
			} else if (type == String.class)
			{
				@SuppressWarnings("unchecked")
				T s = (T) castToString(wrapped);
				return s;
			} else if (type.isEnum())
			{
				@SuppressWarnings({"unchecked", "rawtypes"})
				T e = (T) castToEnum(wrapped, (Class<? extends Enum>) type);
				return e;
			} else if (type.isArray())
			{
				@SuppressWarnings("unchecked")
				T a = (T) castToArray(wrapped, type, optional);
				return a;
			} else
			{
				return castToObject(wrapped, type, optional);
			}
		} catch (ClassCastException e)
		{
			if (optional)
				return null;
			else
				throw e;
		}
	}

	/**
	 * @param value
	 * @return
	 */
	private static Boolean castToBoolean(Object value)
	{
		if (value instanceof Boolean)
		{
			return (boolean) value;
		} else if (value instanceof String)
		{
			String val = (String) value;
			if (val.equalsIgnoreCase("true"))
				return true;
			else if (val.equalsIgnoreCase("false"))
				return false;
		}
		throw new ClassCastException(String.format("The JSON value of %s cannot be cast to a boolean!", value.toString()));
	}

	/**
	 * @param value
	 * @return
	 */
	private static Long castToLong(Object value)
	{
		if (value instanceof Number)
		{
			return ((Number) value).longValue();
		} else if (value instanceof String)
		{
			String val = (String) value;
			try
			{
				return Long.parseLong(val);
			} catch (NumberFormatException e1)
			{
				try
				{
					BigDecimal dec = new BigDecimal(val);
					return dec.longValue();
				} catch (NumberFormatException e2)
				{
					// pass through to the ClassCastException
				}
			}
		}
		throw new ClassCastException(String.format("The JSON value of %s cannot be cast to a long!", value.toString()));
	}

	/**
	 * @param value
	 * @return
	 */
	private static Double castToDouble(Object value)
	{
		if (value instanceof Number)
		{
			return ((Number) value).doubleValue();
		} else if (value instanceof String)
		{
			String val = (String) value;
			try
			{
				return Double.parseDouble(val);
			} catch (NumberFormatException e1)
			{
				try
				{
					BigDecimal dec = new BigDecimal(val);
					return dec.doubleValue();
				} catch (NumberFormatException e2)
				{
					// pass through to the ClassCastException
				}
			}
		}
		throw new ClassCastException(String.format("The JSON value of %s cannot be cast to a double!", value.toString()));
	}

	/**
	 * @param value
	 * @return
	 */
	private static BigInteger castToBigInteger(Object value)
	{
		if (value instanceof BigInteger)
		{
			return (BigInteger) value;
		} else if (value instanceof BigDecimal)
		{
			return ((BigDecimal) value).toBigInteger();
		} else if (value instanceof Number)
		{
			return BigInteger.valueOf(((Number) value).longValue());
		} else if (value instanceof String)
		{
			String val = (String) value;
			try
			{
				return new BigInteger(val);
			} catch (NumberFormatException e1)
			{
				try
				{
					BigDecimal dec = new BigDecimal(val);
					return dec.toBigInteger();
				} catch (NumberFormatException e2)
				{
					// pass through to the ClassCastException
				}
			}
		}
		throw new ClassCastException(String.format("The JSON value of %s cannot be cast to a BigInteger!", value.toString()));
	}

	/**
	 * @param value
	 * @return
	 */
	private static BigDecimal castToBigDecimal(Object value)
	{
		if (value instanceof BigDecimal)
		{
			return (BigDecimal) value;
		} else if (value instanceof BigInteger)
		{
			return new BigDecimal(((BigInteger) value).toString());
		} else if (value instanceof Number)
		{
			return BigDecimal.valueOf(((Number) value).doubleValue());
		} else if (value instanceof String)
		{
			String val = (String) value;
			try
			{
				return new BigDecimal(val);
			} catch (NumberFormatException e1)
			{
				// pass through to the ClassCastException
			}
		}
		throw new ClassCastException(String.format("The JSON value of %s cannot be cast to a BigDecimal!", value.toString()));
	}

	private static String castToString(Object value)
	{
		if (value instanceof String)
		{
			return (String) value;
		}
		throw new ClassCastException(String.format("The JSON value of %s cannot be cast to a String!", value.toString()));
	}

	/**
	 * @param value
	 * @param clazz
	 * @return
	 */
	private static <T extends Enum<T>> Enum<T> castToEnum(Object value, Class<T> clazz)
	{
		if (value instanceof Enum<?>)
		{
			for (T t: clazz.getEnumConstants())
			{
				if (t == value)
				{
					return t;
				}
			}
		} else if (value instanceof String)
		{
			String val = (String) value;
			for (T t: clazz.getEnumConstants())
			{
				if (t.name().equals(val))
				{
					return t;
				}
			}
		}
		throw new ClassCastException(String.format("The JSON value of %s cannot be cast to a BigDecimal!", value.toString()));
	}

	/**
	 * @param value
	 * @param clazz
	 * @param optional
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws JSONException
	 */
	private static <T> T[] castToArray(Object value, Class<T> clazz, boolean optional)
			throws InstantiationException,IllegalAccessException,IllegalArgumentException,InvocationTargetException,
			NoSuchMethodException,SecurityException,JSONException
	{
		if (value instanceof JSONArray)
		{
			Class<?> componentType = clazz.getComponentType();
			JSONArray array = (JSONArray) value;
			int length = array.length();
			@SuppressWarnings("unchecked")
			T[] tArray = (T[]) Array.newInstance(componentType, length);
			for (int i = 0; i < length; i++)
			{
				try
				{
					@SuppressWarnings("unchecked")
					T t = (T) unwrap(array.get(i), componentType, optional);
					tArray[i] = t;
				} catch (ClassCastException e)
				{
					ClassCastException cce = new ClassCastException(
							String.format("The JSON value of %s cannot be cast to a array!", value.toString()));
					cce.initCause(e);
					throw cce;
				}
			}
			return tArray;
		}
		// check for null, and check value.equals(null) because of org.json.JSONObject#NULL
		if (value != null && !value.equals(null) && value.getClass().isArray())
		{
			Class<?> componentType = clazz.getComponentType();
			int length = Array.getLength(value);
			@SuppressWarnings("unchecked")
			T[] tArray = (T[]) Array.newInstance(componentType, length);
			for (int i = 0; i < length; i++)
			{
				try
				{
					@SuppressWarnings("unchecked")
					T t = (T) unwrap(Array.get(value, i), componentType, optional);
					tArray[i] = t;
				} catch (ClassCastException e)
				{
					ClassCastException cce = new ClassCastException(
							String.format("The JSON value of %s cannot be cast to an array!", value.toString()));
					cce.initCause(e);
					throw cce;
				}
			}
			return tArray;
		}
		throw new ClassCastException(String.format("The JSON value of %s cannot be cast to an array!", value.toString()));
	}

	/**
	 * @param value
	 * @param clazz
	 * @param optional
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	private static <T> T castToObject(Object value, Class<T> clazz, boolean optional) throws InstantiationException,
			IllegalAccessException,IllegalArgumentException,InvocationTargetException,NoSuchMethodException,SecurityException
	{
		if (value instanceof JSONObject)
		{
			return _populateObject((JSONObject) value, clazz, optional);
		}
		throw new ClassCastException(String.format("The JSON value of %s(%s) cannot be cast to a %s!", value.getClass().getName(),
				value.toString(), clazz.getSimpleName()));
	}
}
