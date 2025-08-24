package com.rarchives.ripme.db;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionOperation<T> {
    T apply(Connection conn) throws SQLException;
}

