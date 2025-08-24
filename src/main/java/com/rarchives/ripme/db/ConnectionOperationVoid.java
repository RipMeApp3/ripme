package com.rarchives.ripme.db;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionOperationVoid {
    void apply(Connection conn) throws SQLException;
}
