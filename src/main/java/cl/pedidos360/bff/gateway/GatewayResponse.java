package cl.pedidos360.bff.gateway;

import org.springframework.http.HttpStatusCode;

public record GatewayResponse(
        HttpStatusCode statusCode,
        String body,
        String contentType,
        String location,
        String requestId
) {
    public static GatewayResponse empty(HttpStatusCode statusCode, String requestId) {
        return new GatewayResponse(statusCode, null, null, null, requestId);
    }
}
