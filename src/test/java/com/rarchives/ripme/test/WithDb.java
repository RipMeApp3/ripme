package com.rarchives.ripme.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Create a database accessible at {@code db}<br>
 * Any other tests using the same in-memory connection string will share the same database if they run concurrently.
 * For test-independent databases that can run concurrently, try {@link WithInMemoryDb}.
 * <br>
 * Example usage: {@code @WithDb("mydb.sqlite")}<br>
 * Example usage: {@code @WithDb(":memory:?cache=shared")}<br>
 * Example usage: {@code @WithDb("myDbNamespaced?mode=memory&cache=shared")}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithDb {
    String value();
}
