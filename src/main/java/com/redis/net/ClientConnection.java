package com.redis.net;

import com.redis.resp.RespParser;
import com.redis.resp.RespToken;
import com.redis.resp.RespWriter;
import com.redis.storage.DataStore;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the state, buffers, transactions, and subscriptions of a connected Redis client.
 */
public class ClientConnection {
    private static final AtomicLong ID_GEN = new AtomicLong(1);

    private final long id;
    private final SocketChannel channel;
    private final SocketAddress remoteAddress;
    private final long connectedTimeMillis;
    private volatile long lastActivityMillis;

    private String name = "";
    private int currentDbIndex = 0;
    private boolean authenticated = false;

    // Buffer management for streaming reads
    private ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);

    // Buffer queue for non-blocking writes
    private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();

    // Transaction state
    private boolean inTransaction = false;
    private final List<QueuedCommand> transactionQueue = new ArrayList<>();
    // dbIndex -> (key -> version)
    private final Map<Integer, Map<String, Long>> watchedKeys = new HashMap<>();
    private boolean watchedKeysDirty = false;

    // Pub/Sub state
    private final Set<String> subscribedChannels = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedPatterns = ConcurrentHashMap.newKeySet();

    public static class QueuedCommand {
        public final String commandName;
        public final List<byte[]> args;

        public QueuedCommand(String commandName, List<byte[]> args) {
            this.commandName = commandName;
            this.args = args;
        }
    }

    public ClientConnection(SocketChannel channel) {
        this.id = ID_GEN.getAndIncrement();
        this.channel = channel;
        SocketAddress addr = null;
        if (channel != null) {
            try {
                addr = channel.getRemoteAddress();
            } catch (IOException ignored) {}
        }
        this.remoteAddress = addr;
        this.connectedTimeMillis = System.currentTimeMillis();
        this.lastActivityMillis = this.connectedTimeMillis;
    }

    public long getId() {
        return id;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public long getConnectedTimeMillis() {
        return connectedTimeMillis;
    }

    public long getLastActivityMillis() {
        return lastActivityMillis;
    }

    public void updateActivity() {
        this.lastActivityMillis = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCurrentDbIndex() {
        return currentDbIndex;
    }

    public void setCurrentDbIndex(int currentDbIndex) {
        this.currentDbIndex = currentDbIndex;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    public void ensureReadBufferCapacity(int additional) {
        if (readBuffer.remaining() < additional) {
            int newCap = Math.max(readBuffer.capacity() * 2, readBuffer.position() + additional);
            ByteBuffer newBuf = ByteBuffer.allocate(newCap);
            readBuffer.flip();
            newBuf.put(readBuffer);
            readBuffer = newBuf;
        }
    }

    public void queueWrite(byte[] bytes) {
        writeQueue.offer(ByteBuffer.wrap(bytes));
    }

    public void queueWrite(RespToken token) {
        queueWrite(RespWriter.encode(token));
    }

    public Queue<ByteBuffer> getWriteQueue() {
        return writeQueue;
    }

    public boolean hasPendingWrites() {
        return !writeQueue.isEmpty();
    }

    // Transaction helpers
    public boolean isInTransaction() {
        return inTransaction;
    }

    public void startTransaction() {
        this.inTransaction = true;
        this.transactionQueue.clear();
        this.watchedKeysDirty = false;
    }

    public void queueCommand(String cmd, List<byte[]> args) {
        transactionQueue.add(new QueuedCommand(cmd, args));
    }

    public List<QueuedCommand> getTransactionQueue() {
        return transactionQueue;
    }

    public void resetTransaction() {
        this.inTransaction = false;
        this.transactionQueue.clear();
        this.watchedKeys.clear();
        this.watchedKeysDirty = false;
    }

    public void watchKey(int dbIndex, String key, long currentVersion) {
        watchedKeys.computeIfAbsent(dbIndex, k -> new HashMap<>()).put(key, currentVersion);
    }

    public void unwatchAll() {
        watchedKeys.clear();
        watchedKeysDirty = false;
    }

    public boolean checkWatchedKeys(DataStore dataStore) {
        if (watchedKeysDirty) {
            return false;
        }
        for (Map.Entry<Integer, Map<String, Long>> dbEntry : watchedKeys.entrySet()) {
            int dbIdx = dbEntry.getKey();
            var db = dataStore.getDatabase(dbIdx);
            for (Map.Entry<String, Long> keyEntry : dbEntry.getValue().entrySet()) {
                String key = keyEntry.getKey();
                long watchedVer = keyEntry.getValue();
                long currentVer = db.getKeyVersion(key);
                if (watchedVer != currentVer) {
                    return false;
                }
            }
        }
        return true;
    }

    // PubSub helpers
    public Set<String> getSubscribedChannels() {
        return subscribedChannels;
    }

    public Set<String> getSubscribedPatterns() {
        return subscribedPatterns;
    }

    public boolean isSubscribed() {
        return !subscribedChannels.isEmpty() || !subscribedPatterns.isEmpty();
    }

    public int totalSubscriptions() {
        return subscribedChannels.size() + subscribedPatterns.size();
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {}
    }
}
