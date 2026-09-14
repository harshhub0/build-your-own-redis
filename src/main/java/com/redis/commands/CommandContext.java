package com.redis.commands;

import com.redis.config.ServerConfig;
import com.redis.net.ClientConnection;
import com.redis.net.PubSubManager;
import com.redis.persistence.AofEngine;
import com.redis.persistence.RdbEngine;
import com.redis.storage.DataStore;
import com.redis.storage.RedisDatabase;

/**
 * Contextual state provided to each command execution.
 */
public class CommandContext {
    private final ServerConfig config;
    private final DataStore dataStore;
    private final PubSubManager pubSubManager;
    private final ClientConnection client;
    private final AofEngine aofEngine;
    private final RdbEngine rdbEngine;
    private final Runnable shutdownHook;

    public CommandContext(ServerConfig config, DataStore dataStore, PubSubManager pubSubManager,
                          ClientConnection client, AofEngine aofEngine, RdbEngine rdbEngine,
                          Runnable shutdownHook) {
        this.config = config;
        this.dataStore = dataStore;
        this.pubSubManager = pubSubManager;
        this.client = client;
        this.aofEngine = aofEngine;
        this.rdbEngine = rdbEngine;
        this.shutdownHook = shutdownHook;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public RedisDatabase getDatabase() {
        return dataStore.getDatabase(client != null ? client.getCurrentDbIndex() : 0);
    }

    public RedisDatabase getDatabase(int index) {
        return dataStore.getDatabase(index);
    }

    public PubSubManager getPubSubManager() {
        return pubSubManager;
    }

    public ClientConnection getClient() {
        return client;
    }

    public AofEngine getAofEngine() {
        return aofEngine;
    }

    public RdbEngine getRdbEngine() {
        return rdbEngine;
    }

    public Runnable getShutdownHook() {
        return shutdownHook;
    }
}
