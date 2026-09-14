package com.redis.storage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background daemon that periodically scans and actively purges expired keys.
 */
public class ExpiryEngine {
    private final DataStore dataStore;
    private final ScheduledExecutorService scheduler;
    private static final int SAMPLE_SIZE = 20;
    private static final long CHECK_INTERVAL_MS = 100;

    public ExpiryEngine(DataStore dataStore) {
        this.dataStore = dataStore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "redis-expiry-engine");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::runActiveExpiry, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void runActiveExpiry() {
        try {
            int dbCount = dataStore.getDatabaseCount();
            for (int i = 0; i < dbCount; i++) {
                RedisDatabase db = dataStore.getDatabase(i);
                // Active sampling loop
                int expired;
                do {
                    expired = db.sampleAndExpire(SAMPLE_SIZE);
                    // If more than 25% of the sampled keys were expired, repeat immediately for this DB
                } while (expired > (SAMPLE_SIZE / 4));
            }
        } catch (Throwable t) {
            // Ignore background exceptions
        }
    }
}
