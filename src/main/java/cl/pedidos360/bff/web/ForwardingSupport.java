package cl.pedidos360.bff.web;

import cl.pedidos360.bff.gateway.GatewayResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

/** Shared by the controllers that forward requests to the upstream services. */
final class ForwardingSupport {

    private ForwardingSupport() {
    }

    /** Copies status, body, content type, Location and X-Request-Id from the upstream answer. */
    static ResponseEntity<String> toResponseEntity(GatewayResponse response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.statusCode());
        if (StringUtils.hasText(response.contentType())) {
            builder.contentType(MediaType.parseMediaType(response.contentType()));
        } else if (response.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON);
        }
        if (response.location() != null) {
            builder.header(HttpHeaders.LOCATION, response.location());
        }
        if (response.requestId() != null) {
            builder.header("X-Request-Id", response.requestId());
        }
        return builder.body(response.body());
    }

    /** Rejects path segments that could change the upstream path. */
    static String segment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9-]{1,100}")) {
            throw new IllegalArgumentException("Identificador no válido");
        }
        return value;
    }
}
