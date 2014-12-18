package com.github.rmannibucau.rules.test.phantomjs;

import com.github.rmannibucau.rules.api.phantomjs.PhantomJsRule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertTrue;

public class PhantomJsRuleTest {
    @Rule
    public final PhantomJsRule rule = new PhantomJsRule();

    @Test
    public void run() throws IOException {
        final int port = 45678;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/test", new HttpHandler() {
            @Override
            public void handle(final HttpExchange httpExchange) throws IOException {
                final byte[] bytes = "OK Phantom".getBytes();
                httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
                final OutputStream responseBody = httpExchange.getResponseBody();
                responseBody.write(bytes);
                responseBody.close();
            }
        });
        server.start();

        try {
            assertTrue(rule.get("http://localhost:" + port + "/test").contains("OK Phantom"));
        } finally {
            server.stop(0);
        }
    }
}
