package com.redis.resp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance streaming parser for Redis RESP protocol and inline commands.
 */
public class RespParser {

    /**
     * Attempts to parse one complete RESP command/token from the byte buffer.
     * If the buffer does not contain a full frame, the buffer position is reset
     * to the start of the attempt and null is returned.
     *
     * @param buffer The input ByteBuffer
     * @return RespToken if a complete token was parsed, or null if more data is needed
     */
    public static RespToken parse(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return null;
        }

        buffer.mark();
        try {
            byte prefix = buffer.get();
            switch (prefix) {
                case '+': // Simple String
                    return parseSimpleString(buffer);
                case '-': // Error
                    return parseError(buffer);
                case ':': // Integer
                    return parseInteger(buffer);
                case '$': // Bulk String
                    return parseBulkString(buffer);
                case '*': // Array
                    return parseArray(buffer);
                default:
                    // Inline command (telnet or raw text command, e.g. "PING\r\n")
                    buffer.reset();
                    buffer.mark();
                    return parseInlineCommand(buffer);
            }
        } catch (IncompleteFrameException e) {
            buffer.reset();
            return null;
        }
    }

    private static RespToken parseSimpleString(ByteBuffer buffer) throws IncompleteFrameException {
        byte[] line = readLine(buffer);
        return RespToken.simpleString(line);
    }

    private static RespToken parseError(ByteBuffer buffer) throws IncompleteFrameException {
        byte[] line = readLine(buffer);
        return RespToken.error(new String(line, StandardCharsets.UTF_8));
    }

    private static RespToken parseInteger(ByteBuffer buffer) throws IncompleteFrameException {
        byte[] line = readLine(buffer);
        String s = new String(line, StandardCharsets.US_ASCII).trim();
        try {
            long val = Long.parseLong(s);
            return RespToken.integer(val);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Protocol error: invalid integer " + s);
        }
    }

    private static RespToken parseBulkString(ByteBuffer buffer) throws IncompleteFrameException {
        byte[] line = readLine(buffer);
        String lenStr = new String(line, StandardCharsets.US_ASCII).trim();
        int length;
        try {
            length = Integer.parseInt(lenStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Protocol error: invalid bulk length " + lenStr);
        }

        if (length == -1) {
            return RespToken.nullBulkString();
        }

        if (length < 0) {
            throw new RuntimeException("Protocol error: negative bulk length " + length);
        }

        if (buffer.remaining() < length + 2) {
            throw new IncompleteFrameException();
        }

        byte[] data = new byte[length];
        buffer.get(data);

        byte cr = buffer.get();
        byte lf = buffer.get();
        if (cr != '\r' || lf != '\n') {
            throw new RuntimeException("Protocol error: bulk string missing CRLF");
        }

        return RespToken.bulkString(data);
    }

    private static RespToken parseArray(ByteBuffer buffer) throws IncompleteFrameException {
        byte[] line = readLine(buffer);
        String countStr = new String(line, StandardCharsets.US_ASCII).trim();
        int count;
        try {
            count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Protocol error: invalid array count " + countStr);
        }

        if (count == -1) {
            return RespToken.nullArray();
        }

        if (count < 0) {
            throw new RuntimeException("Protocol error: negative array length " + count);
        }

        List<RespToken> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (!buffer.hasRemaining()) {
                throw new IncompleteFrameException();
            }
            byte prefix = buffer.get();
            RespToken item;
            switch (prefix) {
                case '+':
                    item = parseSimpleString(buffer);
                    break;
                case '-':
                    item = parseError(buffer);
                    break;
                case ':':
                    item = parseInteger(buffer);
                    break;
                case '$':
                    item = parseBulkString(buffer);
                    break;
                case '*':
                    item = parseArray(buffer);
                    break;
                default:
                    throw new RuntimeException("Protocol error: unexpected byte in array " + (char) prefix);
            }
            items.add(item);
        }

        return RespToken.array(items);
    }

    private static RespToken parseInlineCommand(ByteBuffer buffer) throws IncompleteFrameException {
        byte[] line = readLine(buffer);
        String text = new String(line, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return null;
        }

        List<String> parts = splitCommandLine(text);
        List<RespToken> tokens = new ArrayList<>(parts.size());
        for (String p : parts) {
            tokens.add(RespToken.bulkString(p));
        }
        return RespToken.array(tokens);
    }

    private static List<String> splitCommandLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else if (c == '\\' && i + 1 < line.length()) {
                    sb.append(line.charAt(++i));
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuotes = true;
                    quoteChar = c;
                } else if (Character.isWhitespace(c)) {
                    if (sb.length() > 0) {
                        tokens.add(sb.toString());
                        sb.setLength(0);
                    }
                } else {
                    sb.append(c);
                }
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    private static byte[] readLine(ByteBuffer buffer) throws IncompleteFrameException {
        int start = buffer.position();
        boolean foundCr = false;

        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\r') {
                foundCr = true;
            } else if (b == '\n' && foundCr) {
                int end = buffer.position() - 2;
                int len = end - start;
                byte[] line = new byte[len];
                int currentPos = buffer.position();
                buffer.position(start);
                buffer.get(line);
                buffer.position(currentPos);
                return line;
            } else {
                foundCr = false;
            }
        }

        throw new IncompleteFrameException();
    }

    private static class IncompleteFrameException extends Exception {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
