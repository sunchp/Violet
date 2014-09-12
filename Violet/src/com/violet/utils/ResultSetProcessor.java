package com.violet.utils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResultSetProcessor {

	protected static final int PROPERTY_NOT_FOUND = -1;

	private static final Map<Class<?>, Object> primitiveDefaults = new HashMap<Class<?>, Object>();

	private final Map<String, String> columnToPropertyOverrides;

	static {
		primitiveDefaults.put(Integer.TYPE, Integer.valueOf(0));
		primitiveDefaults.put(Short.TYPE, Short.valueOf((short) 0));
		primitiveDefaults.put(Byte.TYPE, Byte.valueOf((byte) 0));
		primitiveDefaults.put(Float.TYPE, Float.valueOf(0f));
		primitiveDefaults.put(Double.TYPE, Double.valueOf(0d));
		primitiveDefaults.put(Long.TYPE, Long.valueOf(0L));
		primitiveDefaults.put(Boolean.TYPE, Boolean.FALSE);
		primitiveDefaults.put(Character.TYPE, Character.valueOf((char) 0));
	}

	public ResultSetProcessor() {
		this(new HashMap<String, String>());
	}

	public ResultSetProcessor(Map<String, String> columnToPropertyOverrides) {
		super();
		if (columnToPropertyOverrides == null) {
			throw new IllegalArgumentException("columnToPropertyOverrides map cannot be null");
		}
		this.columnToPropertyOverrides = columnToPropertyOverrides;
	}

	public <T> T toBean(ResultSet rs, Class<T> type) throws SQLException {

		PropertyDescriptor[] props = this.propertyDescriptors(type);

		ResultSetMetaData rsmd = rs.getMetaData();
		int[] columnToProperty = this.mapColumnsToProperties(rsmd, props);

		return this.createBean(rs, type, props, columnToProperty);
	}

	public <T> List<T> toBeanList(ResultSet rs, Class<T> type) throws SQLException {
		List<T> results = new ArrayList<T>();

		if (!rs.next()) {
			return results;
		}

		PropertyDescriptor[] props = this.propertyDescriptors(type);
		ResultSetMetaData rsmd = rs.getMetaData();
		int[] columnToProperty = this.mapColumnsToProperties(rsmd, props);

		do {
			results.add(this.createBean(rs, type, props, columnToProperty));
		} while (rs.next());

		return results;
	}

	private <T> T createBean(ResultSet rs, Class<T> type, PropertyDescriptor[] props, int[] columnToProperty) throws SQLException {

		T bean = this.newInstance(type);

		for (int i = 1; i < columnToProperty.length; i++) {

			if (columnToProperty[i] == PROPERTY_NOT_FOUND) {
				continue;
			}

			PropertyDescriptor prop = props[columnToProperty[i]];
			Class<?> propType = prop.getPropertyType();

			Object value = null;
			if (propType != null) {
				value = this.processColumn(rs, i, propType);

				if (value == null && propType.isPrimitive()) {
					value = primitiveDefaults.get(propType);
				}
			}

			this.callSetter(bean, prop, value);
		}

		return bean;
	}

	@SuppressWarnings("unchecked")
	private void callSetter(Object target, PropertyDescriptor prop, Object value) throws SQLException {

		Method setter = prop.getWriteMethod();

		if (setter == null) {
			return;
		}

		Class<?>[] params = setter.getParameterTypes();
		try {
			// convert types for some popular ones
			if (value instanceof java.util.Date) {
				final String targetType = params[0].getName();
				if ("java.sql.Date".equals(targetType)) {
					value = new java.sql.Date(((java.util.Date) value).getTime());
				} else if ("java.sql.Time".equals(targetType)) {
					value = new java.sql.Time(((java.util.Date) value).getTime());
				} else if ("java.sql.Timestamp".equals(targetType)) {
					Timestamp tsValue = (Timestamp) value;
					int nanos = tsValue.getNanos();
					value = new java.sql.Timestamp(tsValue.getTime());
					((Timestamp) value).setNanos(nanos);
				}
			} else if (value instanceof String && params[0].isEnum()) {
				value = Enum.valueOf(params[0].asSubclass(Enum.class), (String) value);
			}

			// Don't call setter if the value object isn't the right type
			if (this.isCompatibleType(value, params[0])) {
				setter.invoke(target, new Object[] { value });
			} else {
				throw new SQLException("Cannot set " + prop.getName() + ": incompatible types, cannot convert " + value.getClass().getName() + " to " + params[0].getName());
				// value cannot be null here because isCompatibleType allows
				// null
			}

		} catch (IllegalArgumentException e) {
			throw new SQLException("Cannot set " + prop.getName() + ": " + e.getMessage());

		} catch (IllegalAccessException e) {
			throw new SQLException("Cannot set " + prop.getName() + ": " + e.getMessage());

		} catch (InvocationTargetException e) {
			throw new SQLException("Cannot set " + prop.getName() + ": " + e.getMessage());
		}
	}

	private boolean isCompatibleType(Object value, Class<?> type) {
		// Do object check first, then primitives
		if (value == null || type.isInstance(value)) {
			return true;

		} else if (type.equals(Integer.TYPE) && value instanceof Integer) {
			return true;

		} else if (type.equals(Long.TYPE) && value instanceof Long) {
			return true;

		} else if (type.equals(Double.TYPE) && value instanceof Double) {
			return true;

		} else if (type.equals(Float.TYPE) && value instanceof Float) {
			return true;

		} else if (type.equals(Short.TYPE) && value instanceof Short) {
			return true;

		} else if (type.equals(Byte.TYPE) && value instanceof Byte) {
			return true;

		} else if (type.equals(Character.TYPE) && value instanceof Character) {
			return true;

		} else if (type.equals(Boolean.TYPE) && value instanceof Boolean) {
			return true;

		}
		return false;

	}

	protected <T> T newInstance(Class<T> c) throws SQLException {
		try {
			return c.newInstance();

		} catch (InstantiationException e) {
			throw new SQLException("Cannot create " + c.getName() + ": " + e.getMessage());

		} catch (IllegalAccessException e) {
			throw new SQLException("Cannot create " + c.getName() + ": " + e.getMessage());
		}
	}

	private PropertyDescriptor[] propertyDescriptors(Class<?> c) throws SQLException {
		// Introspector caches BeanInfo classes for better performance
		BeanInfo beanInfo = null;
		try {
			beanInfo = Introspector.getBeanInfo(c);

		} catch (IntrospectionException e) {
			throw new SQLException("Bean introspection failed: " + e.getMessage());
		}

		return beanInfo.getPropertyDescriptors();
	}

	protected int[] mapColumnsToProperties(ResultSetMetaData rsmd, PropertyDescriptor[] props) throws SQLException {

		int cols = rsmd.getColumnCount();
		int[] columnToProperty = new int[cols + 1];
		Arrays.fill(columnToProperty, PROPERTY_NOT_FOUND);

		for (int col = 1; col <= cols; col++) {
			String columnName = rsmd.getColumnLabel(col);
			if (null == columnName || 0 == columnName.length()) {
				columnName = rsmd.getColumnName(col);
			}
			String propertyName = columnToPropertyOverrides.get(columnName);
			if (propertyName == null) {
				propertyName = columnName;
			}
			for (int i = 0; i < props.length; i++) {

				if (propertyName.equalsIgnoreCase(props[i].getName())) {
					columnToProperty[col] = i;
					break;
				}
			}
		}

		return columnToProperty;
	}

	protected Object processColumn(ResultSet rs, int index, Class<?> propType) throws SQLException {

		if (!propType.isPrimitive() && rs.getObject(index) == null) {
			return null;
		}

		if (propType.equals(String.class)) {
			return rs.getString(index);

		} else if (propType.equals(Integer.TYPE) || propType.equals(Integer.class)) {
			return Integer.valueOf(rs.getInt(index));

		} else if (propType.equals(Boolean.TYPE) || propType.equals(Boolean.class)) {
			return Boolean.valueOf(rs.getBoolean(index));

		} else if (propType.equals(Long.TYPE) || propType.equals(Long.class)) {
			return Long.valueOf(rs.getLong(index));

		} else if (propType.equals(Double.TYPE) || propType.equals(Double.class)) {
			return Double.valueOf(rs.getDouble(index));

		} else if (propType.equals(Float.TYPE) || propType.equals(Float.class)) {
			return Float.valueOf(rs.getFloat(index));

		} else if (propType.equals(Short.TYPE) || propType.equals(Short.class)) {
			return Short.valueOf(rs.getShort(index));

		} else if (propType.equals(Byte.TYPE) || propType.equals(Byte.class)) {
			return Byte.valueOf(rs.getByte(index));

		} else if (propType.equals(Timestamp.class)) {
			return rs.getTimestamp(index);

		} else if (propType.equals(SQLXML.class)) {
			return rs.getSQLXML(index);

		} else {
			return rs.getObject(index);
		}

	}

	/**
	 * Convert a <code>ResultSet</code> row into a <code>Map</code>.
	 * 
	 * <p>
	 * This implementation returns a <code>Map</code> with case insensitive
	 * column names as keys. Calls to <code>map.get("COL")</code> and
	 * <code>map.get("col")</code> return the same value. Furthermore this
	 * implementation will return an ordered map, that preserves the ordering of
	 * the columns in the ResultSet, so that iterating over the entry set of the
	 * returned map will return the first column of the ResultSet, then the
	 * second and so forth.
	 * </p>
	 * 
	 * @param rs
	 *            ResultSet that supplies the map data
	 * @return the newly created Map
	 * @throws SQLException
	 *             if a database access error occurs
	 * @see org.apache.commons.dbutils.RowProcessor#toMap(java.sql.ResultSet)
	 */
	public Map<String, Object> toMap(ResultSet rs) throws SQLException {
		Map<String, Object> result = new CaseInsensitiveHashMap();
		ResultSetMetaData rsmd = rs.getMetaData();
		int cols = rsmd.getColumnCount();

		for (int i = 1; i <= cols; i++) {
			String columnName = rsmd.getColumnLabel(i);
			if (null == columnName || 0 == columnName.length()) {
				columnName = rsmd.getColumnName(i);
			}
			result.put(columnName, rs.getObject(i));
		}

		return result;
	}

	/**
	 * A Map that converts all keys to lowercase Strings for case insensitive
	 * lookups. This is needed for the toMap() implementation because databases
	 * don't consistently handle the casing of column names.
	 * 
	 * <p>
	 * The keys are stored as they are given [BUG #DBUTILS-34], so we maintain
	 * an internal mapping from lowercase keys to the real keys in order to
	 * achieve the case insensitive lookup.
	 * 
	 * <p>
	 * Note: This implementation does not allow <tt>null</tt> for key, whereas
	 * {@link LinkedHashMap} does, because of the code:
	 * 
	 * <pre>
	 * key.toString().toLowerCase()
	 * </pre>
	 */
	private static class CaseInsensitiveHashMap extends LinkedHashMap<String, Object> {
		/**
		 * The internal mapping from lowercase keys to the real keys.
		 * 
		 * <p>
		 * Any query operation using the key ({@link #get(Object)},
		 * {@link #containsKey(Object)}) is done in three steps:
		 * <ul>
		 * <li>convert the parameter key to lower case</li>
		 * <li>get the actual key that corresponds to the lower case key</li>
		 * <li>query the map with the actual key</li>
		 * </ul>
		 * </p>
		 */
		private final Map<String, String> lowerCaseMap = new HashMap<String, String>();

		/**
		 * Required for serialization support.
		 * 
		 * @see java.io.Serializable
		 */
		private static final long serialVersionUID = -2848100435296897392L;

		/** {@inheritDoc} */
		@Override
		public boolean containsKey(Object key) {
			Object realKey = lowerCaseMap.get(key.toString().toLowerCase(Locale.ENGLISH));
			return super.containsKey(realKey);
			// Possible optimisation here:
			// Since the lowerCaseMap contains a mapping for all the keys,
			// we could just do this:
			// return lowerCaseMap.containsKey(key.toString().toLowerCase());
		}

		/** {@inheritDoc} */
		@Override
		public Object get(Object key) {
			Object realKey = lowerCaseMap.get(key.toString().toLowerCase(Locale.ENGLISH));
			return super.get(realKey);
		}

		/** {@inheritDoc} */
		@Override
		public Object put(String key, Object value) {
			/*
			 * In order to keep the map and lowerCaseMap synchronized, we have
			 * to remove the old mapping before putting the new one. Indeed,
			 * oldKey and key are not necessaliry equals. (That's why we call
			 * super.remove(oldKey) and not just super.put(key, value))
			 */
			Object oldKey = lowerCaseMap.put(key.toLowerCase(Locale.ENGLISH), key);
			Object oldValue = super.remove(oldKey);
			super.put(key, value);
			return oldValue;
		}

		/** {@inheritDoc} */
		@Override
		public void putAll(Map<? extends String, ?> m) {
			for (Map.Entry<? extends String, ?> entry : m.entrySet()) {
				String key = entry.getKey();
				Object value = entry.getValue();
				this.put(key, value);
			}
		}

		/** {@inheritDoc} */
		@Override
		public Object remove(Object key) {
			Object realKey = lowerCaseMap.remove(key.toString().toLowerCase(Locale.ENGLISH));
			return super.remove(realKey);
		}
	}
}
