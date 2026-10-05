package com.nexaai.gateway.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in upstream service that records what the gateway actually forwarded.
 *
 * <p><strong>Why the gateway's security claims need this.</strong> "The gateway strips
 * spoofable identity headers" and "the gateway replaces the token with verified identity
 * headers" are claims about bytes on the wire to another process. Asserting them against the
 * gateway's own objects would only prove the gateway agrees with itself. So the tests route
 * real traffic through a real HTTP connection to this stub and inspect what arrived.
 *
 * <p>Built on {@link HttpServer} from the JDK: no extra dependency, and it is a genuinely
 * separate process-local server, so the gateway is doing real networking rather than calling
 * itself.
 */
public final class UpstreamStub implements AutoCloseable {

    private final HttpServer server;

    /** Every request that reached the upstream, in order. */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    private volatile int statusToReturn = 200;

    private volatile String bodyToReturn = "{\"stub\":true}";

    public UpstreamStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        // A small fixed pool: the default is unbounded, which is a poor habit to model in a
        // test double.
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
    }

    /** The base URI to point a route's {@code uri} at. */
    public String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respondWith(int status, String body) {
        this.statusToReturn = status;
        this.bodyToReturn = body;
    }

    /** Everything recorded so far. */
    public List<RecordedRequest> requests() {
        return new ArrayList<>(requests);
    }

    /** The most recent request. Fails loudly if nothing arrived, which is itself the finding. */
    public RecordedRequest lastRequest() {
        if (requests.isEmpty()) {
            throw new IllegalStateException("No request reached the upstream stub.");
        }
        return requests.get(requests.size() - 1);
    }

    public void clear() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();

        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(),
                copyHeaders(exchange),
                new String(requestBody, StandardCharsets.UTF_8)));

        byte[] response = bodyToReturn.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusToReturn, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    /** Header names are lower-cased, because HTTP header names are case-insensitive. */
    private static Map<String, String> copyHeaders(HttpExchange exchange) {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (name != null && !values.isEmpty()) {
                headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values));
            }
        });
        return headers;
    }

    /**
     * One request as the upstream saw it.
     *
     * @param headers lower-cased names, values joined with commas
     */
    public record RecordedRequest(
            String method,
            String path,
            String query,
            Map<String, String> headers,
            String body) {

        /** The value of a header, or null when it was absent. */
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        public boolean hasHeader(String name) {
            return headers.containsKey(name.toLowerCase(Locale.ROOT));
        }
    }
}