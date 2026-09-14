package com.redis.test;

import com.redis.config.ServerConfig;
import com.redis.net.RedisServer;
import com.redis.resp.RespParser;
import com.redis.resp.RespToken;
import com.redis.resp.RespWriter;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EndToEndServerTest {
    private static final int TEST_PORT = 16379;

    public static void run() {
        System.out.println("[Test] Running End-To-End NIO Socket Server & Concurrency tests...");
        File testDir = new File("./target/test_data");
        testDir.mkdirs();

        ServerConfig config = new ServerConfig();
        config.setHost("127.0.0.1");
        config.setPort(TEST_PORT);
        config.setDir(testDir.getAbsolutePath());
        config.setAppendOnly(true);
        config.setAofFsync("always");

        // Clean up previous test AOF
        config.getAofFile().delete();
        config.getRdbFile().delete();

        RedisServer server = new RedisServer(config);
        try {
            server.start();
            Thread.sleep(200); // Allow server to bind

            testSocketPing();
            testSocketPipeline();
            testConcurrentClients();
            testSocketPubSub();

            server.stop();
            Thread.sleep(300);

            // Test AOF recovery on new server startup
            testAofRecovery(config);

            System.out.println("[Test] End-To-End Server tests passed successfully!\n");
        } catch (Exception e) {
            server.stop();
            throw new RuntimeException("End-to-end test failed", e);
        }
    }

    private static void testSocketPing() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            byte[] req = RespWriter.encode(RespToken.array(List.of(
                    RespToken.bulkString("PING"),
                    RespToken.bulkString("hello redis")
            )));
            out.write(req);
            out.flush();

            RespToken res = readResponse(in);
            assert res != null && "hello redis".equals(res.getStringValue());
        }
    }

    private static void testSocketPipeline() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", TEST_PORT)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            int n = 50;
            // Pipeline 50 INCR commands in one socket write
            byte[] buffer = new byte[0];
            for (int i = 0; i < n; i++) {
                byte[] cmd = RespWriter.encode(RespToken.array(List.of(
                        RespToken.bulkString("INCR"),
                        RespToken.bulkString("pipeline_counter")
                )));
                byte[] temp = new byte[buffer.length + cmd.length];
                System.arraycopy(buffer, 0, temp, 0, buffer.length);
                System.arraycopy(cmd, 0, temp, buffer.length, cmd.length);
                buffer = temp;
            }

            out.write(buffer);
            out.flush();

            for (int i = 1; i <= n; i++) {
                RespToken res = readResponse(in);
                assert res != null && res.getIntValue() == i : "Pipelined INCR mismatch at " + i;
            }
        }
    }

    private static void testConcurrentClients() throws Exception {
        int clientCount = 5;
        int opsPerClient = 20;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int c = 0; c < clientCount; c++) {
            final int clientId = c;
            executor.submit(() -> {
                try (Socket socket = new Socket("127.0.0.1", TEST_PORT)) {
                    OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream();

                    for (int i = 0; i < opsPerClient; i++) {
                        String key = "c_key_" + clientId + "_" + i;
                        String val = "val_" + i;
                        byte[] setCmd = RespWriter.encode(RespToken.array(List.of(
                                RespToken.bulkString("SET"),
                                RespToken.bulkString(key),
                                RespToken.bulkString(val)
                        )));
                        out.write(setCmd);
                        out.flush();
                        RespToken setRes = readResponse(in);
                        assert setRes.equals(RespToken.OK);

                        byte[] getCmd = RespWriter.encode(RespToken.array(List.of(
                                RespToken.bulkString("GET"),
                                RespToken.bulkString(key)
                        )));
                        out.write(getCmd);
                        out.flush();
                        RespToken getRes = readResponse(in);
                        assert val.equals(getRes.getStringValue());
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assert successCount.get() == clientCount : "All concurrent clients should succeed";
    }

    private static void testSocketPubSub() throws Exception {
        Socket subSocket = new Socket("127.0.0.1", TEST_PORT);
        Socket pubSocket = new Socket("127.0.0.1", TEST_PORT);

        OutputStream subOut = subSocket.getOutputStream();
        InputStream subIn = subSocket.getInputStream();

        OutputStream pubOut = pubSocket.getOutputStream();
        InputStream pubIn = pubSocket.getInputStream();

        // 1. Subscribe to channel "notifications"
        byte[] subCmd = RespWriter.encode(RespToken.array(List.of(
                RespToken.bulkString("SUBSCRIBE"),
                RespToken.bulkString("notifications")
        )));
        subOut.write(subCmd);
        subOut.flush();

        // Read confirmation: ["subscribe", "notifications", 1]
        RespToken subConfirm = readResponse(subIn);
        assert subConfirm != null && subConfirm.getType() == RespToken.Type.ARRAY;
        assert "subscribe".equals(subConfirm.getArrayValue().get(0).getStringValue());

        // 2. Publish message from publisher socket
        byte[] pubCmd = RespWriter.encode(RespToken.array(List.of(
                RespToken.bulkString("PUBLISH"),
                RespToken.bulkString("notifications"),
                RespToken.bulkString("Order #1234 Placed")
        )));
        pubOut.write(pubCmd);
        pubOut.flush();

        // Read publisher result (receivers count)
        RespToken pubRes = readResponse(pubIn);
        assert pubRes != null && pubRes.getIntValue() == 1 : "Should have 1 receiver";

        // 3. Read message on subscriber socket: ["message", "notifications", "Order #1234 Placed"]
        RespToken received = readResponse(subIn);
        assert received != null && received.getType() == RespToken.Type.ARRAY;
        assert "message".equals(received.getArrayValue().get(0).getStringValue());
        assert "notifications".equals(received.getArrayValue().get(1).getStringValue());
        assert "Order #1234 Placed".equals(received.getArrayValue().get(2).getStringValue());

        subSocket.close();
        pubSocket.close();
    }

    private static void testAofRecovery(ServerConfig config) throws Exception {
        // Start second server instance with the same config to verify AOF replay
        RedisServer server2 = new RedisServer(config);
        server2.start();
        Thread.sleep(200);

        try (Socket socket = new Socket("127.0.0.1", TEST_PORT)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Verify the key written before shutdown was restored from AOF!
            byte[] getCmd = RespWriter.encode(RespToken.array(List.of(
                    RespToken.bulkString("GET"),
                    RespToken.bulkString("c_key_0_0")
            )));
            out.write(getCmd);
            out.flush();

            RespToken getRes = readResponse(in);
            assert getRes != null && "val_0".equals(getRes.getStringValue()) : "AOF recovery failed for key";
        } finally {
            server2.stop();
        }
    }

    private static RespToken readResponse(InputStream in) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
        byte[] temp = new byte[1024];
        while (true) {
            int read = in.read(temp);
            if (read == -1) return null;
            buf.put(temp, 0, read);
            buf.flip();
            RespToken token = RespParser.parse(buf);
            if (token != null) {
                return token;
            }
            buf.compact();
        }
    }
}
