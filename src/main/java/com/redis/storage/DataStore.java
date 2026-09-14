package com.redis.storage;

/**
 * Manages all 16 isolated Redis database slots (0 to 15).
 */
public class DataStore {
    public static final int DEFAULT_DB_COUNT = 16;
    private final RedisDatabase[] databases;
    private final ExpiryEngine expiryEngine;

    public DataStore() {
        this(DEFAULT_DB_COUNT);
    }

    public DataStore(int dbCount) {
        this.databases = new RedisDatabase[dbCount];
        for (int i = 0; i < dbCount; i++) {
            this.databases[i] = new RedisDatabase(i);
        }
        this.expiryEngine = new ExpiryEngine(this);
        this.expiryEngine.start();
    }

    public RedisDatabase getDatabase(int index) {
        if (index < 0 || index >= databases.length) {
            throw new IndexOutOfBoundsException("ERR DB index is out of range: " + index);
        }
        return databases[index];
    }

    public int getDatabaseCount() {
        return databases.length;
    }

    public void flushAll() {
        for (RedisDatabase db : databases) {
            db.flush();
        }
    }

    public int getTotalKeys() {
        int total = 0;
        for (RedisDatabase db : databases) {
            total += db.dbsize();
        }
        return total;
    }

    public void close() {
        expiryEngine.stop();
    }
}
