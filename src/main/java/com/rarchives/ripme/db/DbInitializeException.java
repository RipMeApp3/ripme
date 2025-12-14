package com.rarchives.ripme.db;

public class DbInitializeException extends Exception {
    public DbInitializeException(String message, Throwable cause) {
        super(message, cause);
    }

    public DbInitializeException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
