package com.rarchives.ripme.db.dao;

import com.rarchives.ripme.db.DatabaseManager;

import java.sql.SQLException;
import java.util.Optional;

public abstract class BaseDao<T> {

    protected final DatabaseManager db;

    public BaseDao(DatabaseManager db) {
        this.db = db;
    }

    public abstract Optional<T> findById(long id) throws SQLException;
}
