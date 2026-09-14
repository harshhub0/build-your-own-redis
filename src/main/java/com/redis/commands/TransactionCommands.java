package com.redis.commands;

import com.redis.net.ClientConnection;
import com.redis.resp.RespToken;
import com.redis.storage.RedisDatabase;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handlers for MULTI, EXEC, DISCARD, WATCH, UNWATCH transaction commands.
 */
public class TransactionCommands {

    public static RespToken multi(CommandContext ctx, List<byte[]> args) {
        ClientConnection client = ctx.getClient();
        if (client == null) {
            return RespToken.error("ERR MULTI requires active client connection");
        }
        if (client.isInTransaction()) {
            return RespToken.error("ERR MULTI calls can not be nested");
        }
        client.startTransaction();
        return RespToken.OK;
    }

    public static RespToken discard(CommandContext ctx, List<byte[]> args) {
        ClientConnection client = ctx.getClient();
        if (client == null || !client.isInTransaction()) {
            return RespToken.error("ERR DISCARD without MULTI");
        }
        client.resetTransaction();
        return RespToken.OK;
    }

    public static RespToken watch(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'watch' command");
        ClientConnection client = ctx.getClient();
        if (client == null) return RespToken.error("ERR WATCH requires client connection");
        if (client.isInTransaction()) {
            return RespToken.error("ERR WATCH inside MULTI is not allowed");
        }

        RedisDatabase db = ctx.getDatabase();
        int dbIdx = client.getCurrentDbIndex();
        for (byte[] arg : args) {
            String key = new String(arg, StandardCharsets.UTF_8);
            long ver = db.getKeyVersion(key);
            client.watchKey(dbIdx, key, ver);
        }
        return RespToken.OK;
    }

    public static RespToken unwatch(CommandContext ctx, List<byte[]> args) {
        ClientConnection client = ctx.getClient();
        if (client != null) {
            client.unwatchAll();
        }
        return RespToken.OK;
    }

    public static RespToken exec(CommandContext ctx, List<byte[]> args, CommandRegistry registry) {
        ClientConnection client = ctx.getClient();
        if (client == null || !client.isInTransaction()) {
            return RespToken.error("ERR EXEC without MULTI");
        }

        // Check WATCH validity
        if (!client.checkWatchedKeys(ctx.getDataStore())) {
            client.resetTransaction();
            return RespToken.nullArray(); // Optimistic locking collision
        }

        List<ClientConnection.QueuedCommand> queue = new ArrayList<>(client.getTransactionQueue());
        client.resetTransaction(); // Reset transaction state before execution

        List<RespToken> results = new ArrayList<>(queue.size());
        for (ClientConnection.QueuedCommand cmd : queue) {
            RespToken result = registry.executeDirect(ctx, cmd.commandName, cmd.args);
            results.add(result);
        }

        return RespToken.array(results);
    }
}
