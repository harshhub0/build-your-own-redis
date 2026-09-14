package com.redis.commands;

import com.redis.net.ClientConnection;
import com.redis.net.PubSubManager;
import com.redis.resp.RespToken;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handlers for Redis Pub/Sub messaging commands.
 */
public class PubSubCommands {

    public static RespToken subscribe(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'subscribe' command");
        ClientConnection client = ctx.getClient();
        if (client == null) return RespToken.error("ERR SUBSCRIBE requires client connection");

        List<String> channels = new ArrayList<>(args.size());
        for (byte[] arg : args) {
            channels.add(new String(arg, StandardCharsets.UTF_8));
        }
        ctx.getPubSubManager().subscribe(client, channels);
        return null; // Output is sent directly through client queue inside subscribe()
    }

    public static RespToken unsubscribe(CommandContext ctx, List<byte[]> args) {
        ClientConnection client = ctx.getClient();
        if (client == null) return RespToken.error("ERR UNSUBSCRIBE requires client connection");

        List<String> channels = new ArrayList<>(args.size());
        for (byte[] arg : args) {
            channels.add(new String(arg, StandardCharsets.UTF_8));
        }
        ctx.getPubSubManager().unsubscribe(client, channels);
        return null;
    }

    public static RespToken psubscribe(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'psubscribe' command");
        ClientConnection client = ctx.getClient();
        if (client == null) return RespToken.error("ERR PSUBSCRIBE requires client connection");

        List<String> patterns = new ArrayList<>(args.size());
        for (byte[] arg : args) {
            patterns.add(new String(arg, StandardCharsets.UTF_8));
        }
        ctx.getPubSubManager().psubscribe(client, patterns);
        return null;
    }

    public static RespToken punsubscribe(CommandContext ctx, List<byte[]> args) {
        ClientConnection client = ctx.getClient();
        if (client == null) return RespToken.error("ERR PUNSUBSCRIBE requires client connection");

        List<String> patterns = new ArrayList<>(args.size());
        for (byte[] arg : args) {
            patterns.add(new String(arg, StandardCharsets.UTF_8));
        }
        ctx.getPubSubManager().punsubscribe(client, patterns);
        return null;
    }

    public static RespToken publish(CommandContext ctx, List<byte[]> args) {
        if (args.size() != 2) return RespToken.error("ERR wrong number of arguments for 'publish' command");
        String channel = new String(args.get(0), StandardCharsets.UTF_8);
        byte[] message = args.get(1);

        int receivers = ctx.getPubSubManager().publish(channel, message);
        return RespToken.integer(receivers);
    }

    public static RespToken pubsub(CommandContext ctx, List<byte[]> args) {
        if (args.isEmpty()) return RespToken.error("ERR wrong number of arguments for 'pubsub' command");
        String subcmd = new String(args.get(0), StandardCharsets.UTF_8).toUpperCase();

        PubSubManager psm = ctx.getPubSubManager();

        if ("CHANNELS".equals(subcmd)) {
            String pattern = (args.size() > 1) ? new String(args.get(1), StandardCharsets.UTF_8) : null;
            List<String> channels = psm.getActiveChannels(pattern);
            List<RespToken> tokens = new ArrayList<>(channels.size());
            for (String ch : channels) {
                tokens.add(RespToken.bulkString(ch));
            }
            return RespToken.array(tokens);
        } else if ("NUMSUB".equals(subcmd)) {
            List<RespToken> tokens = new ArrayList<>();
            for (int i = 1; i < args.size(); i++) {
                String ch = new String(args.get(i), StandardCharsets.UTF_8);
                tokens.add(RespToken.bulkString(ch));
                tokens.add(RespToken.integer(psm.getNumSub(ch)));
            }
            return RespToken.array(tokens);
        } else if ("NUMPAT".equals(subcmd)) {
            return RespToken.integer(psm.getNumPat());
        }

        return RespToken.error("ERR unknown PUBSUB subcommand or wrong number of arguments");
    }
}
