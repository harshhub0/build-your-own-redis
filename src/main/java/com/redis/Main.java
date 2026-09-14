package com.redis;

import com.redis.cli.RedisCli;
import com.redis.config.ServerConfig;
import com.redis.net.RedisServer;

import java.util.Arrays;

/**
 * Main application entrypoint for starting the Redis Server or built-in CLI.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && "cli".equalsIgnoreCase(args[0])) {
            String[] cliArgs = Arrays.copyOfRange(args, 1, args.length);
            RedisCli.run(cliArgs);
            return;
        }

        if (args.length > 0 && ("--help".equalsIgnoreCase(args[0]) || "-help".equalsIgnoreCase(args[0]))) {
            printUsage();
            return;
        }

        ServerConfig config = ServerConfig.parse(args);
        RedisServer server = new RedisServer(config);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "redis-shutdown-hook"));

        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Fatal error starting Redis server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Custom Redis in Java 17");
        System.out.println("Usage:");
        System.out.println("  java -jar redis-java.jar [options]           Start the Redis Server");
        System.out.println("  java -jar redis-java.jar cli [options]       Start interactive CLI client");
        System.out.println("\nServer Options:");
        System.out.println("  --port <port>          TCP port to listen on (default: 6379)");
        System.out.println("  --bind <host>          Host interface to bind (default: 0.0.0.0)");
        System.out.println("  --dir <path>           Working directory for data files (default: .)");
        System.out.println("  --appendonly <yes|no>  Enable AOF persistence (default: yes)");
        System.out.println("  --appendfsync <policy> Fsync policy: always, everysec, no (default: everysec)");
        System.out.println("  --requirepass <pass>   Require password authentication");
        System.out.println("  --databases <count>    Number of isolated databases (default: 16)");
        System.out.println("\nCLI Options:");
        System.out.println("  cli -h <host> -p <port> [command]   Connect to running Redis instance");
    }
}
