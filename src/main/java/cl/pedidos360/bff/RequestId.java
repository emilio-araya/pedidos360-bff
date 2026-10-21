package cl.pedidos360.bff;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Identificador de correlación de la request.
 *
 * <p>Se acepta un id entrante solo si tiene caracteres seguros y hasta 64
 * caracteres: un valor con saltos de línea permitiría inyectar entradas falsas
 * en el log.
 */
public final class RequestId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private RequestId() {
    }

    public static String resolve(String incoming) {
        return incoming != null && SAFE.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }

    /**
     * Id de la request en curso, o uno nuevo si la llamada no viene de una
     * request HTTP (por ejemplo, una tarea programada).
     */
    public static String currentOrNew() {
        String current = MDC.get(MDC_KEY);
        return current != null ? current : UUID.randomUUID().toString();
    }
}
