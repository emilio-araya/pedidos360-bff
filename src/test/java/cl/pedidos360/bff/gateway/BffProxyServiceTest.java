package cl.pedidos360.bff.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

class BffProxyServiceTest {

    private HttpServer server;
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/orders", this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void forwardsValidatedBearerAndRequestData() {
        BffProxyService service = new BffProxyService(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "http://localhost:1"
        );
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("status", "CREADO");
        query.add("customer", "cliente uno");

        GatewayResponse response = service.exchange(
                Upstream.ORDERS,
                HttpMethod.PATCH,
                "/api/orders",
                query,
                "{\"items\":[]}",
                "Bearer validated-token"
        );

        assertThat(response.statusCode().value()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("{\"accepted\":true}");
        assertThat(response.requestId()).isNotBlank();
        assertThat(captured.get().method()).isEqualTo("PATCH");
        assertThat(captured.get().authorization()).isEqualTo("Bearer validated-token");
        assertThat(captured.get().body()).isEqualTo("{\"items\":[]}");
        assertThat(captured.get().query()).isEqualTo("status=CREADO&customer=cliente%20uno");
        assertThat(captured.get().requestId()).isNotBlank();
    }

    private void respond(HttpExchange exchange) throws IOException {
        captured.set(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Request-Id"),
                exchange.getRequestURI().getRawQuery(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        ));
        byte[] response = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(202, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record CapturedRequest(
            String method,
            String authorization,
            String requestId,
            String query,
            String body
    ) {
    }
}
