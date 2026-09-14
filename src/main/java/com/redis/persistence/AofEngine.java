package com.redis.persistence;

import com.redis.commands.CommandContext;
import com.redis.commands.CommandRegistry;
import com.redis.config.ServerConfig;
import com.redis.datastructures.ZSet;
import com.redis.datastructures.ZSetEntry;
import com.redis.net.ClientConnection;
import com.redis.resp.RespParser;
import com.redis.resp.RespToken;
import com.redis.resp.RespWriter;
import com.redis.storage.DataStore;
import com.redis.storage.RedisDatabase;
import com.redis.storage.RedisType;
import com.redis.storage.RedisValue;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Append-Only File (AOF) persistence engine.
 * Records write operations to disk and provides compaction and replay recovery.
 */
public class AofEngine {
    private final ServerConfig config;
    private final File aofFile;
    private FileOutputStream aofOutputStream;
    private final ConcurrentLinkedQueue<byte[]> aofQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService syncExecutor;
    private final AtomicBoolean isRewriting = new AtomicBoolean(false);

    public AofEngine(ServerConfig config) {
        this.config = config;
        this.aofFile = config.getAofFile();
        if (config.isAppendOnly()) {
            try {
                if (aofFile.getParentFile() != null) {
                    aofFile.getParentFile().mkdirs();
                }
                this.aofOutputStream = new FileOutputStream(aofFile, true);
            } catch (IOException e) {
                System.err.println("Warning: Could not open AOF file for append: " + e.getMessage());
            }
        }
        this.syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "redis-aof-sync");
            t.setDaemon(true);
            return t;
        });
        if (config.isAppendOnly() && "everysec".equalsIgnoreCase(config.getAofFsync())) {
            syncExecutor.scheduleWithFixedDelay(this::flushAndFsync, 1, 1, TimeUnit.SECONDS);
        }
    }

    public synchronized void logWriteCommand(int dbIndex, String commandName, List<byte[]> args) {
        if (!config.isAppendOnly()) return;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Write SELECT if needed
            List<RespToken> tokens = new ArrayList<>();
            tokens.add(RespToken.bulkString(commandName));
            for (byte[] arg : args) {
                tokens.add(RespToken.bulkString(arg));
            }
            byte[] encoded = RespWriter.encode(RespToken.array(tokens));
            if (aofOutputStream != null) {
                aofOutputStream.write(encoded);
                if ("always".equalsIgnoreCase(config.getAofFsync())) {
                    aofOutputStream.flush();
                    aofOutputStream.getFD().sync();
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing to AOF: " + e.getMessage());
        }
    }

    public synchronized void flushAndFsync() {
        if (aofOutputStream != null) {
            try {
                aofOutputStream.flush();
                aofOutputStream.getFD().sync();
            } catch (IOException ignored) {}
        }
    }

    public boolean rewriteAof(DataStore dataStore) {
        if (!isRewriting.compareAndSet(false, true)) {
            return false; // Rewrite already in progress
        }

        new Thread(() -> {
            File tempAof = new File(config.getDir(), config.getAofFileName() + ".temp");
            try (FileOutputStream fos = new FileOutputStream(tempAof)) {
                int dbCount = dataStore.getDatabaseCount();
                for (int i = 0; i < dbCount; i++) {
                    RedisDatabase db = dataStore.getDatabase(i);
                    Map<String, RedisValue> entries = db.getEntriesUnsafe();
                    if (entries.isEmpty()) continue;

                    // SELECT db
                    fos.write(RespWriter.encode(RespToken.array(List.of(
                            RespToken.bulkString("SELECT"),
                            RespToken.bulkString(Integer.toString(i))
                    ))));

                    for (Map.Entry<String, RedisValue> entry : entries.entrySet()) {
                        String key = entry.getKey();
                        RedisValue val = entry.getValue();
                        if (db.checkPassiveExpiry(key)) continue;

                        Long exp = db.getExpiresUnsafe().get(key);
                        writeEntryToAof(fos, key, val, exp);
                    }
                }
                fos.flush();
                fos.getFD().sync();

                synchronized (this) {
                    if (aofOutputStream != null) {
                        aofOutputStream.close();
                    }
                    Files.move(tempAof.toPath(), aofFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (config.isAppendOnly()) {
                        this.aofOutputStream = new FileOutputStream(aofFile, true);
                    }
                }
            } catch (Exception e) {
                System.err.println("AOF rewrite failed: " + e.getMessage());
                tempAof.delete();
            } finally {
                isRewriting.set(false);
            }
        }, "redis-aof-rewrite").start();

        return true;
    }

    private void writeEntryToAof(FileOutputStream fos, String key, RedisValue val, Long expireAt) throws IOException {
        RedisType type = val.getType();
        switch (type) {
            case STRING:
                byte[] strData = val.getAsString();
                fos.write(RespWriter.encode(RespToken.array(List.of(
                        RespToken.bulkString("SET"),
                        RespToken.bulkString(key),
                        RespToken.bulkString(strData)
                ))));
                break;
            case LIST:
                LinkedList<byte[]> list = val.getAsList();
                if (!list.isEmpty()) {
                    List<RespToken> lpush = new ArrayList<>();
                    lpush.add(RespToken.bulkString("RPUSH"));
                    lpush.add(RespToken.bulkString(key));
                    for (byte[] item : list) {
                        lpush.add(RespToken.bulkString(item));
                    }
                    fos.write(RespWriter.encode(RespToken.array(lpush)));
                }
                break;
            case HASH:
                Map<String, byte[]> hash = val.getAsHash();
                if (!hash.isEmpty()) {
                    List<RespToken> hset = new ArrayList<>();
                    hset.add(RespToken.bulkString("HSET"));
                    hset.add(RespToken.bulkString(key));
                    for (Map.Entry<String, byte[]> hEntry : hash.entrySet()) {
                        hset.add(RespToken.bulkString(hEntry.getKey()));
                        hset.add(RespToken.bulkString(hEntry.getValue()));
                    }
                    fos.write(RespWriter.encode(RespToken.array(hset)));
                }
                break;
            case SET:
                Set<String> set = val.getAsSet();
                if (!set.isEmpty()) {
                    List<RespToken> sadd = new ArrayList<>();
                    sadd.add(RespToken.bulkString("SADD"));
                    sadd.add(RespToken.bulkString(key));
                    for (String m : set) {
                        sadd.add(RespToken.bulkString(m));
                    }
                    fos.write(RespWriter.encode(RespToken.array(sadd)));
                }
                break;
            case ZSET:
                ZSet zset = val.getAsZSet();
                Map<String, Double> zEntries = zset.getEntries();
                if (!zEntries.isEmpty()) {
                    List<RespToken> zadd = new ArrayList<>();
                    zadd.add(RespToken.bulkString("ZADD"));
                    zadd.add(RespToken.bulkString(key));
                    for (Map.Entry<String, Double> zEntry : zEntries.entrySet()) {
                        zadd.add(RespToken.bulkString(Double.toString(zEntry.getValue())));
                        zadd.add(RespToken.bulkString(zEntry.getKey()));
                    }
                    fos.write(RespWriter.encode(RespToken.array(zadd)));
                }
                break;
        }

        if (expireAt != null) {
            fos.write(RespWriter.encode(RespToken.array(List.of(
                    RespToken.bulkString("PEXPIREAT"),
                    RespToken.bulkString(key),
                    RespToken.bulkString(Long.toString(expireAt))
            ))));
        }
    }

    public boolean loadAof(DataStore dataStore, CommandRegistry registry) {
        if (!aofFile.exists() || aofFile.length() == 0) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(aofFile)) {
            byte[] allBytes = fis.readAllBytes();
            ByteBuffer buf = ByteBuffer.wrap(allBytes);

            ClientConnection dummyClient = new ClientConnection(null);
            CommandContext ctx = new CommandContext(config, dataStore, null, dummyClient, this, null, null);

            while (buf.hasRemaining()) {
                RespToken token = RespParser.parse(buf);
                if (token == null) {
                    break;
                }

                if (token.getType() == RespToken.Type.ARRAY && token.getArrayValue() != null) {
                    List<RespToken> parts = token.getArrayValue();
                    if (!parts.isEmpty()) {
                        String cmdName = parts.get(0).getStringValue();
                        List<byte[]> args = new ArrayList<>();
                        for (int i = 1; i < parts.size(); i++) {
                            args.add(parts.get(i).getBytesValue());
                        }
                        registry.executeDirect(ctx, cmdName, args);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Warning: Error recovering from AOF: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        syncExecutor.shutdown();
        flushAndFsync();
        if (aofOutputStream != null) {
            try {
                aofOutputStream.close();
            } catch (IOException ignored) {}
        }
    }
}
