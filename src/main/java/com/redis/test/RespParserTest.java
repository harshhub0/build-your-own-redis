package com.redis.test;

import com.redis.resp.RespParser;
import com.redis.resp.RespToken;
import com.redis.resp.RespWriter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RespParserTest {

    public static void run() {
        System.out.println("[Test] Running RESP Protocol & Parser tests...");
        testSimpleString();
        testError();
        testInteger();
        testBulkString();
        testArray();
        testInlineCommand();
        testPartialFrame();
        testPipelineStream();
        System.out.println("[Test] RESP Protocol & Parser tests passed successfully!\n");
    }

    private static void testSimpleString() {
        byte[] encoded = RespWriter.encode(RespToken.simpleString("PONG"));
        RespToken token = RespParser.parse(ByteBuffer.wrap(encoded));
        assert token != null && token.getType() == RespToken.Type.SIMPLE_STRING;
        assert "PONG".equals(token.getStringValue());
    }

    private static void testError() {
        byte[] encoded = RespWriter.encode(RespToken.error("ERR unknown command"));
        RespToken token = RespParser.parse(ByteBuffer.wrap(encoded));
        assert token != null && token.getType() == RespToken.Type.ERROR;
        assert "ERR unknown command".equals(token.getStringValue());
    }

    private static void testInteger() {
        byte[] encoded = RespWriter.encode(RespToken.integer(42));
        RespToken token = RespParser.parse(ByteBuffer.wrap(encoded));
        assert token != null && token.getType() == RespToken.Type.INTEGER;
        assert token.getIntValue() == 42;
    }

    private static void testBulkString() {
        byte[] encoded = RespWriter.encode(RespToken.bulkString("foobar"));
        RespToken token = RespParser.parse(ByteBuffer.wrap(encoded));
        assert token != null && token.getType() == RespToken.Type.BULK_STRING;
        assert "foobar".equals(token.getStringValue());

        // Null bulk string
        byte[] nullEncoded = RespWriter.encode(RespToken.nullBulkString());
        RespToken nullToken = RespParser.parse(ByteBuffer.wrap(nullEncoded));
        assert nullToken != null && nullToken.getType() == RespToken.Type.NULL_BULK_STRING;
    }

    private static void testArray() {
        List<RespToken> items = List.of(
                RespToken.bulkString("SET"),
                RespToken.bulkString("mykey"),
                RespToken.bulkString("myval")
        );
        byte[] encoded = RespWriter.encode(RespToken.array(items));
        RespToken token = RespParser.parse(ByteBuffer.wrap(encoded));
        assert token != null && token.getType() == RespToken.Type.ARRAY;
        assert token.getArrayValue().size() == 3;
        assert "SET".equals(token.getArrayValue().get(0).getStringValue());
        assert "mykey".equals(token.getArrayValue().get(1).getStringValue());
        assert "myval".equals(token.getArrayValue().get(2).getStringValue());
    }

    private static void testInlineCommand() {
        byte[] raw = "PING\r\n".getBytes(StandardCharsets.UTF_8);
        RespToken token = RespParser.parse(ByteBuffer.wrap(raw));
        assert token != null && token.getType() == RespToken.Type.ARRAY;
        assert token.getArrayValue().size() == 1;
        assert "PING".equals(token.getArrayValue().get(0).getStringValue());

        byte[] rawSet = "SET \"my key\" 'my value'\r\n".getBytes(StandardCharsets.UTF_8);
        RespToken setToken = RespParser.parse(ByteBuffer.wrap(rawSet));
        assert setToken != null && setToken.getType() == RespToken.Type.ARRAY;
        assert setToken.getArrayValue().size() == 3;
        assert "my key".equals(setToken.getArrayValue().get(1).getStringValue());
        assert "my value".equals(setToken.getArrayValue().get(2).getStringValue());
    }

    private static void testPartialFrame() {
        byte[] partial = "*2\r\n$3\r\nfoo\r\n$3\r\nba".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(partial);
        RespToken token = RespParser.parse(buf);
        assert token == null : "Partial frame should return null";
        assert buf.position() == 0 : "Buffer position should rewind on partial frame";
    }

    private static void testPipelineStream() {
        byte[] pipelined = ("*1\r\n$4\r\nPING\r\n*1\r\n$4\r\nPING\r\n").getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.wrap(pipelined);

        RespToken token1 = RespParser.parse(buf);
        assert token1 != null && token1.getArrayValue().size() == 1;

        RespToken token2 = RespParser.parse(buf);
        assert token2 != null && token2.getArrayValue().size() == 1;

        RespToken token3 = RespParser.parse(buf);
        assert token3 == null;
    }
}
