package com.redis.storage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Represents a single Redis database slot (e.g. DB 0).
 */
public class RedisDatabase {
    private final int dbIndex;
    private final Map<String, RedisValue> entries = new ConcurrentHashMap<>();
    private final Map<String, Long> expires = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> keyVersions = new ConcurrentHashMap<>();

    public RedisDatabase(int dbIndex) {
        this.dbIndex = dbIndex;
    }

    public int getDbIndex() {
        return dbIndex;
    }

    private void touchKey(String key) {
        keyVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public long getKeyVersion(String key) {
        AtomicLong v = keyVersions.get(key);
        return v != null ? v.get() : 0L;
    }

    /**
     * Checks if a key has expired. If so, passively evicts it.
     * @return true if key was expired and deleted
     */
    public boolean checkPassiveExpiry(String key) {
        Long expireTime = expires.get(key);
        if (expireTime != null && System.currentTimeMillis() >= expireTime) {
            delete(key);
            return true;
        }
        return false;
    }

    public RedisValue get(String key) {
        if (checkPassiveExpiry(key)) {
            return null;
        }
        return entries.get(key);
    }

    public void put(String key, RedisValue value) {
        entries.put(key, value);
        expires.remove(key);
        touchKey(key);
    }

    public void put(String key, RedisValue value, Long expireAtMillis) {
        entries.put(key, value);
        if (expireAtMillis != null) {
            expires.put(key, expireAtMillis);
        } else {
            expires.remove(key);
        }
        touchKey(key);
    }

    public boolean delete(String key) {
        boolean removedEntry = (entries.remove(key) != null);
        expires.remove(key);
        if (removedEntry) {
            touchKey(key);
        }
        return removedEntry;
    }

    public boolean exists(String key) {
        if (checkPassiveExpiry(key)) {
            return false;
        }
        return entries.containsKey(key);
    }

    public RedisType type(String key) {
        if (checkPassiveExpiry(key)) {
            return RedisType.NONE;
        }
        RedisValue val = entries.get(key);
        return val != null ? val.getType() : RedisType.NONE;
    }

    public boolean setExpire(String key, long expireAtMillis) {
        if (!exists(key)) {
            return false;
        }
        expires.put(key, expireAtMillis);
        return true;
    }

    public long getTtlSeconds(String key) {
        if (!exists(key)) {
            return -2; // Key does not exist
        }
        Long exp = expires.get(key);
        if (exp == null) {
            return -1; // Key exists with no expiration
        }
        long diff = exp - System.currentTimeMillis();
        return diff > 0 ? (diff / 1000) : -2;
    }

    public long getPttlMillis(String key) {
        if (!exists(key)) {
            return -2; // Key does not exist
        }
        Long exp = expires.get(key);
        if (exp == null) {
            return -1; // Key exists with no expiration
        }
        long diff = exp - System.currentTimeMillis();
        return diff > 0 ? diff : -2;
    }

    public boolean persist(String key) {
        if (!exists(key)) {
            return false;
        }
        return (expires.remove(key) != null);
    }

    public boolean rename(String oldKey, String newKey) {
        if (checkPassiveExpiry(oldKey)) {
            return false;
        }
        RedisValue val = entries.remove(oldKey);
        if (val == null) {
            return false;
        }
        Long exp = expires.remove(oldKey);
        entries.put(newKey, val);
        if (exp != null) {
            expires.put(newKey, exp);
        } else {
            expires.remove(newKey);
        }
        touchKey(oldKey);
        touchKey(newKey);
        return true;
    }

    public boolean renameNx(String oldKey, String newKey) {
        if (exists(newKey)) {
            return false;
        }
        return rename(oldKey, newKey);
    }

    public List<String> keys(String globPattern) {
        Pattern regex = globToRegex(globPattern);
        List<String> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, RedisValue> entry : entries.entrySet()) {
            String key = entry.getKey();
            Long exp = expires.get(key);
            if (exp != null && now >= exp) {
                delete(key);
                continue;
            }
            if (regex.matcher(key).matches()) {
                result.add(key);
            }
        }
        return result;
    }

    public String randomKey() {
        if (entries.isEmpty()) return null;
        List<String> keys = new ArrayList<>(entries.keySet());
        if (keys.isEmpty()) return null;
        Collections.shuffle(keys);
        for (String k : keys) {
            if (!checkPassiveExpiry(k)) {
                return k;
            }
        }
        return null;
    }

    public int dbsize() {
        // Clean up expired keys on size inquiry
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> expEntry : new ArrayList<>(expires.entrySet())) {
            if (now >= expEntry.getValue()) {
                delete(expEntry.getKey());
            }
        }
        return entries.size();
    }

    public void flush() {
        for (String key : entries.keySet()) {
            touchKey(key);
        }
        entries.clear();
        expires.clear();
    }

    /**
     * Active key expiry sampler: randomly samples up to sampleSize expiring keys
     * and deletes expired ones.
     */
    public int sampleAndExpire(int sampleSize) {
        if (expires.isEmpty()) return 0;
        int expiredCount = 0;
        long now = System.currentTimeMillis();
        List<String> expiringKeys = new ArrayList<>(expires.keySet());
        Collections.shuffle(expiringKeys);

        int checked = 0;
        for (String key : expiringKeys) {
            if (checked++ >= sampleSize) break;
            Long exp = expires.get(key);
            if (exp != null && now >= exp) {
                delete(key);
                expiredCount++;
            }
        }
        return expiredCount;
    }

    public Map<String, RedisValue> getEntriesUnsafe() {
        return entries;
    }

    public Map<String, Long> getExpiresUnsafe() {
        return expires;
    }

    public static Pattern globToRegex(String glob) {
        if (glob == null || glob.isEmpty() || glob.equals("*")) {
            return Pattern.compile(".*");
        }
        StringBuilder out = new StringBuilder("^");
        for (int i = 0; i < glob.length(); ++i) {
            final char c = glob.charAt(i);
            switch (c) {
                case '*':
                    out.append(".*");
                    break;
                case '?':
                    out.append('.');
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                    out.append('\\').append(c);
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '[':
                case ']':
                    out.append(c);
                    break;
                default:
                    out.append(c);
            }
        }
        out.append('$');
        return Pattern.compile(out.toString());
    }
}
