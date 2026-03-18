package com.wsi.platform.common;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.*;

/**
 * MyBatis TypeHandler that maps a Java String to/from a PostgreSQL JSONB column.
 * Used by the Annotation entity's "coordinates" field.
 */
public class StringJsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    String parameter, JdbcType jdbcType) throws SQLException {
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(parameter);
        ps.setObject(i, obj);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return extract(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return extract(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return extract(cs.getObject(columnIndex));
    }

    private String extract(Object obj) {
        if (obj == null) return null;
        if (obj instanceof PGobject pgObj) return pgObj.getValue();
        return obj.toString();
    }
}
