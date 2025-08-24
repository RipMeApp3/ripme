package com.rarchives.ripme;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestHelpers {
    public static List<Map<String, Object>> debugSqlQuery(Connection conn, String sql, Object... args) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                stmt.setObject(i + 1, args[i]);
            }
            ResultSet resultSet = stmt.executeQuery();
            ResultSetMetaData rsMeta = resultSet.getMetaData();
            List<Map<String, Object>> results = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i=1; i <= rsMeta.getColumnCount(); i++) {
                    row.put(rsMeta.getColumnName(i), resultSet.getObject(i));
                }
                results.add(row);
            }
            return results;
        } catch (SQLException e) {
            return List.of(Map.of("error", e.getMessage()));
        }
    }
}
