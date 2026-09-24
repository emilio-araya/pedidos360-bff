package cl.pedidos360.bff.web;

import cl.pedidos360.bff.gateway.GatewayTransportException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BffExceptionHandler {

    @ExceptionHandler(GatewayTransportException.class)
    ProblemDetail handleGateway(GatewayTransportException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage()
        );
        problem.setTitle("Microservicio no disponible");
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("requestId", UUID.randomUUID().toString());
        problem.setProperty("timestamp", java.time.Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Solicitud no válida");
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        return problem;
    }
}
