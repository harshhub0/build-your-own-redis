package com.redis.commands;

import com.redis.resp.RespToken;
import com.redis.storage.RedisDatabase;
import com.redis.storage.RedisType;
import com.redis.storage.RedisValue;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handlers for Redis Set data type commands.
 */
public class SetCommands {

    public static RespToken sadd(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'sadd' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);

        Set<String> set;
        if (val == null) {
            val = RedisValue.set();
            db.put(key, val);
            set = val.getAsSet();
        } else {
            if (val.getType() != RedisType.SET) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            set = val.getAsSet();
        }

        long added = 0;
        for (int i = 1; i < args.size(); i++) {
            String member = new String(args.get(i), StandardCharsets.UTF_8);
            if (set.add(member)) {
                added++;
            }
        }
        return RespToken.integer(added);
    }

    public static RespToken srem(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'srem' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Set<String> set = val.getAsSet();
        long removed = 0;
        for (int i = 1; i < args.size(); i++) {
            String member = new String(args.get(i), StandardCharsets.UTF_8);
            if (set.remove(member)) {
                removed++;
            }
        }
        if (set.isEmpty()) {
            db.delete(key);
        }
        return RespToken.integer(removed);
    }

    public static RespToken smembers(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'smembers' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Set<String> set = val.getAsSet();
        List<RespToken> res = new ArrayList<>(set.size());
        for (String m : set) {
            res.add(RespToken.bulkString(m));
        }
        return RespToken.array(res);
    }

    public static RespToken sismember(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'sismember' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String member = new String(args.get(1), StandardCharsets.UTF_8);

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        return val.getAsSet().contains(member) ? RespToken.ONE : RespToken.ZERO;
    }

    public static RespToken smismember(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'smismember' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val != null && val.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Set<String> set = (val != null) ? val.getAsSet() : null;
        List<RespToken> res = new ArrayList<>(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            String m = new String(args.get(i), StandardCharsets.UTF_8);
            if (set != null && set.contains(m)) {
                res.add(RespToken.ONE);
            } else {
                res.add(RespToken.ZERO);
            }
        }
        return RespToken.array(res);
    }

    public static RespToken scard(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'scard' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return RespToken.integer(val.getAsSet().size());
    }

    public static RespToken spop(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'spop' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        int count = 1;
        boolean multi = (args.size() > 1);
        if (multi) {
            try {
                count = Integer.parseInt(new String(args.get(1), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return RespToken.error("ERR value is not an integer or out of range");
            }
        }

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return multi ? RespToken.array(List.of()) : RespToken.nullBulkString();
        if (val.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Set<String> set = val.getAsSet();
        if (set.isEmpty()) return multi ? RespToken.array(List.of()) : RespToken.nullBulkString();

        List<RespToken> popped = new ArrayList<>();
        var it = set.iterator();
        while (it.hasNext() && count > 0) {
            String m = it.next();
            it.remove();
            popped.add(RespToken.bulkString(m));
            count--;
        }

        if (set.isEmpty()) {
            db.delete(key);
        }

        return multi ? RespToken.array(popped) : popped.get(0);
    }

    public static RespToken srandmember(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'srandmember' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        boolean multi = (args.size() > 1);
        int count = 1;
        if (multi) {
            try {
                count = Integer.parseInt(new String(args.get(1), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return RespToken.error("ERR value is not an integer or out of range");
            }
        }

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return multi ? RespToken.array(List.of()) : RespToken.nullBulkString();
        if (val.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Set<String> set = val.getAsSet();
        if (set.isEmpty()) return multi ? RespToken.array(List.of()) : RespToken.nullBulkString();

        List<String> list = new ArrayList<>(set);
        Collections.shuffle(list);

        if (!multi) {
            return RespToken.bulkString(list.get(0));
        }

        List<RespToken> res = new ArrayList<>();
        if (count >= 0) {
            // Distinct elements up to count
            int limit = Math.min(count, list.size());
            for (int i = 0; i < limit; i++) {
                res.add(RespToken.bulkString(list.get(i)));
            }
        } else {
            // Can repeat elements
            Random r = new Random();
            int absCount = -count;
            for (int i = 0; i < absCount; i++) {
                res.add(RespToken.bulkString(list.get(r.nextInt(list.size()))));
            }
        }
        return RespToken.array(res);
    }

    public static RespToken sunion(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'sunion' command");
        Set<String> union = new HashSet<>();
        RedisDatabase db = ctx.getDatabase();
        for (byte[] arg : args) {
            String key = new String(arg, StandardCharsets.UTF_8);
            RedisValue val = db.get(key);
            if (val != null) {
                if (val.getType() != RedisType.SET) {
                    return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
                }
                union.addAll(val.getAsSet());
            }
        }

        List<RespToken> res = new ArrayList<>(union.size());
        for (String m : union) {
            res.add(RespToken.bulkString(m));
        }
        return RespToken.array(res);
    }

    public static RespToken sinter(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'sinter' command");
        RedisDatabase db = ctx.getDatabase();
        Set<String> intersection = null;

        for (byte[] arg : args) {
            String key = new String(arg, StandardCharsets.UTF_8);
            RedisValue val = db.get(key);
            if (val == null) {
                return RespToken.array(List.of());
            }
            if (val.getType() != RedisType.SET) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            if (intersection == null) {
                intersection = new HashSet<>(val.getAsSet());
            } else {
                intersection.retainAll(val.getAsSet());
            }
        }

        if (intersection == null) return RespToken.array(List.of());
        List<RespToken> res = new ArrayList<>(intersection.size());
        for (String m : intersection) {
            res.add(RespToken.bulkString(m));
        }
        return RespToken.array(res);
    }

    public static RespToken sdiff(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'sdiff' command");
        RedisDatabase db = ctx.getDatabase();
        String firstKey = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue firstVal = db.get(firstKey);
        if (firstVal == null) return RespToken.array(List.of());
        if (firstVal.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Set<String> diff = new HashSet<>(firstVal.getAsSet());
        for (int i = 1; i < args.size(); i++) {
            String key = new String(args.get(i), StandardCharsets.UTF_8);
            RedisValue val = db.get(key);
            if (val != null) {
                if (val.getType() != RedisType.SET) {
                    return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
                }
                diff.removeAll(val.getAsSet());
            }
        }

        List<RespToken> res = new ArrayList<>(diff.size());
        for (String m : diff) {
            res.add(RespToken.bulkString(m));
        }
        return RespToken.array(res);
    }

    public static RespToken smove(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'smove' command");
        String srcKey = new String(args.get(0), StandardCharsets.UTF_8);
        String dstKey = new String(args.get(1), StandardCharsets.UTF_8);
        String member = new String(args.get(2), StandardCharsets.UTF_8);

        RedisDatabase db = ctx.getDatabase();
        RedisValue srcVal = db.get(srcKey);
        if (srcVal == null) return RespToken.ZERO;
        if (srcVal.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Set<String> srcSet = srcVal.getAsSet();
        if (!srcSet.remove(member)) {
            return RespToken.ZERO;
        }
        if (srcSet.isEmpty()) {
            db.delete(srcKey);
        }

        RedisValue dstVal = db.get(dstKey);
        if (dstVal == null) {
            dstVal = RedisValue.set();
            db.put(dstKey, dstVal);
        } else if (dstVal.getType() != RedisType.SET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        dstVal.getAsSet().add(member);
        return RespToken.ONE;
    }
}
