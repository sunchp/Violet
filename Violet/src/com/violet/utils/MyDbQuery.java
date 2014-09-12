package com.violet.utils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jodd.db.DbQuery;
import jodd.db.DbSqlException;
import jodd.db.DbUtil;

public class MyDbQuery extends DbQuery {
	public MyDbQuery(String sqlString) {
		super(sqlString);
	}

	private static final ResultSetProcessor convert = new ResultSetProcessor();

	public <T> T findOne(Class<T> type) {
		ResultSet resultSet = execute();

		try {
			if (resultSet.next()) {
				return convert.toBean(resultSet, type);
			}
		} catch (SQLException sex) {
			throw new DbSqlException(sex);
		} finally {
			DbUtil.close(resultSet);
		}
		return null;
	}

	public <T> List<T> findList(Class<T> type) {
		ResultSet resultSet = execute();

		List<T> list = new ArrayList<T>();

		try {
			while (resultSet.next()) {
				T t = convert.toBean(resultSet, type);
				if (t == null) {
					break;
				}
				list.add(t);
			}
		} catch (SQLException sex) {
			throw new DbSqlException(sex);
		} finally {
			DbUtil.close(resultSet);
		}

		return list;
	}

	public Map<String, Object> findMap() {
		ResultSet resultSet = execute();

		try {
			if (resultSet.next()) {
				return convert.toMap(resultSet);
			}
		} catch (SQLException sex) {
			throw new DbSqlException(sex);
		} finally {
			DbUtil.close(resultSet);
		}
		return null;
	}

	public List<Map<String, Object>> findMapList() {
		ResultSet resultSet = execute();

		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

		try {
			while (resultSet.next()) {
				Map<String, Object> t = convert.toMap(resultSet);
				if (t == null) {
					break;
				}
				list.add(t);
			}
		} catch (SQLException sex) {
			throw new DbSqlException(sex);
		} finally {
			DbUtil.close(resultSet);
		}

		return list;
	}

}
