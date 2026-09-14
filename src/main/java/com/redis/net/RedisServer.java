package com.redis.net;

import com.redis.commands.CommandContext;
import com.redis.commands.CommandRegistry;
import com.redis.config.ServerConfig;
import com.redis.persistence.AofEngine;
import com.redis.persistence.RdbEngine;
import com.redis.resp.RespParser;
import com.redis.resp.RespToken;
import com.redis.resp.RespWriter;
import com.redis.storage.DataStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Non-blocking Java NIO Reactor Event Loop Server for Redis.
 */
public class RedisServer {
    private final ServerConfig config;
    private final DataStore dataStore;
    private final PubSubManager pubSubManager;
    private final CommandRegistry commandRegistry;
    private final AofEngine aofEngine;
    private final RdbEngine rdbEngine;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<ClientConnection> clients = ConcurrentHashMap.newKeySet();
    private Thread serverThread;

    public RedisServer(ServerConfig config) {
        this.config = config;
        this.dataStore = new DataStore(config.getDbCount());
        this.pubSubManager = new PubSubManager();
        this.commandRegistry = new CommandRegistry();
        this.aofEngine = new AofEngine(config);
        this.rdbEngine = new RdbEngine(config);
    }

    public void start() throws IOException {
        // 1. Recover state from disk if persistence file exists
        if (config.isAppendOnly() && aofEngine.loadAof(dataStore, commandRegistry)) {
            System.out.println("[Persistence] Recovered dataset from AOF file: " + config.getAofFile().getName());
        } else if (rdbEngine.loadRdb(dataStore)) {
            System.out.println("[Persistence] Recovered dataset from RDB file: " + config.getRdbFile().getName());
        }

        // 2. Setup NIO Server Socket & Selector
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(config.getHost(), config.getPort()));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        running.set(true);

        System.out.println("==========================================================");
        System.out.println("   *   Custom Redis Server in Java 17 (RESP2/RESP3)       ");
        System.out.println("   *   Port: " + config.getPort() + " | Host: " + config.getHost());
        System.out.println("   *   AOF: " + (config.isAppendOnly() ? "Enabled (" + config.getAofFsync() + ")" : "Disabled"));
        System.out.println("   *   Ready to accept connections                        ");
        System.out.println("==========================================================");

        serverThread = new Thread(this::eventLoop, "redis-reactor-loop");
        serverThread.start();
    }

    private void eventLoop() {
        while (running.get() && selector.isOpen()) {
            try {
                int readyChannels = selector.select(100);

                // Check pending writes on all active clients
                flushPendingWrites();

                if (readyChannels == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            } catch (ClosedSelectorException e) {
                break;
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("Reactor loop error: " + e.getMessage());
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) {
        try {
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel clientChannel = server.accept();
            if (clientChannel != null) {
                clientChannel.configureBlocking(false);
                ClientConnection client = new ClientConnection(clientChannel);
                clients.add(client);
                clientChannel.register(selector, SelectionKey.OP_READ, client);
            }
        } catch (IOException e) {
            System.err.println("Failed to accept client connection: " + e.getMessage());
        }
    }

    private void handleRead(SelectionKey key) {
        ClientConnection client = (ClientConnection) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = client.getReadBuffer();

        try {
            int bytesRead = channel.read(buffer);
            if (bytesRead == -1) {
                // Client disconnected
                disconnectClient(client, key);
                return;
            }

            if (bytesRead > 0) {
                client.updateActivity();
                processIncomingData(client, key);
            }
        } catch (IOException e) {
            disconnectClient(client, key);
        }
    }

    private void processIncomingData(ClientConnection client, SelectionKey key) {
        ByteBuffer buffer = client.getReadBuffer();
        buffer.flip();

        while (true) {
            RespToken token = RespParser.parse(buffer);
            if (token == null) {
                break; // Incomplete command, wait for more TCP packets
            }

            RespToken response = executeCommand(client, token);
            if (response != null) {
                client.queueWrite(response);
            }
        }

        buffer.compact();

        // If client has queued writes, try writing immediately or register OP_WRITE
        flushClientWrites(client, key);
    }

    private RespToken executeCommand(ClientConnection client, RespToken token) {
        if (token.getType() == RespToken.Type.ARRAY) {
            List<RespToken> parts = token.getArrayValue();
            if (parts == null || parts.isEmpty()) {
                return null;
            }
            String cmdName = parts.get(0).getStringValue();
            if (cmdName == null) {
                return RespToken.error("ERR protocol error");
            }
            List<byte[]> args = new ArrayList<>(parts.size() - 1);
            for (int i = 1; i < parts.size(); i++) {
                args.add(parts.get(i).getBytesValue());
            }

            CommandContext ctx = new CommandContext(
                    config, dataStore, pubSubManager, client, aofEngine, rdbEngine, this::stop
            );
            return commandRegistry.dispatch(ctx, cmdName, args);
        } else if (token.getType() == RespToken.Type.BULK_STRING || token.getType() == RespToken.Type.SIMPLE_STRING) {
            // Single word command
            String cmdName = token.getStringValue();
            CommandContext ctx = new CommandContext(
                    config, dataStore, pubSubManager, client, aofEngine, rdbEngine, this::stop
            );
            return commandRegistry.dispatch(ctx, cmdName, List.of());
        }

        return RespToken.error("ERR unknown command format");
    }

    private void handleWrite(SelectionKey key) {
        ClientConnection client = (ClientConnection) key.attachment();
        flushClientWrites(client, key);
    }

    private void flushClientWrites(ClientConnection client, SelectionKey key) {
        SocketChannel channel = client.getChannel();
        Queue<ByteBuffer> queue = client.getWriteQueue();

        try {
            while (!queue.isEmpty()) {
                ByteBuffer buf = queue.peek();
                channel.write(buf);
                if (buf.hasRemaining()) {
                    // Channel buffer full, register OP_WRITE to resume later
                    if (key.isValid()) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    }
                    return;
                } else {
                    queue.poll();
                }
            }

            // All pending writes flushed, clear OP_WRITE interest
            if (key.isValid()) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            disconnectClient(client, key);
        }
    }

    private void flushPendingWrites() {
        for (ClientConnection client : clients) {
            if (client.hasPendingWrites()) {
                SelectionKey key = client.getChannel().keyFor(selector);
                if (key != null && key.isValid()) {
                    flushClientWrites(client, key);
                }
            }
        }
    }

    private void disconnectClient(ClientConnection client, SelectionKey key) {
        clients.remove(client);
        pubSubManager.onClientDisconnect(client);
        if (key != null) {
            key.cancel();
        }
        client.close();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        System.out.println("Shutting down Redis Server...");
        try {
            if (selector != null) {
                selector.wakeup();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
            for (ClientConnection client : clients) {
                client.close();
            }
            clients.clear();
            if (selector != null) {
                selector.close();
            }
        } catch (IOException ignored) {}

        aofEngine.close();
        dataStore.close();

        if (serverThread != null) {
            try {
                serverThread.join(2000);
            } catch (InterruptedException ignored) {}
        }
        System.out.println("Redis Server stopped cleanly.");
    }

    public ServerConfig getConfig() {
        return config;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public PubSubManager getPubSubManager() {
        return pubSubManager;
    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public boolean isRunning() {
        return running.get();
    }
}
