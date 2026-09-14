package com.redis.resp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a parsed token/value in the REdis Serialization Protocol (RESP).
 */
public class RespToken {
    public enum Type {
        SIMPLE_STRING,
        ERROR,
        INTEGER,
        BULK_STRING,
        ARRAY,
        NULL_BULK_STRING,
        NULL_ARRAY
    }

    private final Type type;
    private final byte[] bytesValue;
    private final long intValue;
    private final List<RespToken> arrayValue;

    private RespToken(Type type, byte[] bytesValue, long intValue, List<RespToken> arrayValue) {
        this.type = type;
        this.bytesValue = bytesValue;
        this.intValue = intValue;
        this.arrayValue = arrayValue;
    }

    public static RespToken simpleString(String str) {
        return new RespToken(Type.SIMPLE_STRING, str.getBytes(StandardCharsets.UTF_8), 0, null);
    }

    public static RespToken simpleString(byte[] bytes) {
        return new RespToken(Type.SIMPLE_STRING, bytes, 0, null);
    }

    public static RespToken error(String message) {
        return new RespToken(Type.ERROR, message.getBytes(StandardCharsets.UTF_8), 0, null);
    }

    public static RespToken integer(long val) {
        return new RespToken(Type.INTEGER, null, val, null);
    }

    public static RespToken bulkString(byte[] data) {
        if (data == null) {
            return nullBulkString();
        }
        return new RespToken(Type.BULK_STRING, data, 0, null);
    }

    public static RespToken bulkString(String str) {
        if (str == null) {
            return nullBulkString();
        }
        return new RespToken(Type.BULK_STRING, str.getBytes(StandardCharsets.UTF_8), 0, null);
    }

    public static RespToken nullBulkString() {
        return new RespToken(Type.NULL_BULK_STRING, null, 0, null);
    }

    public static RespToken array(List<RespToken> items) {
        if (items == null) {
            return nullArray();
        }
        return new RespToken(Type.ARRAY, null, 0, items);
    }

    public static RespToken nullArray() {
        return new RespToken(Type.NULL_ARRAY, null, 0, null);
    }

    // Common standard constants
    public static final RespToken OK = simpleString("OK");
    public static final RespToken PONG = simpleString("PONG");
    public static final RespToken QUEUED = simpleString("QUEUED");
    public static final RespToken ONE = integer(1);
    public static final RespToken ZERO = integer(0);
    public static final RespToken NULL_BULK = nullBulkString();
    public static final RespToken NULL_ARR = nullArray();

    public Type getType() {
        return type;
    }

    public byte[] getBytesValue() {
        return bytesValue;
    }

    public String getStringValue() {
        if (bytesValue == null) return null;
        return new String(bytesValue, StandardCharsets.UTF_8);
    }

    public long getIntValue() {
        return intValue;
    }

    public List<RespToken> getArrayValue() {
        return arrayValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RespToken respToken = (RespToken) o;
        return type == respToken.type &&
               intValue == respToken.intValue &&
               Arrays.equals(bytesValue, respToken.bytesValue) &&
               Objects.equals(arrayValue, respToken.arrayValue);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, intValue, arrayValue);
        result = 31 * result + Arrays.hashCode(bytesValue);
        return result;
    }

    @Override
    public String toString() {
        switch (type) {
            case SIMPLE_STRING:
                return "+" + getStringValue();
            case ERROR:
                return "-" + getStringValue();
            case INTEGER:
                return ":" + intValue;
            case BULK_STRING:
                return "$" + (bytesValue != null ? bytesValue.length + " " + getStringValue() : "null");
            case ARRAY:
                return "*" + (arrayValue != null ? arrayValue.size() : "null") + " " + arrayValue;
            case NULL_BULK_STRING:
                return "$-1";
            case NULL_ARRAY:
                return "*-1";
            default:
                return super.toString();
        }
    }
}
