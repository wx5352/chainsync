package io.github.chainsync.store.jdbc;

/** Unchecked wrapper for persistence failures in the JDBC store. */
public class StoreException extends RuntimeException {

    public StoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
