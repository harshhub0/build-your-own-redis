package com.redis.storage;

public enum RedisType {
    STRING("string"),
    LIST("list"),
    HASH("hash"),
    SET("set"),
    ZSET("zset"),
    NONE("none");

    private final String typeName;

    RedisType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }
}
