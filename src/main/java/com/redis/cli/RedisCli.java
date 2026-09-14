package com.redis.cli;

import com.redis.resp.RespParser;
import com.redis.resp.RespToken;
import com.redis.resp.RespWriter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Built-in interactive Redis CLI client with colorized prompt, multi-type output formatting,
 * and pipelining/pubsub listener support.
 */
public class RedisCli {
    private final String host;
    private final int port;
    private int currentDb = 0;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public RedisCli(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static void run(String[] args) {
        String host = "127.0.0.1";
        int port = 6379;
        List<String> commandArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (("-h".equals(a) || "--host".equals(a)) && i + 1 < args.length) {
                host = args[++i];
            } else if (("-p".equals(a) || "--port".equals(a)) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else {
                commandArgs.add(a);
            }
        }

        RedisCli cli = new RedisCli(host, port);
        try {
            cli.connect();
        } catch (Exception e) {
            System.err.println("Could not connect to Redis at " + host + ":" + port + ": " + e.getMessage());
            return;
        }

        if (!commandArgs.isEmpty()) {
            // One-shot command execution
            cli.executeAndPrint(commandArgs);
            cli.disconnect();
        } else {
            // Interactive REPL loop
            cli.startRepl();
        }
    }

    public void connect() throws Exception {
        socket = new Socket(host, port);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}
    }

    public void startRepl() {
        System.out.println("==================================================");
        System.out.println("Connected to Redis at " + host + ":" + port);
        System.out.println("Type 'help' for examples or 'quit' to exit.");
        System.out.println("==================================================");

        Scanner scanner = new Scanner(System.in);
        while (socket.isConnected() && !socket.isClosed()) {
            String prompt = (currentDb == 0)
                    ? host + ":" + port + "> "
                    : host + ":" + port + "[" + currentDb + "]> ";

            System.out.print(prompt);
            if (!scanner.hasNextLine()) break;

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                try {
                    executeCommand(List.of("QUIT"));
                } catch (Exception ignored) {}
                break;
            }

            if ("clear".equalsIgnoreCase(line) || "cls".equalsIgnoreCase(line)) {
                System.out.print("\033[H\033[2J");
                System.out.flush();
                continue;
            }

            List<String> tokens = parseCommandLine(line);
            if (tokens.isEmpty()) continue;

            // Track SELECT db index for prompt display
            if ("SELECT".equalsIgnoreCase(tokens.get(0)) && tokens.size() > 1) {
                try {
                    currentDb = Integer.parseInt(tokens.get(1));
                } catch (Exception ignored) {}
            }

            boolean isSubscribe = "SUBSCRIBE".equalsIgnoreCase(tokens.get(0)) || "PSUBSCRIBE".equalsIgnoreCase(tokens.get(0));

            try {
                if (isSubscribe) {
                    executeAndListenPubSub(tokens);
                } else {
                    executeAndPrint(tokens);
                }
            } catch (Exception e) {
                System.out.println("(error) " + e.getMessage());
                break;
            }
        }
        disconnect();
    }

    public void executeAndPrint(List<String> tokens) {
        try {
            RespToken response = executeCommand(tokens);
            printResponse(response, "");
        } catch (Exception e) {
            System.out.println("(error) " + e.getMessage());
        }
    }

    private RespToken executeCommand(List<String> tokens) throws Exception {
        List<RespToken> reqTokens = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            reqTokens.add(RespToken.bulkString(t));
        }
        byte[] payload = RespWriter.encode(RespToken.array(reqTokens));
        out.write(payload);
        out.flush();

        return readResponse();
    }

    private void executeAndListenPubSub(List<String> tokens) throws Exception {
        List<RespToken> reqTokens = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            reqTokens.add(RespToken.bulkString(t));
        }
        byte[] payload = RespWriter.encode(RespToken.array(reqTokens));
        out.write(payload);
        out.flush();

        System.out.println("Reading messages... (Press Ctrl+C to quit)");
        while (socket.isConnected() && !socket.isClosed()) {
            RespToken msg = readResponse();
            if (msg == null) break;
            printResponse(msg, "");
        }
    }

    private RespToken readResponse() throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
        byte[] temp = new byte[4096];

        while (true) {
            int read = in.read(temp);
            if (read == -1) {
                throw new Exception("Server closed connection");
            }
            buf.put(temp, 0, read);
            buf.flip();

            RespToken token = RespParser.parse(buf);
            if (token != null) {
                return token;
            }
            buf.compact();
        }
    }

    private void printResponse(RespToken token, String indent) {
        if (token == null) {
            System.out.println(indent + "(nil)");
            return;
        }

        switch (token.getType()) {
            case SIMPLE_STRING:
                System.out.println(indent + token.getStringValue());
                break;
            case ERROR:
                System.out.println(indent + "(error) " + token.getStringValue());
                break;
            case INTEGER:
                System.out.println(indent + "(integer) " + token.getIntValue());
                break;
            case BULK_STRING:
                System.out.println(indent + "\"" + token.getStringValue() + "\"");
                break;
            case NULL_BULK_STRING:
            case NULL_ARRAY:
                System.out.println(indent + "(nil)");
                break;
            case ARRAY:
                List<RespToken> list = token.getArrayValue();
                if (list == null || list.isEmpty()) {
                    System.out.println(indent + "(empty array)");
                } else {
                    for (int i = 0; i < list.size(); i++) {
                        System.out.print(indent + (i + 1) + ") ");
                        printResponse(list.get(i), indent + "   ");
                    }
                }
                break;
        }
    }

    private static List<String> parseCommandLine(String line) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                } else if (c == '\\' && i + 1 < line.length()) {
                    sb.append(line.charAt(++i));
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quoteChar = c;
                } else if (Character.isWhitespace(c)) {
                    if (sb.length() > 0) {
                        list.add(sb.toString());
                        sb.setLength(0);
                    }
                } else {
                    sb.append(c);
                }
            }
        }
        if (sb.length() > 0) {
            list.add(sb.toString());
        }
        return list;
    }
}
