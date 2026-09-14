package com.redis.commands;

import com.redis.net.ClientConnection;
import com.redis.resp.RespToken;
import com.redis.storage.RedisValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command router, transaction queue coordinator, and dispatch registry.
 */
public class CommandRegistry {

    public static class CommandMeta {
        public final Command command;
        public final boolean isWrite;
        public final boolean allowedInPubSub;

        public CommandMeta(Command command, boolean isWrite, boolean allowedInPubSub) {
            this.command = command;
            this.isWrite = isWrite;
            this.allowedInPubSub = allowedInPubSub;
        }
    }

    private final Map<String, CommandMeta> commands = new ConcurrentHashMap<>();

    public CommandRegistry() {
        registerAll();
    }

    public void register(String name, Command cmd, boolean isWrite, boolean allowedInPubSub) {
        commands.put(name.toUpperCase(), new CommandMeta(cmd, isWrite, allowedInPubSub));
    }

    public RespToken dispatch(CommandContext ctx, String cmdName, List<byte[]> args) {
        String upperName = cmdName.toUpperCase();
        ClientConnection client = ctx.getClient();

        // 1. Check Authentication
        if (ctx.getConfig().getRequirePass() != null) {
            if (client != null && !client.isAuthenticated()) {
                if (!"AUTH".equals(upperName) && !"QUIT".equals(upperName)) {
                    return RespToken.error("NOAUTH Authentication required.");
                }
            }
        }

        // 2. Check Pub/Sub subscription restrictions
        if (client != null && client.isSubscribed()) {
            CommandMeta meta = commands.get(upperName);
            if (meta == null || !meta.allowedInPubSub) {
                return RespToken.error("ERR Can't execute '" + cmdName + "': only (P)SUBSCRIBE / (P)UNSUBSCRIBE / PING / QUIT are allowed in this context");
            }
        }

        // 3. Check Transaction Queuing
        if (client != null && client.isInTransaction()) {
            if ("EXEC".equals(upperName)) {
                return TransactionCommands.exec(ctx, args, this);
            } else if ("DISCARD".equals(upperName)) {
                return TransactionCommands.discard(ctx, args);
            } else if ("MULTI".equals(upperName)) {
                return TransactionCommands.multi(ctx, args);
            } else if ("WATCH".equals(upperName)) {
                return TransactionCommands.watch(ctx, args);
            } else if ("UNWATCH".equals(upperName)) {
                return TransactionCommands.unwatch(ctx, args);
            } else if ("QUIT".equals(upperName)) {
                return ServerCommands.quit(ctx, args);
            } else {
                CommandMeta meta = commands.get(upperName);
                if (meta == null) {
                    return RespToken.error("ERR unknown command '" + cmdName + "'");
                }
                client.queueCommand(upperName, args);
                return RespToken.QUEUED;
            }
        }

        // Special handling for EXEC without MULTI
        if ("EXEC".equals(upperName)) {
            return TransactionCommands.exec(ctx, args, this);
        }

        // 4. Standard Direct Execution
        return executeDirect(ctx, upperName, args);
    }

    public RespToken executeDirect(CommandContext ctx, String cmdName, List<byte[]> args) {
        String upperName = cmdName.toUpperCase();
        CommandMeta meta = commands.get(upperName);
        if (meta == null) {
            return RespToken.error("ERR unknown command '" + cmdName + "'");
        }

        try {
            RespToken result = meta.command.execute(ctx, args);
            // Log to AOF on write commands if result was not an error
            if (meta.isWrite && result != null && result.getType() != RespToken.Type.ERROR) {
                int dbIndex = ctx.getClient() != null ? ctx.getClient().getCurrentDbIndex() : 0;
                ctx.getAofEngine().logWriteCommand(dbIndex, upperName, args);
            }
            return result;
        } catch (RedisValue.WrongTypeException e) {
            return RespToken.error(e.getMessage());
        } catch (Exception e) {
            return RespToken.error("ERR " + e.getMessage());
        }
    }

    private void registerAll() {
        // String Commands
        register("SET", StringCommands::set, true, false);
        register("GET", StringCommands::get, false, false);
        register("MSET", StringCommands::mset, true, false);
        register("MGET", StringCommands::mget, false, false);
        register("MSETNX", StringCommands::msetnx, true, false);
        register("SETNX", StringCommands::setnx, true, false);
        register("SETEX", StringCommands::setex, true, false);
        register("PSETEX", StringCommands::psetex, true, false);
        register("GETSET", StringCommands::getset, true, false);
        register("INCR", StringCommands::incr, true, false);
        register("DECR", StringCommands::decr, true, false);
        register("INCRBY", StringCommands::incrby, true, false);
        register("DECRBY", StringCommands::decrby, true, false);
        register("INCRBYFLOAT", StringCommands::incrbyfloat, true, false);
        register("APPEND", StringCommands::append, true, false);
        register("STRLEN", StringCommands::strlen, false, false);

        // Key Commands
        register("DEL", KeyCommands::del, true, false);
        register("EXISTS", KeyCommands::exists, false, false);
        register("TYPE", KeyCommands::type, false, false);
        register("EXPIRE", KeyCommands::expire, true, false);
        register("PEXPIRE", KeyCommands::pexpire, true, false);
        register("EXPIREAT", KeyCommands::expireat, true, false);
        register("PEXPIREAT", KeyCommands::pexpireat, true, false);
        register("TTL", KeyCommands::ttl, false, false);
        register("PTTL", KeyCommands::pttl, false, false);
        register("PERSIST", KeyCommands::persist, true, false);
        register("KEYS", KeyCommands::keys, false, false);
        register("SCAN", KeyCommands::scan, false, false);
        register("RENAME", KeyCommands::rename, true, false);
        register("RENAMENX", KeyCommands::renamenx, true, false);
        register("RANDOMKEY", KeyCommands::randomkey, false, false);
        register("DBSIZE", KeyCommands::dbsize, false, false);

        // List Commands
        register("LPUSH", ListCommands::lpush, true, false);
        register("RPUSH", ListCommands::rpush, true, false);
        register("LPUSHX", ListCommands::lpushx, true, false);
        register("RPUSHX", ListCommands::rpushx, true, false);
        register("LPOP", ListCommands::lpop, true, false);
        register("RPOP", ListCommands::rpop, true, false);
        register("LLEN", ListCommands::llen, false, false);
        register("LRANGE", ListCommands::lrange, false, false);
        register("LINDEX", ListCommands::lindex, false, false);
        register("LSET", ListCommands::lset, true, false);
        register("LREM", ListCommands::lrem, true, false);
        register("LTRIM", ListCommands::ltrim, true, false);
        register("RPOPLPUSH", ListCommands::rpoplpush, true, false);

        // Hash Commands
        register("HSET", HashCommands::hset, true, false);
        register("HGET", HashCommands::hget, false, false);
        register("HDEL", HashCommands::hdel, true, false);
        register("HEXISTS", HashCommands::hexists, false, false);
        register("HLEN", HashCommands::hlen, false, false);
        register("HGETALL", HashCommands::hgetall, false, false);
        register("HKEYS", HashCommands::hkeys, false, false);
        register("HVALS", HashCommands::hvals, false, false);
        register("HMSET", HashCommands::hmset, true, false);
        register("HMGET", HashCommands::hmget, false, false);
        register("HINCRBY", HashCommands::hincrby, true, false);
        register("HINCRBYFLOAT", HashCommands::hincrbyfloat, true, false);
        register("HSETNX", HashCommands::hsetnx, true, false);

        // Set Commands
        register("SADD", SetCommands::sadd, true, false);
        register("SREM", SetCommands::srem, true, false);
        register("SMEMBERS", SetCommands::smembers, false, false);
        register("SISMEMBER", SetCommands::sismember, false, false);
        register("SMISMEMBER", SetCommands::smismember, false, false);
        register("SCARD", SetCommands::scard, false, false);
        register("SPOP", SetCommands::spop, true, false);
        register("SRANDMEMBER", SetCommands::srandmember, false, false);
        register("SUNION", SetCommands::sunion, false, false);
        register("SINTER", SetCommands::sinter, false, false);
        register("SDIFF", SetCommands::sdiff, false, false);
        register("SMOVE", SetCommands::smove, true, false);

        // ZSet Commands
        register("ZADD", ZSetCommands::zadd, true, false);
        register("ZREM", ZSetCommands::zrem, true, false);
        register("ZSCORE", ZSetCommands::zscore, false, false);
        register("ZMSCORE", ZSetCommands::zmscore, false, false);
        register("ZRANK", ZSetCommands::zrank, false, false);
        register("ZREVRANK", ZSetCommands::zrevrank, false, false);
        register("ZRANGE", ZSetCommands::zrange, false, false);
        register("ZREVRANGE", ZSetCommands::zrevrange, false, false);
        register("ZRANGEBYSCORE", ZSetCommands::zrangebyscore, false, false);
        register("ZREVRANGEBYSCORE", ZSetCommands::zrevrangebyscore, false, false);
        register("ZCARD", ZSetCommands::zcard, false, false);
        register("ZCOUNT", ZSetCommands::zcount, false, false);
        register("ZINCRBY", ZSetCommands::zincrby, true, false);
        register("ZPOPMIN", ZSetCommands::zpopmin, true, false);
        register("ZPOPMAX", ZSetCommands::zpopmax, true, false);

        // Server Commands
        register("PING", ServerCommands::ping, false, true);
        register("ECHO", ServerCommands::echo, false, false);
        register("SELECT", ServerCommands::select, false, false);
        register("FLUSHDB", ServerCommands::flushdb, true, false);
        register("FLUSHALL", ServerCommands::flushall, true, false);
        register("TIME", ServerCommands::time, false, false);
        register("AUTH", ServerCommands::auth, false, true);
        register("INFO", ServerCommands::info, false, false);
        register("CONFIG", ServerCommands::config, false, false);
        register("CLIENT", ServerCommands::client, false, false);
        register("COMMAND", ServerCommands::command, false, false);
        register("QUIT", ServerCommands::quit, false, true);
        register("SAVE", ServerCommands::save, false, false);
        register("BGSAVE", ServerCommands::bgsave, false, false);
        register("BGREWRITEAOF", ServerCommands::bgrewriteaof, false, false);
        register("SHUTDOWN", ServerCommands::shutdown, false, false);

        // Transaction Commands
        register("MULTI", TransactionCommands::multi, false, false);
        register("DISCARD", TransactionCommands::discard, false, false);
        register("WATCH", TransactionCommands::watch, false, false);
        register("UNWATCH", TransactionCommands::unwatch, false, false);

        // PubSub Commands
        register("SUBSCRIBE", PubSubCommands::subscribe, false, true);
        register("UNSUBSCRIBE", PubSubCommands::unsubscribe, false, true);
        register("PSUBSCRIBE", PubSubCommands::psubscribe, false, true);
        register("PUNSUBSCRIBE", PubSubCommands::punsubscribe, false, true);
        register("PUBLISH", PubSubCommands::publish, false, false);
        register("PUBSUB", PubSubCommands::pubsub, false, false);
    }
}
