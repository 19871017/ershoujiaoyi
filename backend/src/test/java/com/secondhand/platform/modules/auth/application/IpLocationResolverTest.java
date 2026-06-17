package com.secondhand.platform.modules.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IpLocationResolverTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void configuredHttpProviderShouldResolvePublicIpProvinceAndCity() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = startServer("""
                {"province":"浙江","city":"杭州"}
                """, hits);
        IpLocationResolver resolver = new DefaultIpLocationResolver(endpoint("/ip/{ip}"), HttpClient.newHttpClient());

        String location = resolver.resolve("203.0.113.88");

        assertEquals("浙江 杭州", location);
        assertEquals(1, hits.get());
    }

    @Test
    void privateAndLocalIpShouldUseLocalLabelsWithoutCallingProvider() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = startServer("""
                {"province":"浙江","city":"杭州"}
                """, hits);
        IpLocationResolver resolver = new DefaultIpLocationResolver(endpoint("/ip/{ip}"), HttpClient.newHttpClient());

        assertEquals("本机", resolver.resolve("127.0.0.1"));
        assertEquals("内网", resolver.resolve("192.168.1.9"));
        assertEquals(0, hits.get());
    }

    @Test
    void providerFailureShouldFallBackToUnknownInsteadOfBlockingLogin() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = startServer("{}", hits);
        IpLocationResolver resolver = new DefaultIpLocationResolver(endpoint("/ip/{ip}"), HttpClient.newHttpClient());

        assertEquals("IP属地未知", resolver.resolve("198.51.100.9"));
        assertEquals(1, hits.get());
    }

    private HttpServer startServer(String body, AtomicInteger hits) throws Exception {
        HttpServer next = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        next.createContext("/ip", exchange -> {
            hits.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        next.start();
        return next;
    }

    private String endpoint(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
