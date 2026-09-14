package com.redis.commands;

import com.redis.resp.RespToken;
import com.redis.storage.RedisDatabase;
import com.redis.storage.RedisType;
import com.redis.storage.RedisValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Handlers for Redis List data type commands.
 */
public class ListCommands {

    public static RespToken lpush(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'lpush' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);

        LinkedList<byte[]> list;
        if (val == null) {
            val = RedisValue.list();
            db.put(key, val);
            list = val.getAsList();
        } else {
            if (val.getType() != RedisType.LIST) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            list = val.getAsList();
        }

        for (int i = 1; i < args.size(); i++) {
            list.addFirst(args.get(i));
        }
        return RespToken.integer(list.size());
    }

    public static RespToken rpush(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'rpush' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);

        LinkedList<byte[]> list;
        if (val == null) {
            val = RedisValue.list();
            db.put(key, val);
            list = val.getAsList();
        } else {
            if (val.getType() != RedisType.LIST) {
                return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            list = val.getAsList();
        }

        for (int i = 1; i < args.size(); i++) {
            list.addLast(args.get(i));
        }
        return RespToken.integer(list.size());
    }

    public static RespToken lpushx(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'lpushx' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        LinkedList<byte[]> list = val.getAsList();
        for (int i = 1; i < args.size(); i++) {
            list.addFirst(args.get(i));
        }
        return RespToken.integer(list.size());
    }

    public static RespToken rpushx(CommandContext ctx, List<byte[]> args) {
        if (args.size() < 2) return RespToken.error("ERR wrong number of arguments for 'rpushx' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        LinkedList<byte[]> list = val.getAsList();
        for (int i = 1; i < args.size(); i++) {
            list.addLast(args.get(i));
        }
        return RespToken.integer(list.size());
    }

    public static RespToken lpop(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'lpop' command");
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
        if (val == null) {
            return multi ? RespToken.nullArray() : RespToken.nullBulkString();
        }
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        LinkedList<byte[]> list = val.getAsList();
        if (list.isEmpty()) {
            return multi ? RespToken.nullArray() : RespToken.nullBulkString();
        }

        if (!multi) {
            byte[] item = list.removeFirst();
            if (list.isEmpty()) {
                db.delete(key);
            }
            return RespToken.bulkString(item);
        } else {
            List<RespToken> popped = new ArrayList<>();
            while (count > 0 && !list.isEmpty()) {
                popped.add(RespToken.bulkString(list.removeFirst()));
                count--;
            }
            if (list.isEmpty()) {
                db.delete(key);
            }
            return RespToken.array(popped);
        }
    }

    public static RespToken rpop(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'rpop' command");
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
        if (val == null) {
            return multi ? RespToken.nullArray() : RespToken.nullBulkString();
        }
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        LinkedList<byte[]> list = val.getAsList();
        if (list.isEmpty()) {
            return multi ? RespToken.nullArray() : RespToken.nullBulkString();
        }

        if (!multi) {
            byte[] item = list.removeLast();
            if (list.isEmpty()) {
                db.delete(key);
            }
            return RespToken.bulkString(item);
        } else {
            List<RespToken> popped = new ArrayList<>();
            while (count > 0 && !list.isEmpty()) {
                popped.add(RespToken.bulkString(list.removeLast()));
                count--;
            }
            if (list.isEmpty()) {
                db.delete(key);
            }
            return RespToken.array(popped);
        }
    }

    public static RespToken llen(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'llen' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return RespToken.integer(val.getAsList().size());
    }

    public static RespToken lrange(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'lrange' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long start, stop;
        try {
            start = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            stop = Long.parseLong(new String(args.get(2), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.array(List.of());
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        LinkedList<byte[]> list = val.getAsList();
        int size = list.size();
        if (size == 0) return RespToken.array(List.of());

        if (start < 0) start = size + start;
        if (stop < 0) stop = size + stop;

        if (start < 0) start = 0;
        if (start >= size || start > stop) return RespToken.array(List.of());
        if (stop >= size) stop = size - 1;

        List<RespToken> res = new ArrayList<>();
        int idx = 0;
        for (byte[] item : list) {
            if (idx >= start && idx <= stop) {
                res.add(RespToken.bulkString(item));
            }
            if (idx > stop) break;
            idx++;
        }
        return RespToken.array(res);
    }

    public static RespToken lindex(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'lindex' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long index;
        try {
            index = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }

        RedisValue val = ctx.getDatabase().get(key);
        if (val == null) return RespToken.nullBulkString();
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        LinkedList<byte[]> list = val.getAsList();
        int size = list.size();
        if (index < 0) index = size + index;
        if (index < 0 || index >= size) {
            return RespToken.nullBulkString();
        }

        return RespToken.bulkString(list.get((int) index));
    }

    public static RespToken lset(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'lset' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long index;
        try {
            index = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
        byte[] value = args.get(2);

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.error("ERR no such key");
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        LinkedList<byte[]> list = val.getAsList();
        int size = list.size();
        if (index < 0) index = size + index;
        if (index < 0 || index >= size) {
            return RespToken.error("ERR index out of range");
        }

        list.set((int) index, value);
        return RespToken.OK;
    }

    public static RespToken lrem(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'lrem' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long count;
        try {
            count = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
        byte[] target = args.get(2);

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.ZERO;
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        LinkedList<byte[]> list = val.getAsList();
        long removed = 0;

        if (count == 0) {
            // Remove all
            var it = list.iterator();
            while (it.hasNext()) {
                if (Arrays.equals(it.next(), target)) {
                    it.remove();
                    removed++;
                }
            }
        } else if (count > 0) {
            // Remove from head to tail
            var it = list.iterator();
            while (it.hasNext() && removed < count) {
                if (Arrays.equals(it.next(), target)) {
                    it.remove();
                    removed++;
                }
            }
        } else {
            // Remove from tail to head
            var it = list.descendingIterator();
            long absCount = -count;
            while (it.hasNext() && removed < absCount) {
                if (Arrays.equals(it.next(), target)) {
                    it.remove();
                    removed++;
                }
            }
        }

        if (list.isEmpty()) {
            db.delete(key);
        }
        return RespToken.integer(removed);
    }

    public static RespToken ltrim(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'ltrim' command");
        String key = new String(args.get(0), StandardCharsets.UTF_8);
        long start, stop;
        try {
            start = Long.parseLong(new String(args.get(1), StandardCharsets.UTF_8));
            stop = Long.parseLong(new String(args.get(2), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }

        RedisDatabase db = ctx.getDatabase();
        RedisValue val = db.get(key);
        if (val == null) return RespToken.OK;
        if (val.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        LinkedList<byte[]> list = val.getAsList();
        int size = list.size();
        if (start < 0) start = size + start;
        if (stop < 0) stop = size + stop;

        if (start < 0) start = 0;
        if (start >= size || start > stop) {
            db.delete(key);
            return RespToken.OK;
        }
        if (stop >= size) stop = size - 1;

        LinkedList<byte[]> trimmed = new LinkedList<>();
        int idx = 0;
        for (byte[] item : list) {
            if (idx >= start && idx <= stop) {
                trimmed.add(item);
            }
            if (idx > stop) break;
            idx++;
        }
        list.clear();
        list.addAll(trimmed);
        return RespToken.OK;
    }

    public static RespToken rpoplpush(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'rpoplpush' command");
        String srcKey = new String(args.get(0), StandardCharsets.UTF_8);
        String dstKey = new String(args.get(1), StandardCharsets.UTF_8);

        RedisDatabase db = ctx.getDatabase();
        RedisValue srcVal = db.get(srcKey);
        if (srcVal == null) return RespToken.nullBulkString();
        if (srcVal.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }

        LinkedList<byte[]> srcList = srcVal.getAsList();
        if (srcList.isEmpty()) return RespToken.nullBulkString();
        byte[] item = srcList.removeLast();
        if (srcList.isEmpty()) {
            db.delete(srcKey);
        }

        RedisValue dstVal = db.get(dstKey);
        if (dstVal == null) {
            dstVal = RedisValue.list();
            db.put(dstKey, dstVal);
        } else if (dstVal.getType() != RedisType.LIST) {
            return RespToken.error("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        dstVal.getAsList().addFirst(item);

        return RespToken.bulkString(item);
    }
}
