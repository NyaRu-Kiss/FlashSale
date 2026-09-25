package com.flashsale.order;

import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.RequestLine;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeignHttpClientR21Test {
    interface ProbeClient {
        @RequestLine("GET /probe")
        String probe();
    }

    @Test
    void sendsRealHttpRequestThroughFeign() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/probe", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            ProbeClient client = Feign.builder().target(ProbeClient.class,
                    "http://127.0.0.1:" + server.getAddress().getPort());
            assertEquals("ok", client.probe());
        } finally {
            server.stop(0);
        }
    }
}
