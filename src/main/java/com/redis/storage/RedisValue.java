package com.redis.storage;

import com.redis.datastructures.ZSet;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Polymorphic value container holding the data and type of a Redis key.
 */
public class RedisValue {
    private final RedisType type;
    private final Object data;

    public RedisValue(RedisType type, Object data) {
        this.type = type;
        this.data = data;
    }

    public static RedisValue string(byte[] data) {
        return new RedisValue(RedisType.STRING, data);
    }

    public static RedisValue string(String str) {
        return new RedisValue(RedisType.STRING, str.getBytes(StandardCharsets.UTF_8));
    }

    public static RedisValue list() {
        return new RedisValue(RedisType.LIST, new LinkedList<byte[]>());
    }

    public static RedisValue list(List<byte[]> initial) {
        return new RedisValue(RedisType.LIST, new LinkedList<>(initial));
    }

    public static RedisValue hash() {
        return new RedisValue(RedisType.HASH, new LinkedHashMap<String, byte[]>());
    }

    public static RedisValue hash(Map<String, byte[]> initial) {
        return new RedisValue(RedisType.HASH, new LinkedHashMap<>(initial));
    }

    public static RedisValue set() {
        return new RedisValue(RedisType.SET, new LinkedHashSet<String>());
    }

    public static RedisValue set(Set<String> initial) {
        return new RedisValue(RedisType.SET, new LinkedHashSet<>(initial));
    }

    public static RedisValue zset() {
        return new RedisValue(RedisType.ZSET, new ZSet());
    }

    public static RedisValue zset(ZSet zset) {
        return new RedisValue(RedisType.ZSET, zset);
    }

    public RedisType getType() {
        return type;
    }

    public byte[] getAsString() {
        if (type != RedisType.STRING) {
            throw new WrongTypeException();
        }
        return (byte[]) data;
    }

    public String getAsStringUtf8() {
        byte[] bytes = getAsString();
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    @SuppressWarnings("unchecked")
    public LinkedList<byte[]> getAsList() {
        if (type != RedisType.LIST) {
            throw new WrongTypeException();
        }
        return (LinkedList<byte[]>) data;
    }

    @SuppressWarnings("unchecked")
    public Map<String, byte[]> getAsHash() {
        if (type != RedisType.HASH) {
            throw new WrongTypeException();
        }
        return (Map<String, byte[]>) data;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getAsSet() {
        if (type != RedisType.SET) {
            throw new WrongTypeException();
        }
        return (Set<String>) data;
    }

    public ZSet getAsZSet() {
        if (type != RedisType.ZSET) {
            throw new WrongTypeException();
        }
        return (ZSet) data;
    }

    public Object getRawData() {
        return data;
    }

    public static class WrongTypeException extends RuntimeException {
        public WrongTypeException() {
            super("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
    }
}
