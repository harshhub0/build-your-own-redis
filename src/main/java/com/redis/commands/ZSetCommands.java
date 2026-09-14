package com.redis.commands;

import com.redis.datastructures.ZSet;
import com.redis.datastructures.ZSetEntry;
import com.redis.resp.RespToken;
import com.redis.storage.RedisDatabase;
import com.redis.storage.RedisType;
import com.redis.storage.RedisValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handlers for Redis Sorted Set (ZSet) data type commands.
 */
public class ZSetCommands {

    public static RespToken zadd(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 3) return RespToken.error("ERR wrong number of arguments for 'zadd' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);

        boolean nx = false;
        boolean xx = false;
        boolean ch = false;
        boolean incr = false;

        int idx = 1;
        while (idx < args.size()) {
            String opt = new String(args.get(idx), StandardCharsets.UTF_8).toUpperCase();
            if ("NX".equals(opt)) {
                nx = true;
                idx++;
            } else if ("XX".equals(opt)) {
                xx = true;
                idx++;
            } else if ("CH".equals(opt)) {
                ch = true;
                idx++;
            } else if ("INCR".equals(opt)) {
                incr = true;
                idx++;
            } else {
                break;
            }
        }

        int scoreMemberCount = args.size() - idx;
        if (scoreMemberCount < 2 || (scoreMemberCount % 2 != 0)) {
            return RespToken.error("ERR syntax error");
        }

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        ZSet zset;
        if (val == null) {
            val = RedisValue.zset();
            db.put(key, val);
            zset = val.getAsZSet();
        } else {
            if (val.getType() != RedisType.ZSET) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            zset = val.getAsZSet();
        }

        if (incr) {
            if (scoreMemberCount != 2) {
                return RespToken.error("ERR INCR option supports a single increment-element pair");
            }
            double delta;
            try {
                delta = Double.parseDouble(new String(args.get(idx), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return RespToken.error("ERR value is not a valid float");
            }
            String member = new String(args.get(idx + 1), StandardCharsets.UTF_8);
            boolean exists = (zset.getScore(member) != null);
            if (nx && exists) return RespToken.nullBulkString();
            if (xx && !exists) return RespToken.nullBulkString();

            double newScore = zset.incrBy(delta, member);
            String scoreStr = (newScore == (long) newScore) ? Long.toString((long) newScore) : Double.toString(newScore);
            return RespToken.bulkString(scoreStr);
        }

        long added = 0;
        long changed = 0;

        for (int i = idx; i < args.size(); i += 2) {
            double score;
            try {
                score = Double.parseDouble(new String(args.get(i), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return RespToken.error("ERR value is not a valid float");
            }
            String member = new String(args.get(i + 1), StandardCharsets.UTF_8);
            Double oldScore = zset.getScore(member);
            boolean exists = (oldScore != null);

            if (nx && exists) continue;
            if (xx && !exists) continue;

            boolean isNew = zset.add(score, member);
            if (isNew) {
                added++;
            } else if (oldScore != score) {
                changed++;
            }
        }

        return RespToken.integer(ch ? (added + changed) : added);
    }

    public static RespToken zrem(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'zrem' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        ZSet zset = val.getAsZSet();
        long removed = 0;
        for (int i = 1; i < args.size(); i++) {
            String member = new String(args.get(i), StandardCharsets.UTF_8);
            if (zset.remove(member)) {
                removed++;
            }
        }
        if (zset.size() == 0) {
            db.delete(key);
        }
        return RespToken.integer(removed);
    }

    public static RespToken zscore(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'zscore' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String member = new String(args.get(1), StandardCharsets.UTF_8);

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.nullBulkString();
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        Double score = val.getAsZSet().getScore(member);
        if (score == null) return RespToken.nullBulkString();
        String str = (score == (long)(double)score) ? Long.toString((long)(double)score) : Double.toString(score);
        return RespToken.bulkString(str);
    }

    public static RespToken zmscore(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'zmscore' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val != null && val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        ZSet zset = (val != null) ? val.getAsZSet() : null;
        List<RespToken> res = new ArrayList<>(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            String member = new String(args.get(i), StandardCharsets.UTF_8);
            Double score = (zset != null) ? zset.getScore(member) : null;
            if (score == null) {
                res.add(RespToken.nullBulkString());
            } else {
                String str = (score == (long)(double)score) ? Long.toString((long)(double)score) : Double.toString(score);
                res.add(RespToken.bulkString(str));
            }
        }
        return RespToken.array(res);
    }

    public static RespToken zrank(CommandContext ctx, List<byte[]> args) {
        return zrankHelper(ctx, args, false);
    }

    public static RespToken zrevrank(CommandContext ctx, List<byte[]> args) {
        return zrankHelper(ctx, args, true);
    }

    private static RespToken zrankHelper(CommandContext ctx, List<byte[]> args, boolean reverse) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String member = new String(args.get(1), StandardCharsets.UTF_8);

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.nullBulkString();
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        long rank = val.getAsZSet().getRank(member, reverse);
        return (rank == -1) ? RespToken.nullBulkString() : RespToken.integer(rank);
    }

    public static RespToken zrange(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 3) return RespToken.error("ERR wrong number of arguments for 'zrange' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long start, stop;
        try {
            start = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            stop = Long.parseLong(new String(args.get(2), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }

        boolean withScores = false;
        boolean reverse = false;

        for (int i = 3; i < args.size(); i++) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            if ("WITHSCORES".equals(opt)) {
                withScores = true;
            } else if ("REV".equals(opt)) {
                reverse = true;
            }
        }

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        List<ZSetEntry> entries = val.getAsZSet().getRange(start, stop, reverse);
        List<RespToken> tokens = new ArrayList<>(entries.size() * (withScores ? 2 : 1));
        for (ZSetEntry entry : entries) {
            tokens.add(RespToken.bulkString(entry.getMember()));
            if (withScores) {
                double s = entry.getScore();
                String sStr = (s == (long) s) ? Long.toString((long) s) : Double.toString(s);
                tokens.add(RespToken.bulkString(sStr));
            }
        }
        return RespToken.array(tokens);
    }

    public static RespToken zrevrange(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 3) return RespToken.error("ERR wrong number of arguments for 'zrevrange' command");
        List<byte[]> modArgs = new ArrayList<>(args);
        modArgs.add("REV".getBytes(StandardCharsets.UTF_8));
        return zrange(ctx, modArgs);
    }

    public static RespToken zrangebyscore(CommandContext ctx, List<byte[]> args) {
        return zrangeByScoreHelper(ctx, args, false);
    }

    public static RespToken zrevrangebyscore(CommandContext ctx, List<byte[]> args) {
        return zrangeByScoreHelper(ctx, args, true);
    }

    private static RespToken zrangeByScoreHelper(CommandContext ctx, List<byte[]> args, boolean reverse) {
        if (args.size() < 3) return RespToken.error("ERR wrong number of arguments");
        String key = new String(args.get(0), StandardCharsets.UTF_8);

        String minStr = new String(args.get(reverse ? 2 : 1), StandardCharsets.UTF_8);
        String maxStr = new String(args.get(reverse ? 1 : 2), StandardCharsets.UTF_8);

        boolean minInc = true;
        if (minStr.startsWith("(")) {
            minInc = false;
            minStr = minStr.substring(1);
        }
        boolean maxInc = true;
        if (maxStr.startsWith("(")) {
            maxInc = false;
            maxStr = maxStr.substring(1);
        }

        double min = "-inf".equalsIgnoreCase(minStr) ? Double.NEGATIVE_INFINITY : ("+inf".equalsIgnoreCase(minStr) ? Double.POSITIVE_INFINITY : Double.parseDouble(minStr));
        double max = "+inf".equalsIgnoreCase(maxStr) ? Double.POSITIVE_INFINITY : ("-inf".equalsIgnoreCase(maxStr) ? Double.NEGATIVE_INFINITY : Double.parseDouble(maxStr));

        boolean withScores = false;
        long offset = 0;
        long count = Long.MAX_VALUE;

        for (int i = 3; i < args.size(); i++) {
            String opt = new String(args.get(i), StandardCharsets.UTF_8).toUpperCase();
            if ("WITHSCORES".equals(opt)) {
                withScores = true;
            } else if ("LIMIT".equals(opt) && i + 2 < args.size()) {
                offset = Long.parseLong(new String(args.get(++i), StandardCharsets.UTF_8));
                count = Long.parseLong(new String(args.get(++i), StandardCharsets.UTF_8));
            }
        }

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        List<ZSetEntry> entries = val.getAsZSet().getRangeByScore(min, max, minInc, maxInc, offset, count, reverse);
        List<RespToken> tokens = new ArrayList<>(entries.size() * (withScores ? 2 : 1));
        for (ZSetEntry entry : entries) {
            tokens.add(RespToken.bulkString(entry.getMember()));
            if (withScores) {
                double s = entry.getScore();
                String sStr = (s == (long) s) ? Long.toString((long) s) : Double.toString(s);
                tokens.add(RespToken.bulkString(sStr));
            }
        }
        return RespToken.array(tokens);
    }

    public static RespToken zcard(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'zcard' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return RespToken.integer(val.getAsZSet().size());
    }

    public static RespToken zcount(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'zcount' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        String minStr = new String(args.get(1), StandardCharsets.UTF_8);
        String maxStr = new String(args.get(2), StandardCharsets.UTF_8);

        boolean minInc = true;
        if (minStr.startsWith("(")) {
            minInc = false;
            minStr = minStr.substring(1);
        }
        boolean maxInc = true;
        if (maxStr.startsWith("(")) {
            maxInc = false;
            maxStr = maxStr.substring(1);
        }

        double min = "-inf".equalsIgnoreCase(minStr) ? Double.NEGATIVE_INFINITY : Double.parseDouble(minStr);
        double max = "+inf".equalsIgnoreCase(maxStr) ? Double.POSITIVE_INFINITY : Double.parseDouble(maxStr);

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        long cnt = val.getAsZSet().countRange(min, max, minInc, maxInc);
        return RespToken.integer(cnt);
    }

    public static RespToken zincrby(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'zincrby' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        double delta;
        try {
            delta = Double.parseDouble(new String(args.get(1), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not a valid float");
        }
        String member = new String(args.get(2), StandardCharsets.UTF_8);

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        ZSet zset;
        if (val == null) {
            val = RedisValue.zset();
            db.put(key, val);
            zset = val.getAsZSet();
        } else {
            if (val.getType() != RedisType.ZSET) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            zset = val.getAsZSet();
        }

        double newScore = zset.incrBy(delta, member);
        String sStr = (newScore == (long) newScore) ? Long.toString((long) newScore) : Double.toString(newScore);
        return RespToken.bulkString(sStr);
    }

    public static RespToken zpopmin(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'zpopmin' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long count = 1;
        if (args.size() > 1) {
            try {
                count = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return RespToken.error("ERR value is not an integer or out of range");
            }
        }

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        ZSet zset = val.getAsZSet();
        List<ZSetEntry> popped = zset.popMin(count);
        if (zset.size() == 0) {
            db.delete(key);
        }

        List<RespToken> tokens = new ArrayList<>(popped.size() * 2);
        for (ZSetEntry entry : popped) {
            tokens.add(RespToken.bulkString(entry.getMember()));
            double s = entry.getScore();
            String sStr = (s == (long) s) ? Long.toString((long) s) : Double.toString(s);
            tokens.add(RespToken.bulkString(sStr));
        }
        return RespToken.array(tokens);
    }

    public static RespToken zpopmax(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'zpopmax' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long count = 1;
        if (args.size() > 1) {
            try {
                count = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return RespToken.error("ERR value is not an integer or out of range");
            }
        }

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.ZSET) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        ZSet zset = val.getAsZSet();
        List<ZSetEntry> popped = zset.popMax(count);
        if (zset.size() == 0) {
            db.delete(key);
        }

        List<RespToken> tokens = new ArrayList<>(popped.size() * 2);
        for (ZSetEntry entry : popped) {
            tokens.add(RespToken.bulkString(entry.getMember()));
            double s = entry.getScore();
            String sStr = (s == (long) s) ? Long.toString((long) s) : Double.toString(s);
            tokens.add(RespToken.bulkString(sStr));
        }
        return RespToken.array(tokens);
    }
}
