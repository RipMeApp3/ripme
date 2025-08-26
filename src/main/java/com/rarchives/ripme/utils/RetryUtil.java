package com.rarchives.ripme.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class RetryUtil {
    private static final Logger logger = LogManager.getLogger(RetryUtil.class);

    public static <T> T executeWithRetry(Callable<T> task, int maxAttempts, Duration initialDelay, double delayMultiplier, Predicate<Exception> shouldRetry) throws Exception {
        int attempt = 0;
        long initialDelayMillis = initialDelay.toMillis();
        while (true) {
            try {
                return task.call();
            } catch (Exception e) {
                if (!shouldRetry.test(e)) {
                    throw e;
                }
                if (++attempt >= maxAttempts) {
                    logger.error("Maximum retry attempts exceeded", e);
                    throw e;
                }
                // Exponential backoff delay
                try {
                    long delay = (long) (initialDelayMillis * Math.pow(delayMultiplier, attempt));
                    logger.debug("Retry: backing off for {}", Duration.ofMillis(delay));
                    TimeUnit.MILLISECONDS.sleep(delay);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}
