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

public class TransactionTest {

    public static void run() {
        System.out.println("[Test] Running Transaction (MULTI/EXEC/WATCH) tests...");
        ServerConfig config = new ServerConfig();
        config.setAppendOnly(false);
        DataStore dataStore = new DataStore(16);
        PubSubManager pubSubManager = new PubSubManager();
        CommandRegistry registry = new CommandRegistry();
        AofEngine aofEngine = new AofEngine(config);
        RdbEngine rdbEngine = new RdbEngine(config);

        ClientConnection client1 = new ClientConnection(null);
        CommandContext ctx1 = new CommandContext(config, dataStore, pubSubManager, client1, aofEngine, rdbEngine, () -> {});

        ClientConnection client2 = new ClientConnection(null);
        CommandContext ctx2 = new CommandContext(config, dataStore, pubSubManager, client2, aofEngine, rdbEngine, () -> {});

        testBasicMultiExec(ctx1, registry);
        testDiscard(ctx1, registry);
        testWatchSuccess(ctx1, registry);
        testWatchFailure(ctx1, ctx2, registry);

        dataStore.close();
        System.out.println("[Test] Transaction tests passed successfully!\n");
    }

    private static RespToken exec(CommandContext ctx, CommandRegistry registry, String cmd, String... strArgs) {
        List<byte[]> args = new ArrayList<>();
        for (String a : strArgs) {
            args.add(a.getBytes(StandardCharsets.UTF_8));
        }
        return registry.dispatch(ctx, cmd, args);
    }

    private static void testBasicMultiExec(CommandContext ctx, CommandRegistry registry) {
        exec(ctx, registry, "DEL", "tx_key1", "tx_key2");

        RespToken res = exec(ctx, registry, "MULTI");
        assert res.equals(RespToken.OK);

        res = exec(ctx, registry, "SET", "tx_key1", "val1");
        assert res.equals(RespToken.QUEUED);

        res = exec(ctx, registry, "SET", "tx_key2", "val2");
        assert res.equals(RespToken.QUEUED);

        res = exec(ctx, registry, "EXEC");
        assert res.getType() == RespToken.Type.ARRAY;
        assert res.getArrayValue().size() == 2;
        assert res.getArrayValue().get(0).equals(RespToken.OK);
        assert res.getArrayValue().get(1).equals(RespToken.OK);

        res = exec(ctx, registry, "GET", "tx_key1");
        assert "val1".equals(res.getStringValue());
    }

    private static void testDiscard(CommandContext ctx, CommandRegistry registry) {
        exec(ctx, registry, "SET", "discard_key", "original");
        exec(ctx, registry, "MULTI");
        exec(ctx, registry, "SET", "discard_key", "modified");
        RespToken res = exec(ctx, registry, "DISCARD");
        assert res.equals(RespToken.OK);

        res = exec(ctx, registry, "GET", "discard_key");
        assert "original".equals(res.getStringValue());
    }

    private static void testWatchSuccess(CommandContext ctx, CommandRegistry registry) {
        exec(ctx, registry, "SET", "watched_key", "100");
        exec(ctx, registry, "WATCH", "watched_key");
        exec(ctx, registry, "MULTI");
        exec(ctx, registry, "INCR", "watched_key");
        RespToken res = exec(ctx, registry, "EXEC");
        assert res.getType() == RespToken.Type.ARRAY;
        assert res.getArrayValue().size() == 1;
        assert res.getArrayValue().get(0).getIntValue() == 101;
    }

    private static void testWatchFailure(CommandContext ctx1, CommandContext ctx2, CommandRegistry registry) {
        exec(ctx1, registry, "SET", "watched_key2", "100");
        exec(ctx1, registry, "WATCH", "watched_key2");

        // Client 2 modifies the key before Client 1 executes MULTI
        exec(ctx2, registry, "SET", "watched_key2", "200");

        exec(ctx1, registry, "MULTI");
        exec(ctx1, registry, "INCR", "watched_key2");
        RespToken res = exec(ctx1, registry, "EXEC");

        // Optimistic locking failure should return null array
        assert res.getType() == RespToken.Type.NULL_ARRAY : "EXEC should return null array on WATCH collision";

        res = exec(ctx1, registry, "GET", "watched_key2");
        assert "200".equals(res.getStringValue());
    }
}
