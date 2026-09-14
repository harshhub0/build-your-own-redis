package com.redis.persistence;

import com.redis.config.ServerConfig;
import com.redis.datastructures.ZSet;
import com.redis.datastructures.ZSetEntry;
import com.redis.storage.DataStore;
import com.redis.storage.RedisDatabase;
import com.redis.storage.RedisType;
import com.redis.storage.RedisValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RDB Snapshot persistence engine for point-in-time database backups.
 */
public class RdbEngine {
    private static final byte[] MAGIC = "REDIS0009".getBytes(StandardCharsets.US_ASCII);
    private static final byte OP_SELECT_DB = (byte) 0xFE;
    private static final byte OP_EXPIRE_MS = (byte) 0xFC;
    private static final byte OP_EOF = (byte) 0xFF;

    private static final byte TYPE_STRING = 0;
    private static final byte TYPE_LIST = 1;
    private static final byte TYPE_SET = 2;
    private static final byte TYPE_ZSET = 3;
    private static final byte TYPE_HASH = 4;

    private final ServerConfig config;
    private final File rdbFile;
    private final AtomicBoolean isSaving = new AtomicBoolean(false);

    public RdbEngine(ServerConfig config) {
        this.config = config;
        this.rdbFile = config.getRdbFile();
    }

    public synchronized boolean save(DataStore dataStore) throws IOException {
        File tempFile = new File(config.getDir(), config.getDbFileName() + ".temp");
        if (tempFile.getParentFile() != null) {
            tempFile.getParentFile().mkdirs();
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)))) {
            out.write(MAGIC);

            int dbCount = dataStore.getDatabaseCount();
            for (int i = 0; i < dbCount; i++) {
                RedisDatabase db = dataStore.getDatabase(i);
                Map<String, RedisValue> entries = db.getEntriesUnsafe();
                if (entries.isEmpty()) continue;

                // Select DB
                out.writeByte(OP_SELECT_DB);
                out.writeInt(i);

                long now = System.currentTimeMillis();
                for (Map.Entry<String, RedisValue> entry : entries.entrySet()) {
                    String key = entry.getKey();
                    if (db.checkPassiveExpiry(key)) continue;

                    RedisValue val = entry.getValue();
                    Long exp = db.getExpiresUnsafe().get(key);

                    if (exp != null) {
                        out.writeByte(OP_EXPIRE_MS);
                        out.writeLong(exp);
                    }

                    writeKeyAndValue(out, key, val);
                }
            }

            out.writeByte(OP_EOF);
            out.flush();
        }

        Files.move(tempFile.toPath(), rdbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    public boolean bgsave(DataStore dataStore) {
        if (!isSaving.compareAndSet(false, true)) {
            return false;
        }

        new Thread(() -> {
            try {
                save(dataStore);
            } catch (Exception e) {
                System.err.println("BGSAVE failed: " + e.getMessage());
            } finally {
                isSaving.set(false);
            }
        }, "redis-rdb-bgsave").start();

        return true;
    }

    public boolean loadRdb(DataStore dataStore) {
        if (!rdbFile.exists() || rdbFile.length() == 0) {
            return false;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(rdbFile)))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                System.err.println("Warning: Invalid RDB magic header");
                return false;
            }

            int currentDb = 0;
            Long currentExpire = null;

            while (true) {
                int op = in.read();
                if (op == -1 || op == (OP_EOF & 0xFF)) {
                    break;
                }

                if (op == (OP_SELECT_DB & 0xFF)) {
                    currentDb = in.readInt();
                    continue;
                }

                if (op == (OP_EXPIRE_MS & 0xFF)) {
                    currentExpire = in.readLong();
                    continue;
                }

                byte valType = (byte) op;
                String key = readString(in);
                RedisValue value = readValue(in, valType);

                long now = System.currentTimeMillis();
                if (currentExpire == null || now < currentExpire) {
                    RedisDatabase db = dataStore.getDatabase(currentDb);
                    db.put(key, value, currentExpire);
                }

                currentExpire = null;
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error loading RDB file: " + e.getMessage());
            return false;
        }
    }

    private void writeKeyAndValue(DataOutputStream out, String key, RedisValue val) throws IOException {
        RedisType type = val.getType();
        switch (type) {
            case STRING:
                out.writeByte(TYPE_STRING);
                writeString(out, key);
                writeByteArray(out, val.getAsString());
                break;
            case LIST:
                out.writeByte(TYPE_LIST);
                writeString(out, key);
                LinkedList<byte[]> list = val.getAsList();
                out.writeInt(list.size());
                for (byte[] item : list) {
                    writeByteArray(out, item);
                }
                break;
            case HASH:
                out.writeByte(TYPE_HASH);
                writeString(out, key);
                Map<String, byte[]> hash = val.getAsHash();
                out.writeInt(hash.size());
                for (Map.Entry<String, byte[]> hEntry : hash.entrySet()) {
                    writeString(out, hEntry.getKey());
                    writeByteArray(out, hEntry.getValue());
                }
                break;
            case SET:
                out.writeByte(TYPE_SET);
                writeString(out, key);
                Set<String> set = val.getAsSet();
                out.writeInt(set.size());
                for (String m : set) {
                    writeString(out, m);
                }
                break;
            case ZSET:
                out.writeByte(TYPE_ZSET);
                writeString(out, key);
                ZSet zset = val.getAsZSet();
                Map<String, Double> zEntries = zset.getEntries();
                out.writeInt(zEntries.size());
                for (Map.Entry<String, Double> zEntry : zEntries.entrySet()) {
                    writeDouble(out, zEntry.getValue());
                    writeString(out, zEntry.getKey());
                }
                break;
            default:
                break;
        }
    }

    private RedisValue readValue(DataInputStream in, byte valType) throws IOException {
        switch (valType) {
            case TYPE_STRING:
                return RedisValue.string(readByteArray(in));
            case TYPE_LIST:
                int listLen = in.readInt();
                LinkedList<byte[]> list = new LinkedList<>();
                for (int i = 0; i < listLen; i++) {
                    list.add(readByteArray(in));
                }
                return RedisValue.list(list);
            case TYPE_HASH:
                int hashLen = in.readInt();
                Map<String, byte[]> hash = new LinkedHashMap<>();
                for (int i = 0; i < hashLen; i++) {
                    String hKey = readString(in);
                    byte[] hVal = readByteArray(in);
                    hash.put(hKey, hVal);
                }
                return RedisValue.hash(hash);
            case TYPE_SET:
                int setLen = in.readInt();
                Set<String> set = new LinkedHashSet<>();
                for (int i = 0; i < setLen; i++) {
                    set.add(readString(in));
                }
                return RedisValue.set(set);
            case TYPE_ZSET:
                int zLen = in.readInt();
                ZSet zset = new ZSet();
                for (int i = 0; i < zLen; i++) {
                    double score = in.readDouble();
                    String member = readString(in);
                    zset.add(score, member);
                }
                return RedisValue.zset(zset);
            default:
                throw new IOException("Unknown RDB value type: " + valType);
        }
    }

    private void writeString(DataOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeByteArray(DataOutputStream out, byte[] bytes) throws IOException {
        if (bytes == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }

    private byte[] readByteArray(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len == -1) return null;
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return bytes;
    }

    private void writeDouble(DataOutputStream out, double d) throws IOException {
        out.writeDouble(d);
    }
}
