package com.redis.commands;

import com.redis.resp.RespToken;
import com.redis.storage.RedisDatabase;
import com.redis.storage.RedisType;
import com.redis.storage.RedisValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handlers for Redis String data type commands.
 */
public class StringCommands {

    public static RespToken set(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) {
            return RespToken.error("ERR wrong number of arguments for 'set' command");
        }

        String key = new String(args.get(0), StandardCharsets.UTF_8);
        byte[] value = args.get(1);

        Long expireAtMillis = null;
        boolean nx = false; // Only set if not exists
        boolean xx = false; // Only set if already exists
        boolean getOld = false; // Return old value
        boolean keepTtl = false;

        for (int i = 2; i < args.size(); i++) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            switch (opt) {
                case "EX":
                    if (i + 1 >= args.size()) return RespToken.error("ERR syntax error");
                    try {
                        long sec = Long.parseLong(new String(args.get(++i), StandardCharsets.UTF_8));
                        expireAtMillis = System.currentTimeMillis() + (sec * 1000);
                    } catch (NumberFormatException e) {
                        return RespToken.error("ERR value is not an integer or out of range");
                    }
                    break;
                case "PX":
                    if (i + 1 >= args.size()) return RespToken.error("ERR syntax error");
                    try {
                        long ms = Long.parseLong(new String(args.get(++i), StandardCharsets.UTF_8));
                        expireAtMillis = System.currentTimeMillis() + ms;
                    } catch (NumberFormatException e) {
                        return RespToken.error("ERR value is not an integer or out of range");
                    }
                    break;
                case "EXAT":
                    if (i + 1 >= args.size()) return RespToken.error("ERR syntax error");
                    try {
                        long secAt = Long.parseLong(new String(args.get(++i), StandardCharsets.UTF_8));
                        expireAtMillis = secAt * 1000;
                    } catch (NumberFormatException e) {
                        return RespToken.error("ERR value is not an integer or out of range");
                    }
                    break;
                case "PXAT":
                    if (i + 1 >= args.size()) return RespToken.error("ERR syntax error");
                    try {
                        long msAt = Long.parseLong(new String(args.get(++i), StandardCharsets.UTF_8));
                        expireAtMillis = msAt;
                    } catch (NumberFormatException e) {
                        return RespToken.error("ERR value is not an integer or out of range");
                    }
                    break;
                case "NX":
                    nx = true;
                    break;
                case "XX":
                    xx = true;
                    break;
                case "GET":
                    getOld = true;
                    break;
                case "KEEPTTL":
                    keepTtl = true;
                    break;
                default:
                    return RespToken.error("ERR syntax error");
            }
        }

        if (nx && xx) {
            return RespToken.error("ERR syntax error");
        }

        RedisDatabase db = ctx.getDatabase();
        boolean exists = db.exists(key);

        if (nx && exists) {
            return getOld ? RespToken.nullBulkString() : RespToken.nullBulkString();
        }
        if (xx && !exists) {
            return getOld ? RespToken.nullBulkString() : RespToken.nullBulkString();
        }

        RespToken oldValToken = null;
        if (getOld) {
            RedisValue oldVal = db.get(key);
            if (oldVal == null) {
                oldValToken = RespToken.nullBulkString();
            } else if (oldVal.getType() != RedisType.STRING) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            } else {
                oldValToken = RespToken.bulkString(oldVal.getAsString());
            }
        }

        if (keepTtl && exists) {
            long pttl = db.getPttlMillis(key);
            if (pttl > 0) {
                expireAtMillis = System.currentTimeMillis() + pttl;
            }
        }

        db.put(key, RedisValue.string(value), expireAtMillis);

        if (getOld) {
            return oldValToken;
        }
        return RespToken.OK;
    }

    public static RespToken get(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) {
            return RespToken.error("ERR wrong number of arguments for 'get' command");
        }
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) {
            return RespToken.nullBulkString();
        }
        if (val.getType() != RedisType.STRING) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return RespToken.bulkString(val.getAsString());
    }

    public static RespToken mget(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) {
            return RespToken.error("ERR wrong number of arguments for 'mget' command");
        }
        List<RespToken> results = new ArrayList<>(args.size());
        RedisDatabase db = ctx.getDatabase();
        for (byte[] arg : args) {
            String key = new String(arg, StandardCharsets.UTF_8);
            RedisValue val = db.get(key);
            if (val == null || val.getType() != RedisType.STRING) {
                results.add(RespToken.nullBulkString());
            } else {
                results.add(RespToken.bulkString(val.getAsString()));
            }
        }
        return RespToken.array(results);
    }

    public static RespToken mset(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty() || (args.size() % 2 != 0)) {
            return RespToken.error("ERR wrong number of arguments for 'mset' command");
        }
        RedisDatabase db = ctx.getDatabase();
        for (int i = 0; i < args.size(); i += 2) {
            String key = new String(args.get(i), StandardCharsets.UTF_8);
            byte[] value = args.get(i + 1);
            db.put(key, RedisValue.string(value));
        }
        return RespToken.OK;
    }

    public static RespToken msetnx(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty() || (args.size() % 2 != 0)) {
            return RespToken.error("ERR wrong number of arguments for 'msetnx' command");
        }
        RedisDatabase db = ctx.getDatabase();
        for (int i = 0; i < args.size(); i += 2) {
            String key = new String(args.get(i), StandardCharsets.UTF_8);
            if (db.exists(key)) {
                return RespToken.ZERO;
            }
        }
        for (int i = 0; i < args.size(); i += 2) {
            String key = new String(args.get(i), StandardCharsets.UTF_8);
            byte[] value = args.get(i + 1);
            db.put(key, RedisValue.string(value));
        }
        return RespToken.ONE;
    }

    public static RespToken setnx(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) {
            return RespToken.error("ERR wrong number of arguments for 'setnx' command");
        }
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        byte[] value = args.get(1);
        RedisDatabase db = ctx.getDatabase();
        if (db.exists(key)) {
            return RespToken.ZERO;
        }
        db.put(key, RedisValue.string(value));
        return RespToken.ONE;
    }

    public static RespToken setex(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) {
            return RespToken.error("ERR wrong number of arguments for 'setex' command");
        }
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long sec;
        try {
            sec = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
        if (sec <= 0) {
            return RespToken.error("ERR invalid expire time in 'setex' command");
        }
        byte[] value = args.get(2);
        long expireAt = System.currentTimeMillis() + (sec * 1000);
        ctx.getDatabase().put(key, RedisValue.string(value), expireAt);
        return RespToken.OK;
    }

    public static RespToken psetex(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) {
            return RespToken.error("ERR wrong number of arguments for 'psetex' command");
        }
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long ms;
        try {
            ms = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
        if (ms <= 0) {
            return RespToken.error("ERR invalid expire time in 'psetex' command");
        }
        byte[] value = args.get(2);
        long expireAt = System.currentTimeMillis() + ms;
        ctx.getDatabase().put(key, RedisValue.string(value), expireAt);
        return RespToken.OK;
    }

    public static RespToken getset(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) {
            return RespToken.error("ERR wrong number of arguments for 'getset' command");
        }
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        byte[] newValue = args.get(1);
        RedisDatabase db = ctx.getDatabase();
        RedisValue oldVal = db.get(key);
        db.put(key, RedisValue.string(newValue));
        if (oldVal == null) {
            return RespToken.nullBulkString();
        }
        if (oldVal.getType() != RedisType.STRING) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return RespToken.bulkString(oldVal.getAsString());
    }

    public static RespToken incr(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'incr' command");
        return incrByHelper(ctx, args.get(0), 1L);
    }

    public static RespToken decr(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'decr' command");
        return incrByHelper(ctx, args.get(0), -1L);
    }

    public static RespToken incrby(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'incrby' command");
        try {
            long delta = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            return incrByHelper(ctx, args.get(0), delta);
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
    }

    public static RespToken decrby(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'decrby' command");
        try {
            long delta = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            return incrByHelper(ctx, args.get(0), -delta);
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
    }

    private static RespToken incrByHelper(CommandContext ctx, byte[] keyBytes, long delta) {
        String key = new String(keyBytes, StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        long current = 0;
        if (val != null) {
            if (val.getType() != RedisType.STRING) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            String str = val.getAsStringUtf8();
            try {
                current = Long.parseLong(str);
            } catch (NumberFormatException e) {
                return RespToken.error("ERR value is not an integer or out of range");
            }
        }
        long result = current + delta;
        db.put(key, RedisValue.string(Long.toString(result)));
        return RespToken.integer(result);
    }

    public static RespToken incrbyfloat(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'incrbyfloat' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        double delta;
        try {
            delta = Double.parseDouble(new String(args.get(1), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not a valid float");
        }

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        double current = 0.0;
        if (val != null) {
            if (val.getType() != RedisType.STRING) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            try {
                current = Double.parseDouble(val.getAsStringUtf8());
            } catch (NumberFormatException e) {
                return RespToken.error("ERR value is not a valid float");
            }
        }
        double result = current + delta;
        String resStr = (result == (long) result) ? Long.toString((long) result) : Double.toString(result);
        db.put(key, RedisValue.string(resStr));
        return RespToken.bulkString(resStr);
    }

    public static RespToken append(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'append' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        byte[] appendBytes = args.get(1);

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) {
            db.put(key, RedisValue.string(appendBytes));
            return RespToken.integer(appendBytes.length);
        }
        if (val.getType() != RedisType.STRING) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        byte[] existing = val.getAsString();
        byte[] combined = new byte[existing.length + appendBytes.length];
        System.arraycopy(existing, 0, combined, 0, existing.length);
        System.arraycopy(appendBytes, 0, combined, existing.length, appendBytes.length);
        db.put(key, RedisValue.string(combined));
        return RespToken.integer(combined.length);
    }

    public static RespToken strlen(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'strlen' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) {
            return RespToken.ZERO;
        }
        if (val.getType() != RedisType.STRING) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return RespToken.integer(val.getAsString().length);
    }
}
