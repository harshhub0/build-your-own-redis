package com.redis.test;

import com.redis.commands.CommandContext;
import com.redis.commands.CommandRegistry;
import com.redis.config.ServerConfig;
import com.redis.net.ClientConnection;
import com.redis.net.PubSubManager;
import com.redis.persistence.AofEngine;
import com.redis.persistence.RdbEngine;
import com.redis.resp.RespToken;
import com.redis.storage.DataStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StorageCommandsTest {

    public static void run() {
        System.out.println("[Test] Running Storage Commands tests...");
        ServerConfig config = new ServerConfig();
        config.setAppendOnly(false); // Disable file I/O for unit test
        DataStore dataStore = new DataStore(16);
        PubSubManager pubSubManager = new PubSubManager();
        CommandRegistry registry = new CommandRegistry();
        ClientConnection client = new ClientConnection(null);
        AofEngine aofEngine = new AofEngine(config);
        RdbEngine rdbEngine = new RdbEngine(config);

        CommandContext ctx = new CommandContext(config, dataStore, pubSubManager, client, aofEngine, rdbEngine, () -> {});

        testStrings(ctx, registry);
        testLists(ctx, registry);
        testHashes(ctx, registry);
        testSets(ctx, registry);
        testZSets(ctx, registry);
        testTtlExpiry(ctx, registry);

        dataStore.close();
        System.out.println("[Test] Storage Commands tests passed successfully!\n");
    }

    private static RespToken exec(CommandContext ctx, CommandRegistry registry, String cmd, String... strArgs) {
        List<byte[]> args = new ArrayList<>();
        for (String a : strArgs) {
            args.add(a.getBytes(StandardCharsets.UTF_8));
        }
        return registry.dispatch(ctx, cmd, args);
    }

    private static void testStrings(CommandContext ctx, CommandRegistry registry) {
        RespToken res = exec(ctx, registry, "SET", "k1", "hello");
        assert res.equals(RespToken.OK);

        res = exec(ctx, registry, "GET", "k1");
        assert "hello".equals(res.getStringValue());

        res = exec(ctx, registry, "APPEND", "k1", " world");
        assert res.getIntValue() == 11;

        res = exec(ctx, registry, "GET", "k1");
        assert "hello world".equals(res.getStringValue());

        res = exec(ctx, registry, "STRLEN", "k1");
        assert res.getIntValue() == 11;

        // INCR / DECR
        exec(ctx, registry, "SET", "counter", "10");
        res = exec(ctx, registry, "INCR", "counter");
        assert res.getIntValue() == 11;

        res = exec(ctx, registry, "INCRBY", "counter", "5");
        assert res.getIntValue() == 16;

        res = exec(ctx, registry, "DECR", "counter");
        assert res.getIntValue() == 15;

        // MSET / MGET
        exec(ctx, registry, "MSET", "a", "1", "b", "2", "c", "3");
        res = exec(ctx, registry, "MGET", "a", "b", "c", "nonexistent");
        assert res.getArrayValue().size() == 4;
        assert "1".equals(res.getArrayValue().get(0).getStringValue());
        assert "2".equals(res.getArrayValue().get(1).getStringValue());
        assert "3".equals(res.getArrayValue().get(2).getStringValue());
        assert res.getArrayValue().get(3).getType() == RespToken.Type.NULL_BULK_STRING;
    }

    private static void testLists(CommandContext ctx, CommandRegistry registry) {
        exec(ctx, registry, "DEL", "mylist");
        exec(ctx, registry, "RPUSH", "mylist", "a", "b", "c");
        exec(ctx, registry, "LPUSH", "mylist", "first");

        RespToken res = exec(ctx, registry, "LLEN", "mylist");
        assert res.getIntValue() == 4;

        res = exec(ctx, registry, "LRANGE", "mylist", "0", "-1");
        assert res.getArrayValue().size() == 4;
        assert "first".equals(res.getArrayValue().get(0).getStringValue());
        assert "c".equals(res.getArrayValue().get(3).getStringValue());

        res = exec(ctx, registry, "LPOP", "mylist");
        assert "first".equals(res.getStringValue());

        res = exec(ctx, registry, "RPOP", "mylist");
        assert "c".equals(res.getStringValue());

        res = exec(ctx, registry, "LINDEX", "mylist", "0");
        assert "a".equals(res.getStringValue());
    }

    private static void testHashes(CommandContext ctx, CommandRegistry registry) {
        exec(ctx, registry, "DEL", "myhash");
        exec(ctx, registry, "HSET", "myhash", "name", "John", "age", "30");

        RespToken res = exec(ctx, registry, "HGET", "myhash", "name");
        assert "John".equals(res.getStringValue());

        res = exec(ctx, registry, "HEXISTS", "myhash", "age");
        assert res.getIntValue() == 1;

        res = exec(ctx, registry, "HLEN", "myhash");
        assert res.getIntValue() == 2;

        res = exec(ctx, registry, "HINCRBY", "myhash", "age", "5");
        assert res.getIntValue() == 35;

        res = exec(ctx, registry, "HDEL", "myhash", "name");
        assert res.getIntValue() == 1;

        res = exec(ctx, registry, "HGET", "myhash", "name");
        assert res.getType() == RespToken.Type.NULL_BULK_STRING;
    }

    private static void testSets(CommandContext ctx, CommandRegistry registry) {
        exec(ctx, registry, "DEL", "set1", "set2");
        exec(ctx, registry, "SADD", "set1", "a", "b", "c");
        exec(ctx, registry, "SADD", "set2", "b", "c", "d");

        RespToken res = exec(ctx, registry, "SCARD", "set1");
        assert res.getIntValue() == 3;

        res = exec(ctx, registry, "SISMEMBER", "set1", "a");
        assert res.getIntValue() == 1;

        res = exec(ctx, registry, "SINTER", "set1", "set2");
        assert res.getArrayValue().size() == 2; // "b" and "c"

        res = exec(ctx, registry, "SUNION", "set1", "set2");
        assert res.getArrayValue().size() == 4; // "a", "b", "c", "d"

        res = exec(ctx, registry, "SDIFF", "set1", "set2");
        assert res.getArrayValue().size() == 1;
        assert "a".equals(res.getArrayValue().get(0).getStringValue());
    }

    private static void testZSets(CommandContext ctx, CommandRegistry registry) {
        exec(ctx, registry, "DEL", "myzset");
        exec(ctx, registry, "ZADD", "myzset", "10", "one", "20", "two", "30", "three");

        RespToken res = exec(ctx, registry, "ZCARD", "myzset");
        assert res.getIntValue() == 3;

        res = exec(ctx, registry, "ZSCORE", "myzset", "two");
        assert "20".equals(res.getStringValue());

        res = exec(ctx, registry, "ZRANK", "myzset", "two");
        assert res.getIntValue() == 1;

        res = exec(ctx, registry, "ZCOUNT", "myzset", "15", "35");
        assert res.getIntValue() == 2;

        res = exec(ctx, registry, "ZRANGE", "myzset", "0", "-1", "WITHSCORES");
        assert res.getArrayValue().size() == 6;
        assert "one".equals(res.getArrayValue().get(0).getStringValue());
        assert "10".equals(res.getArrayValue().get(1).getStringValue());
    }

    private static void testTtlExpiry(CommandContext ctx, CommandRegistry registry) {
        exec(ctx, registry, "SET", "tempKey", "val");
        exec(ctx, registry, "EXPIRE", "tempKey", "100");

        RespToken res = exec(ctx, registry, "TTL", "tempKey");
        assert res.getIntValue() > 0 && res.getIntValue() <= 100;

        exec(ctx, registry, "PERSIST", "tempKey");
        res = exec(ctx, registry, "TTL", "tempKey");
        assert res.getIntValue() == -1; // No expiry
    }
}
