package com.recipick.domain.payment.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 테스트 상점만 흉내 낸다. 요청 원문·Authorization·키를 출력하지 않는다.
public final class BillingKeyDeletionStubServer implements AutoCloseable {
    public static final String STORE = "store-billing-deletion-test";
    private final HttpServer server;
    private final ExecutorService handlers = Executors.newCachedThreadPool();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicInteger gets = new AtomicInteger();
    private final AtomicInteger deletes = new AtomicInteger();
    private volatile CountDownLatch arrived;
    private volatile CountDownLatch release;
    private volatile String lastRawPath;
    private volatile String lastRawQuery;

    public static final class Entry {
        public volatile boolean deleted;
        public volatile boolean disappearAfterDelete;
        public volatile boolean failGetAfterDelete;
        public volatile int getHttp = 200;
        public volatile int deleteHttp = 200;
        public volatile String errorType = "BILLING_KEY_NOT_FOUND";
        public volatile String responseKey;
        public volatile String store = STORE;
        public volatile String channelType = "TEST";
        public volatile boolean applyDelete = true;
        public volatile boolean malformedGet;
    }

    public BillingKeyDeletionStubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("로컬 삭제 스텁을 열지 못했습니다", e);
        }
        server.setExecutor(handlers);
        server.createContext("/billing-keys/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public Entry register(String key) {
        Entry entry = new Entry();
        entries.put(key, entry);
        return entry;
    }

    public int getCount() { return gets.get(); }
    public int deleteCount() { return deletes.get(); }
    public String lastRawPath() { return lastRawPath; }
    public String lastRawQuery() { return lastRawQuery; }

    public CountDownLatch holdDeleteReply() {
        arrived = new CountDownLatch(1);
        release = new CountDownLatch(1);
        return arrived;
    }

    public void releaseDeleteReply() {
        CountDownLatch current = release;
        if (current != null) { current.countDown(); }
    }

    public void reset() {
        releaseDeleteReply();
        entries.clear();
        gets.set(0);
        deletes.set(0);
        arrived = null;
        release = null;
        lastRawPath = null;
        lastRawQuery = null;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            lastRawPath = exchange.getRequestURI().getRawPath();
            lastRawQuery = exchange.getRequestURI().getRawQuery();
            String key = URLDecoder.decode(lastRawPath.substring("/billing-keys/".length()), StandardCharsets.UTF_8);
            Entry entry = entries.get(key);
            String method = exchange.getRequestMethod();
            if ("GET".equals(method)) {
                gets.incrementAndGet();
                if (entry == null || (entry.deleted && entry.disappearAfterDelete)) {
                    send(exchange, 404, "{\"type\":\"BILLING_KEY_NOT_FOUND\"}");
                } else if (entry.getHttp != 200 || (entry.deleted && entry.failGetAfterDelete)) {
                    send(exchange, entry.getHttp == 200 ? 500 : entry.getHttp,
                        "{\"type\":\"" + entry.errorType + "\"}");
                } else if (entry.malformedGet) {
                    send(exchange, 200, "{broken");
                } else {
                    send(exchange, 200, "{\"status\":\"" + (entry.deleted ? "DELETED" : "ISSUED")
                        + "\",\"billingKey\":\"" + escape(entry.responseKey == null ? key : entry.responseKey)
                        + "\",\"storeId\":\"" + escape(entry.store) + "\",\"channels\":[{\"type\":\""
                        + escape(entry.channelType) + "\"}]}");
                }
                return;
            }
            if (!"DELETE".equals(method)) {
                send(exchange, 405, "{}");
                return;
            }
            deletes.incrementAndGet();
            if (entry == null) {
                send(exchange, 404, "{\"type\":\"BILLING_KEY_NOT_FOUND\"}");
                return;
            }
            if (entry.deleteHttp != 200) {
                send(exchange, entry.deleteHttp, "{\"type\":\"" + entry.errorType + "\"}");
                return;
            }
            if (entry.applyDelete) { entry.deleted = true; }
            CountDownLatch signal = arrived;
            CountDownLatch gate = release;
            if (signal != null) { signal.countDown(); }
            if (gate != null) {
                try {
                    if (!gate.await(10, TimeUnit.SECONDS)) {
                        send(exchange, 500, "{}");
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            send(exchange, 200, "{\"deletedAt\":\"2026-11-02T13:00:00Z\"}");
        } finally {
            exchange.close();
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        releaseDeleteReply();
        server.stop(0);
        handlers.shutdownNow();
    }
}
