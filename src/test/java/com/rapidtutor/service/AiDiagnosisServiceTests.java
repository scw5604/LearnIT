package com.rapidtutor.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AiDiagnosisServiceTests {

    @Test
    public void testApiTimeoutTriggersOfflineFallback() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = failingServer(hits);
        try {
            AiDiagnosisService service = servicePointedAt(server);
            String note = service.prepareNote("IST 440W", "Patterns", "The call fails before a note comes back.");

            assertEquals(1, hits.get());
            assertEquals(SocraticNotes.offline("Patterns"), note);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void aFailedFollowUpUsesTheOfflineFollowUp() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = failingServer(hits);
        try {
            AiDiagnosisService service = servicePointedAt(server);
            String answer = "I tried rewriting the loop.";
            String note = service.prepareFollowUp(
                    "IST 440W",
                    "Patterns",
                    "The follow-up call fails.",
                    "Core concept: prior.\nDiagnostic question: one?\nStarting step: try.\n",
                    answer);

            assertEquals(1, hits.get());
            assertEquals(SocraticNotes.offlineFollowUp("Patterns", answer), note);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void anUnusableDiagnosisReplyUsesTheOfflineNote() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        byte[] body = """
                {"choices":[{"message":{"content":"Core concept: the answer is 4.\\nDiagnostic question: Why?\\nStarting step: Add them.\\n"}}]}
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hang", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String note = servicePointedAt(server).prepareNote("IST 440W", "Patterns", "The model states an answer.");
            assertEquals(1, hits.get());
            assertEquals(SocraticNotes.offline("Patterns"), note);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void anAcceptedDiagnosisReplyIsShown() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        String accepted = """
                Core concept: Addition combines two quantities.
                Diagnostic question: What does the plus sign tell you to do?
                Starting step: Rewrite the expression in words.
                """.trim();
        byte[] body = ("{\"choices\":[{\"message\":{\"content\":\""
                + accepted.replace("\n", "\\n")
                + "\"}}]}").getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hang", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String note = servicePointedAt(server).prepareNote("IST 440W", "Patterns", "A usable reply.");
            assertEquals(1, hits.get());
            assertEquals(accepted, note);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void anAcceptedFollowUpIsShown() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        String accepted = """
                Core concept: The loop variable is the part already understood.
                Diagnostic question: Which value does the variable have on the last pass?
                Starting step: Write that value next to the loop condition.
                """.trim();
        String answer = "I tried rewriting the loop.";
        byte[] body = ("{\"choices\":[{\"message\":{\"content\":\""
                + accepted.replace("\n", "\\n")
                + "\"}}]}").getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hang", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String note = servicePointedAt(server).prepareFollowUp(
                    "IST 440W", "Patterns", "A usable follow-up.", "Core concept: prior.", answer);
            assertEquals(1, hits.get());
            assertEquals(accepted, note);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void aCallTurnedAwayByTheInFlightCapReturnsTheOfflineNote() throws Exception {
        int cap = inFlightCap();
        CountDownLatch held = new CountDownLatch(cap);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService httpPool = Executors.newFixedThreadPool(cap + 2);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hang", exchange -> {
            held.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.setExecutor(httpPool);
        server.start();
        ExecutorService callers = Executors.newFixedThreadPool(cap + 1);
        List<Future<String>> hung = new ArrayList<>();
        try {
            AiDiagnosisService service = servicePointedAt(server);
            for (int i = 0; i < cap; i++) {
                hung.add(callers.submit(() -> service.prepareNote("IST 440W", "Cap", "This call holds a permit.")));
            }
            assertTrue(held.await(10, TimeUnit.SECONDS), "the in-flight calls did not reach the cap");

            Future<String> turnedAway = callers.submit(() ->
                    service.prepareNote("IST 440W", "Cap", "This call should not wait on the network."));
            assertEquals(SocraticNotes.offline("Cap"), turnedAway.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            for (Future<String> call : hung) {
                try {
                    call.get(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // the held sockets close when the server stops
                }
            }
            callers.shutdownNow();
            server.stop(0);
            httpPool.shutdownNow();
        }
    }

    private static HttpServer failingServer(AtomicInteger hits) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hang", exchange -> {
            hits.incrementAndGet();
            byte[] body = "unavailable".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static AiDiagnosisService servicePointedAt(HttpServer server) throws Exception {
        AiDiagnosisService service = new AiDiagnosisService(RestClient.builder());
        setField(service, "apiKey", "not-the-mock-key");
        setField(service, "model", "test-model");
        setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/hang");
        return service;
    }

    private static int inFlightCap() throws Exception {
        Field field = AiDiagnosisService.class.getDeclaredField("MAX_IN_FLIGHT");
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
