package com.redis.commands;

import com.redis.resp.RespToken;
import com.redis.storage.RedisDatabase;
import com.redis.storage.RedisType;
import com.redis.storage.RedisValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handlers for Redis Hash data type commands.
 */
public class HashCommands {

    public static RespToken hset(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 3 || (args.size() % 2 == 0)) {
            return RespToken.error("ERR wrong number of arguments for 'hset' command");
        }
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);

        Map<String, byte[]> hash;
        if (val == null) {
            val = RedisValue.hash();
            db.put(key, val);
            hash = val.getAsHash();
        } else {
            if (val.getType() != RedisType.HASH) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            hash = val.getAsHash();
        }

        long added = 0;
        for (int i = 1; i < args.size(); i += 2) {
            String field = new String(args.get(i), StandardCharsets.UTF_8);
            byte[] fieldVal = args.get(i + 1);
            if (hash.put(field, fieldVal) == null) {
                added++;
            }
        }
        return RespToken.integer(added);
    }

    public static RespToken hget(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'hget' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String field = new String(args.get(1), StandardCharsets.UTF_8);

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.nullBulkString();
        if (val.getType() != RedisType.HASH) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        byte[] fieldVal = val.getAsHash().get(field);
        return fieldVal != null ? RespToken.bulkString(fieldVal) : RespToken.nullBulkString();
    }

    public static RespToken hdel(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'hdel' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.HASH) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Map<String, byte[]> hash = val.getAsHash();
        long deleted = 0;
        for (int i = 1; i < args.size(); i++) {
            String field = new String(args.get(i), StandardCharsets.UTF_8);
            if (hash.remove(field) != null) {
                deleted++;
            }
        }
        if (hash.isEmpty()) {
            db.delete(key);
        }
        return RespToken.integer(deleted);
    }

    public static RespToken hexists(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'hexists' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String field = new String(args.get(1), StandardCharsets.UTF_8);

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.HASH) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        return val.getAsHash().containsKey(field) ? RespToken.ONE : RespToken.ZERO;
    }

    public static RespToken hlen(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'hlen' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.HASH) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return RespToken.integer(val.getAsHash().size());
    }

    public static RespToken hgetall(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'hgetall' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.HASH) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Map<String, byte[]> hash = val.getAsHash();
        List<RespToken> list = new ArrayList<>(hash.size() * 2);
        for (Map.Entry<String, byte[]> entry : hash.entrySet()) {
            list.add(RespToken.bulkString(entry.getKey()));
            list.add(RespToken.bulkString(entry.getValue()));
        }
        return RespToken.array(list);
    }

    public static RespToken hkeys(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'hkeys' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.HASH) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        List<RespToken> list = new ArrayList<>();
        for (String f : val.getAsHash().keySet()) {
            list.add(RespToken.bulkString(f));
        }
        return RespToken.array(list);
    }

    public static RespToken hvals(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'hvals' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.HASH) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        List<RespToken> list = new ArrayList<>();
        for (byte[] fVal : val.getAsHash().values()) {
            list.add(RespToken.bulkString(fVal));
        }
        return RespToken.array(list);
    }

    public static RespToken hmget(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'hmget' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val != null && val.getType() != RedisType.HASH) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Map<String, byte[]> hash = (val != null) ? val.getAsHash() : null;
        List<RespToken> res = new ArrayList<>(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            String field = new String(args.get(i), StandardCharsets.UTF_8);
            byte[] fVal = (hash != null) ? hash.get(field) : null;
            res.add(fVal != null ? RespToken.bulkString(fVal) : RespToken.nullBulkString());
        }
        return RespToken.array(res);
    }

    public static RespToken hmset(CommandContext ctx, List<byte[]> args) {
        RespToken res = hset(ctx, args);
        return (res.getType() == RespToken.Type.ERROR) ? res : RespToken.OK;
    }

    public static RespToken hincrby(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'hincrby' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String field = new String(args.get(1), StandardCharsets.UTF_8);
        long delta;
        try {
            delta = Long.parseLong(new String(args.get(2), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        Map<String, byte[]> hash;
        if (val == null) {
            val = RedisValue.hash();
            db.put(key, val);
            hash = val.getAsHash();
        } else {
            if (val.getType() != RedisType.HASH) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            hash = val.getAsHash();
        }

        byte[] existing = hash.get(field);
        long cur = 0;
        if (existing != null) {
            try {
                cur = Long.parseLong(new String(existing, StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return RespToken.error("ERR hash value is not an integer");
            }
        }
        long newVal = cur + delta;
        hash.put(field, Long.toString(newVal).getBytes(StandardCharsets.UTF_8));
        return RespToken.integer(newVal);
    }

    public static RespToken hincrbyfloat(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'hincrbyfloat' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String field = new String(args.get(1), StandardCharsets.UTF_8);
        double delta;
        try {
            delta = Double.parseDouble(new String(args.get(2), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not a valid float");
        }

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        Map<String, byte[]> hash;
        if (val == null) {
            val = RedisValue.hash();
            db.put(key, val);
            hash = val.getAsHash();
        } else {
            if (val.getType() != RedisType.HASH) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            hash = val.getAsHash();
        }

        byte[] existing = hash.get(field);
        double cur = 0.0;
        if (existing != null) {
            try {
                cur = Double.parseDouble(new String(existing, StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return RespToken.error("ERR hash value is not a float");
            }
        }
        double newVal = cur + delta;
        String resStr = (newVal == (long) newVal) ? Long.toString((long) newVal) : Double.toString(newVal);
        hash.put(field, resStr.getBytes(StandardCharsets.UTF_8));
        return RespToken.bulkString(resStr);
    }

    public static RespToken hsetnx(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'hsetnx' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String field = new String(args.get(1), StandardCharsets.UTF_8);
        byte[] fieldVal = args.get(2);

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        Map<String, byte[]> hash;
        if (val == null) {
            val = RedisValue.hash();
            db.put(key, val);
            hash = val.getAsHash();
        } else {
            if (val.getType() != RedisType.HASH) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            hash = val.getAsHash();
        }

        if (hash.containsKey(field)) {
            return RespToken.ZERO;
        }
        hash.put(field, fieldVal);
        return RespToken.ONE;
    }
}
