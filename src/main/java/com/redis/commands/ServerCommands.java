package com.redis.commands;

import com.redis.config.ServerConfig;
import com.redis.net.ClientConnection;
import com.redis.resp.RespToken;
import com.redis.storage.DataStore;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handlers for server administration, connection management, metadata, and persistence triggers.
 */
public class ServerCommands {

    public static RespToken ping(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) {
            return RespToken.PONG;
        }
        return RespToken.bulkString(args.get(0));
    }

    public static RespToken echo(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'echo' command");
        return RespToken.bulkString(args.get(0));
    }

    public static RespToken select(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1) return RespToken.error("ERR wrong number of arguments for 'select' command");
        try {
            int index = Integer.parseInt(new String(args.get(0), StandardCharsets.UTF_8));
            if (index < 0 || index >= ctx.getDataStore().getDatabaseCount()) {
                return RespToken.error("ERR DB index is out of range");
            }
            if (ctx.getClient() != null) {
                ctx.getClient().setCurrentDbIndex(index);
            }
            return RespToken.OK;
        } catch (NumberFormatException e) {
            return RespToken.error("ERR value is not an integer or out of range");
        }
    }

    public static RespToken flushdb(CommandContext ctx, List<byte[]> args) {
        ctx.getDatabase().flush();
        return RespToken.OK;
    }

    public static RespToken flushall(CommandContext ctx, List<byte[]> args) {
        ctx.getDataStore().flushAll();
        return RespToken.OK;
    }

    public static RespToken time(CommandContext ctx, List<byte[]> args) {
        long nowMs = System.currentTimeMillis();
        long sec = nowMs / 1000;
        long micro = (nowMs % 1000) * 1000;
        return RespToken.array(List.of(
                RespToken.bulkString(Long.toString(sec)),
                RespToken.bulkString(Long.toString(micro))
        ));
    }

    public static RespToken auth(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 1 && args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'auth' command");
        String required = ctx.getConfig().getRequirePass();
        if (required == null) {
            return RespToken.error("ERR Client sent AUTH, but no password is set");
        }

        String password = new String(args.get(args.size() - 1), StandardCharsets.UTF_8);
        if (required.equals(password)) {
            if (ctx.getClient() != null) {
                ctx.getClient().setAuthenticated(true);
            }
            return RespToken.OK;
        }
        return RespToken.error("ERR invalid password");
    }

    public static RespToken info(CommandContext ctx, List<byte[]> args) {
        long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;

        StringBuilder sb = new StringBuilder();
        sb.append("# Server\r\n");
        sb.append("redis_version:7.2.0-custom-java\r\n");
        sb.append("redis_mode:standalone\r\n");
        sb.append("os:").append(System.getProperty("os.name")).append("\r\n");
        sb.append("arch_bits:64\r\n");
        sb.append("tcp_port:").append(ctx.getConfig().getPort()).append("\r\n");
        sb.append("uptime_in_seconds:").append(uptimeSec).append("\r\n");
        sb.append("uptime_in_days:").append(uptimeSec / 86400).append("\r\n");

        sb.append("\r\n# Clients\r\n");
        sb.append("connected_clients:1\r\n");

        sb.append("\r\n# Memory\r\n");
        sb.append("used_memory:").append(usedMemory).append("\r\n");
        sb.append("used_memory_human:").append(usedMemory / (1024 * 1024)).append("M\r\n");

        sb.append("\r\n# Persistence\r\n");
        sb.append("aof_enabled:").append(ctx.getConfig().isAppendOnly() ? 1 : 0).append("\r\n");

        sb.append("\r\n# Keyspace\r\n");
        DataStore ds = ctx.getDataStore();
        for (int i = 0; i < ds.getDatabaseCount(); i++) {
            int count = ds.getDatabase(i).dbsize();
            if (count > 0) {
                int expCount = ds.getDatabase(i).getExpiresUnsafe().size();
                sb.append("db").append(i).append(":keys=").append(count).append(",expires=").append(expCount).append("\r\n");
            }
        }

        return RespToken.bulkString(sb.toString());
    }

    public static RespToken config(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'config' command");
        String subcmd = new String(args.get(0), StandardCharsets.UTF_8).toUpperCase();

        if ("GET".equals(subcmd)) {
            if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'config|get' command");
            String param = new String(args.get(1), StandardCharsets.UTF_8).toLowerCase();
            ServerConfig cfg = ctx.getConfig();
            List<RespToken> list = new ArrayList<>();

            if (param.equals("dir") || param.equals("*")) {
                list.add(RespToken.bulkString("dir"));
                list.add(RespToken.bulkString(cfg.getDir()));
            }
            if (param.equals("dbfilename") || param.equals("*")) {
                list.add(RespToken.bulkString("dbfilename"));
                list.add(RespToken.bulkString(cfg.getDbFileName()));
            }
            if (param.equals("appendfilename") || param.equals("*")) {
                list.add(RespToken.bulkString("appendfilename"));
                list.add(RespToken.bulkString(cfg.getAofFileName()));
            }
            if (param.equals("appendonly") || param.equals("*")) {
                list.add(RespToken.bulkString("appendonly"));
                list.add(RespToken.bulkString(cfg.isAppendOnly() ? "yes" : "no"));
            }
            if (param.equals("port") || param.equals("*")) {
                list.add(RespToken.bulkString("port"));
                list.add(RespToken.bulkString(Integer.toString(cfg.getPort())));
            }

            return RespToken.array(list);
        } else if ("SET".equals(subcmd)) {
            if (args.size() != 3) return RespToken.error("ERR wrong number of arguments for 'config|set' command");
            String param = new String(args.get(1), StandardCharsets.UTF_8).toLowerCase();
            String val = new String(args.get(2), StandardCharsets.UTF_8);
            if (param.equals("dir")) {
                ctx.getConfig().setDir(val);
            } else if (param.equals("dbfilename")) {
                ctx.getConfig().setDbFileName(val);
            } else if (param.equals("appendfilename")) {
                ctx.getConfig().setAofFileName(val);
            } else if (param.equals("appendonly")) {
                ctx.getConfig().setAppendOnly("yes".equalsIgnoreCase(val) || "true".equalsIgnoreCase(val));
            }
            return RespToken.OK;
        }

        return RespToken.error("ERR unknown CONFIG subcommand");
    }

    public static RespToken client(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'client' command");
        String sub = new String(args.get(0), StandardCharsets.UTF_8).toUpperCase();
        ClientConnection client = ctx.getClient();

        if ("SETNAME".equals(sub)) {
            if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'client|setname' command");
            String name = new String(args.get(1), StandardCharsets.UTF_8);
            if (client != null) {
                client.setName(name);
            }
            return RespToken.OK;
        } else if ("GETNAME".equals(sub)) {
            if (client == null || client.getName().isEmpty()) {
                return RespToken.nullBulkString();
            }
            return RespToken.bulkString(client.getName());
        } else if ("ID".equals(sub)) {
            return RespToken.integer(client != null ? client.getId() : 1);
        } else if ("LIST".equals(sub)) {
            if (client == null) {
                return RespToken.bulkString("id=1 addr=127.0.0.1:0 fd=0 name= age=0 idle=0 db=0\n");
            }
            long age = (System.currentTimeMillis() - client.getConnectedTimeMillis()) / 1000;
            long idle = (System.currentTimeMillis() - client.getLastActivityMillis()) / 1000;
            String info = String.format("id=%d addr=%s name=%s age=%d idle=%d db=%d\n",
                    client.getId(),
                    client.getRemoteAddress() != null ? client.getRemoteAddress() : "127.0.0.1:0",
                    client.getName(),
                    age,
                    idle,
                    client.getCurrentDbIndex());
            return RespToken.bulkString(info);
        }

        return RespToken.OK;
    }

    public static RespToken command(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) {
            return RespToken.array(List.of());
        }
        String sub = new String(args.get(0), StandardCharsets.UTF_8).toUpperCase();
        if ("DOCS".equals(sub)) {
            return RespToken.array(List.of());
        } else if ("COUNT".equals(sub)) {
            return RespToken.integer(50);
        }
        return RespToken.array(List.of());
    }

    public static RespToken quit(CommandContext ctx, List<byte[]> args) {
        if (ctx.getClient() != null) {
            ctx.getClient().queueWrite(RespToken.OK);
            ctx.getClient().close();
        }
        return RespToken.OK;
    }

    public static RespToken save(CommandContext ctx, List<byte[]> args) {
        try {
            ctx.getRdbEngine().save(ctx.getDataStore());
            return RespToken.OK;
        } catch (IOException e) {
            return RespToken.error("ERR " + e.getMessage());
        }
    }

    public static RespToken bgsave(CommandContext ctx, List<byte[]> args) {
        boolean ok = ctx.getRdbEngine().bgsave(ctx.getDataStore());
        return ok ? RespToken.simpleString("Background saving started") : RespToken.error("ERR Background save already in progress");
    }

    public static RespToken bgrewriteaof(CommandContext ctx, List<byte[]> args) {
        boolean ok = ctx.getAofEngine().rewriteAof(ctx.getDataStore());
        return ok ? RespToken.simpleString("Background append only file rewriting started") : RespToken.error("ERR Background AOF rewrite already in progress");
    }

    public static RespToken shutdown(CommandContext ctx, List<byte[]> args) {
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            if (ctx.getShutdownHook() != null) {
                ctx.getShutdownHook().run();
            }
        }).start();
        return RespToken.OK;
    }
}
