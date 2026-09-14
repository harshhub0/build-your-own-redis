package com.redis.net;

import com.redis.resp.RespToken;
import com.redis.storage.RedisDatabase;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages Pub/Sub channel subscriptions, pattern matches, and message routing.
 */
public class PubSubManager {
    private final Map<String, Set<ClientConnection>> channelSubs = new ConcurrentHashMap<>();
    private final Map<String, Set<ClientConnection>> patternSubs = new ConcurrentHashMap<>();

    public synchronized void subscribe(ClientConnection client, List<String> channels) {
        for (String channel : channels) {
            channelSubs.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(client);
            client.getSubscribedChannels().add(channel);
            List<RespToken> resp = List.of(
                    RespToken.bulkString("subscribe"),
                    RespToken.bulkString(channel),
                    RespToken.integer(client.totalSubscriptions())
            );
            client.queueWrite(RespToken.array(resp));
        }
    }

    public synchronized void unsubscribe(ClientConnection client, List<String> channels) {
        Collection<String> targetChannels = channels.isEmpty() ? new ArrayList<>(client.getSubscribedChannels()) : channels;
        for (String channel : targetChannels) {
            Set<ClientConnection> set = channelSubs.get(channel);
            if (set != null) {
                set.remove(client);
                if (set.isEmpty()) {
                    channelSubs.remove(channel);
                }
            }
            client.getSubscribedChannels().remove(channel);
            List<RespToken> resp = List.of(
                    RespToken.bulkString("unsubscribe"),
                    RespToken.bulkString(channel),
                    RespToken.integer(client.totalSubscriptions())
            );
            client.queueWrite(RespToken.array(resp));
        }
    }

    public synchronized void psubscribe(ClientConnection client, List<String> patterns) {
        for (String pattern : patterns) {
            patternSubs.computeIfAbsent(pattern, k -> ConcurrentHashMap.newKeySet()).add(client);
            client.getSubscribedPatterns().add(pattern);
            List<RespToken> resp = List.of(
                    RespToken.bulkString("psubscribe"),
                    RespToken.bulkString(pattern),
                    RespToken.integer(client.totalSubscriptions())
            );
            client.queueWrite(RespToken.array(resp));
        }
    }

    public synchronized void punsubscribe(ClientConnection client, List<String> patterns) {
        Collection<String> targetPatterns = patterns.isEmpty() ? new ArrayList<>(client.getSubscribedPatterns()) : patterns;
        for (String pattern : targetPatterns) {
            Set<ClientConnection> set = patternSubs.get(pattern);
            if (set != null) {
                set.remove(client);
                if (set.isEmpty()) {
                    patternSubs.remove(pattern);
                }
            }
            client.getSubscribedPatterns().remove(pattern);
            List<RespToken> resp = List.of(
                    RespToken.bulkString("punsubscribe"),
                    RespToken.bulkString(pattern),
                    RespToken.integer(client.totalSubscriptions())
            );
            client.queueWrite(RespToken.array(resp));
        }
    }

    public synchronized int publish(String channel, byte[] message) {
        int receivers = 0;

        // 1. Direct channel subscribers
        Set<ClientConnection> directSubscribers = channelSubs.get(channel);
        if (directSubscribers != null) {
            RespToken directMsg = RespToken.array(List.of(
                    RespToken.bulkString("message"),
                    RespToken.bulkString(channel),
                    RespToken.bulkString(message)
            ));
            for (ClientConnection client : directSubscribers) {
                client.queueWrite(directMsg);
                receivers++;
            }
        }

        // 2. Pattern subscribers
        for (Map.Entry<String, Set<ClientConnection>> entry : patternSubs.entrySet()) {
            String pattern = entry.getKey();
            Pattern regex = RedisDatabase.globToRegex(pattern);
            if (regex.matcher(channel).matches()) {
                RespToken pMsg = RespToken.array(List.of(
                        RespToken.bulkString("pmessage"),
                        RespToken.bulkString(pattern),
                        RespToken.bulkString(channel),
                        RespToken.bulkString(message)
                ));
                for (ClientConnection client : entry.getValue()) {
                    client.queueWrite(pMsg);
                    receivers++;
                }
            }
        }

        return receivers;
    }

    public synchronized void onClientDisconnect(ClientConnection client) {
        for (String channel : client.getSubscribedChannels()) {
            Set<ClientConnection> set = channelSubs.get(channel);
            if (set != null) {
                set.remove(client);
                if (set.isEmpty()) {
                    channelSubs.remove(channel);
                }
            }
        }
        for (String pattern : client.getSubscribedPatterns()) {
            Set<ClientConnection> set = patternSubs.get(pattern);
            if (set != null) {
                set.remove(client);
                if (set.isEmpty()) {
                    patternSubs.remove(pattern);
                }
            }
        }
    }

    public List<String> getActiveChannels(String globPattern) {
        Pattern regex = (globPattern == null || globPattern.isEmpty()) ? null : RedisDatabase.globToRegex(globPattern);
        List<String> list = new ArrayList<>();
        for (String ch : channelSubs.keySet()) {
            if (regex == null || regex.matcher(ch).matches()) {
                list.add(ch);
            }
        }
        return list;
    }

    public int getNumSub(String channel) {
        Set<ClientConnection> set = channelSubs.get(channel);
        return set != null ? set.size() : 0;
    }

    public int getNumPat() {
        return patternSubs.size();
    }
}
