package com.redis.commands;

import com.redis.resp.RespToken;
import com.redis.storage.RedisDatabase;
import com.redis.storage.RedisType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Handlers for key lifecycle, metadata, TTL, and scanning commands.
 */
public class KeyCommands {

    public static RespToken del(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'del' command");
        RedisDatabase db = ctx.getDatabase();
        long deletedCount = 0;
        for (byte[] arg : args) {
            String key = new String(arg, StandardCharsets.UTF_8);
            if (db.delete(key)) {
                deletedCount++;
            }
        }
        return RespToken.integer(deletedCount);
    }

    public static RespToken exists(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'exists' command");
        RedisDatabase db = ctx.getDatabase();
        long count = 0;
        for (byte[] arg : args) {
            String key = new String(arg, StandardCharsets.UTF_8);
            if (db.exists(key)) {
                count++;
            }
        }
        return RespToken.integer(count);
    }

    public static RespToken type(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'type' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisType t = ctx.getDatabase().type(key);
        return RespToken.simpleString(t.getTypeName());
    }

    public static RespToken expire(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'expire' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        try {
            long seconds = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            long expireAt = System.currentTimeMillis() + (seconds * 1000);
            boolean success = ctx.getDatabase().setExpire(key, expireAt);
            return success ? RespToken.ONE : RespToken.ZERO;
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
    }

    public static RespToken pexpire(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'pexpire' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        try {
            long ms = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            long expireAt = System.currentTimeMillis() + ms;
            boolean success = ctx.getDatabase().setExpire(key, expireAt);
            return success ? RespToken.ONE : RespToken.ZERO;
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
    }

    public static RespToken expireat(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'expireat' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        try {
            long secAt = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            long expireAt = secAt * 1000;
            boolean success = ctx.getDatabase().setExpire(key, expireAt);
            return success ? RespToken.ONE : RespToken.ZERO;
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
    }

    public static RespToken pexpireat(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'pexpireat' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        try {
            long msAt = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            boolean success = ctx.getDatabase().setExpire(key, msAt);
            return success ? RespToken.ONE : RespToken.ZERO;
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
    }

    public static RespToken ttl(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'ttl' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long ttl = ctx.getDatabase().getTtlSeconds(key);
        return RespToken.integer(ttl);
    }

    public static RespToken pttl(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'pttl' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long pttl = ctx.getDatabase().getPttlMillis(key);
        return RespToken.integer(pttl);
    }

    public static RespToken persist(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'persist' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        boolean res = ctx.getDatabase().persist(key);
        return res ? RespToken.ONE : RespToken.ZERO;
    }

    public static RespToken keys(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'keys' command");
        String pattern = new String(args.get(0), StandardCharsets.UTF_8);
        List<String> matching = ctx.getDatabase().keys(pattern);
        List<RespToken> tokens = new ArrayList<>(matching.size());
        for (String k : matching) {
            tokens.add(RespToken.bulkString(k));
        }
        return RespToken.array(tokens);
    }

    public static RespToken scan(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'scan' command");
        int cursor;
        try {
            cursor = Integer.parseInt(new String(args.get(0), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }

        String matchPattern = "*";
        int count = 10;

        for (int i = 1; i < args.size(); i++) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            if ("MATCH".equals(opt) && i + 1 < args.size()) {
                matchPattern = new String(args.get(++i), StandardCharsets.UTF_8);
            } else if ("COUNT".equals(opt) && i + 1 < args.size()) {
                try {
                    count = Integer.parseInt(new String(args.get(++i), StandardCharsets.UTF_8));
                } catch (NumberFormatException ignored) {}
            }
        }

        List<String> allMatching = ctx.getDatabase().keys(matchPattern);
        int nextCursor = 0;
        List<RespToken> batch = new ArrayList<>();

        if (cursor < allMatching.size()) {
            int end = Math.min(cursor + count, allMatching.size());
            for (int i = cursor; i < end; i++) {
                batch.add(RespToken.bulkString(allMatching.get(i)));
            }
            nextCursor = (end < allMatching.size()) ? end : 0;
        }

        return RespToken.array(List.of(
                RespToken.bulkString(Integer.toString(nextCursor)),
                RespToken.array(batch)
        ));
    }

    public static RespToken rename(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'rename' command");
        String oldKey = new String(args.get(0), StandardCharsets.UTF_8);
        String newKey = new String(args.get(1), StandardCharsets.UTF_8);
        boolean res = ctx.getDatabase().rename(oldKey, newKey);
        if (!res) {
            return RespToken.error("ERR no such key");
        }
        return RespToken.OK;
    }

    public static RespToken renamenx(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'renamenx' command");
        String oldKey = new String(args.get(0), StandardCharsets.UTF_8);
        String newKey = new String(args.get(1), StandardCharsets.UTF_8);
        if (!ctx.getDatabase().exists(oldKey)) {
            return RespToken.error("ERR no such key");
        }
        boolean res = ctx.getDatabase().renameNx(oldKey, newKey);
        return res ? RespToken.ONE : RespToken.ZERO;
    }

    public static RespToken randomkey(CommandContext ctx, List<byte[]> args) {
        String k = ctx.getDatabase().randomKey();
        return k != null ? RespToken.bulkString(k) : RespToken.nullBulkString();
    }

    public static RespToken dbsize(CommandContext ctx, List<byte[]> args) {
        return RespToken.integer(ctx.getDatabase().dbsize());
    }
}
