package com.redis.resp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serializes RespToken and raw Java objects into RESP wire format bytes.
 */
public class RespWriter {
    public static final byte[] CRLF = new byte[]{'\r', '\n'};
    public static final byte SIMPLE_STRING_PREFIX = '+';
    public static final byte ERROR_PREFIX = '-';
    public static final byte INTEGER_PREFIX = ':';
    public static final byte BULK_STRING_PREFIX = '$';
    public static final byte ARRAY_PREFIX = '*';

    public static byte[] encode(RespToken token) {
        if (token == null) {
            return encodeNullBulkString();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeToken(token, out);
        } catch (IOException e) {
            throw new RuntimeException("Serialization error", e);
        }
        return out.toByteArray();
    }

    private static void writeToken(RespToken token, ByteArrayOutputStream out) throws IOException {
        switch (token.getType()) {
            case SIMPLE_STRING:
                out.write(SIMPLE_STRING_PREFIX);
                if (token.getBytesValue() != null) {
                    out.write(token.getBytesValue());
                }
                out.write(CRLF);
                break;
            case ERROR:
                out.write(ERROR_PREFIX);
                if (token.getBytesValue() != null) {
                    out.write(token.getBytesValue());
                }
                out.write(CRLF);
                break;
            case INTEGER:
                out.write(INTEGER_PREFIX);
                out.write(Long.toString(token.getIntValue()).getBytes(StandardCharsets.US_ASCII));
                out.write(CRLF);
                break;
            case BULK_STRING:
                byte[] val = token.getBytesValue();
                if (val == null) {
                    out.write(BULK_STRING_PREFIX);
                    out.write("-1".getBytes(StandardCharsets.US_ASCII));
                    out.write(CRLF);
                } else {
                    out.write(BULK_STRING_PREFIX);
                    out.write(Integer.toString(val.length).getBytes(StandardCharsets.US_ASCII));
                    out.write(CRLF);
                    out.write(val);
                    out.write(CRLF);
                }
                break;
            case NULL_BULK_STRING:
                out.write(BULK_STRING_PREFIX);
                out.write("-1".getBytes(StandardCharsets.US_ASCII));
                out.write(CRLF);
                break;
            case ARRAY:
                List<RespToken> items = token.getArrayValue();
                if (items == null) {
                    out.write(ARRAY_PREFIX);
                    out.write("-1".getBytes(StandardCharsets.US_ASCII));
                    out.write(CRLF);
                } else {
                    out.write(ARRAY_PREFIX);
                    out.write(Integer.toString(items.size()).getBytes(StandardCharsets.US_ASCII));
                    out.write(CRLF);
                    for (RespToken child : items) {
                        writeToken(child, out);
                    }
                }
                break;
            case NULL_ARRAY:
                out.write(ARRAY_PREFIX);
                out.write("-1".getBytes(StandardCharsets.US_ASCII));
                out.write(CRLF);
                break;
        }
    }

    public static byte[] encodeSimpleString(String s) {
        return encode(RespToken.simpleString(s));
    }

    public static byte[] encodeError(String s) {
        return encode(RespToken.error(s));
    }

    public static byte[] encodeInteger(long n) {
        return encode(RespToken.integer(n));
    }

    public static byte[] encodeBulkString(byte[] bytes) {
        return encode(RespToken.bulkString(bytes));
    }

    public static byte[] encodeBulkString(String s) {
        return encode(RespToken.bulkString(s));
    }

    public static byte[] encodeNullBulkString() {
        return ("$-1\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodeNullArray() {
        return ("*-1\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodeArray(List<RespToken> tokens) {
        return encode(RespToken.array(tokens));
    }
}
