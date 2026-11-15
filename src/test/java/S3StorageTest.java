import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S3StorageTest {
    private HttpServer server;
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> body = new AtomicReference<>("account_number,email,payment_due_date,payment_amount\nACC-1,a@example.com,2026-12-01,4.50\n");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().close();
            int responseStatus = status.get();
            byte[] responseBody = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void streamsResponseFromPathStyleBucketAndKey() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        server.removeContext("/");
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            byte[] responseBody = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                S3Storage.open(config(), "jobs/job-7/input.csv"), StandardCharsets.UTF_8))) {
            assertEquals("account_number,email,payment_due_date,payment_amount", reader.readLine());
            assertEquals("ACC-1,a@example.com,2026-12-01,4.50", reader.readLine());
        }
        assertEquals("/payments/jobs/job-7/input.csv", requestPath.get());
    }

    @Test
    void missingObjectIsPermanent() {
        status.set(404);
        body.set("<Error><Code>NoSuchKey</Code><Message>not found</Message></Error>");
        assertThrows(WorkerInputException.class, () -> S3Storage.open(config(), "absent.csv"));
    }

    @Test
    void throttlingAndTimeoutAreRetryable() {
        for (int code : new int[]{408, 429, 503}) {
            status.set(code);
            body.set("<Error><Code>SlowDown</Code><Message>retry later</Message></Error>");
            assertThrows(RetryableStorageException.class, () -> S3Storage.open(config(), "input.csv"),
                    "HTTP " + code);
        }
    }

    @Test
    void authorizationFailureIsPermanent() {
        status.set(403);
        body.set("<Error><Code>AccessDenied</Code></Error>");
        assertThrows(WorkerInputException.class, () -> S3Storage.open(config(), "input.csv"));
    }

    @Test
    void refusedConnectionIsRetryable() {
        S3Config config = config();
        server.stop(0);
        server = null;
        assertThrows(RetryableStorageException.class, () -> S3Storage.open(config, "input.csv"));
    }

    private S3Config config() {
        return new S3Config(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "payments", "access", "secret", "us-east-1");
    }
}
