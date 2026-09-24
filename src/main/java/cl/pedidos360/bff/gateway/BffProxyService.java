package cl.pedidos360.bff.gateway;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class BffProxyService {

    private final RestClient ordersClient;
    private final RestClient catalogClient;
    private final String ordersClientUri;
    private final String catalogClientUri;

    @Autowired
    public BffProxyService(
            @Value("${services.orders.base-url}") String ordersBaseUrl,
            @Value("${services.catalog.base-url}") String catalogBaseUrl,
            @Value("${services.http.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${services.http.read-timeout-ms:10000}") long readTimeoutMs
    ) {
        this.ordersClientUri = stripTrailingSlash(ordersBaseUrl);
        this.catalogClientUri = stripTrailingSlash(catalogBaseUrl);
        this.ordersClient = createClient(ordersClientUri, connectTimeoutMs, readTimeoutMs);
        this.catalogClient = createClient(catalogClientUri, connectTimeoutMs, readTimeoutMs);
    }

    BffProxyService(String ordersBaseUrl, String catalogBaseUrl) {
        this(ordersBaseUrl, catalogBaseUrl, 2000L, 10000L);
    }

    private static RestClient createClient(String baseUrl, long connectTimeoutMs, long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public GatewayResponse exchange(
            Upstream upstream,
            HttpMethod method,
            String path,
            MultiValueMap<String, String> queryParameters,
            String body,
            String authorization
    ) {
        RestClient client = clientFor(upstream);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(clientUri(upstream))
                .path(path);

        if (queryParameters != null) {
            queryParameters.forEach((name, values) -> values.forEach(value -> uriBuilder.queryParam(name, value)));
        }

        String requestId = UUID.randomUUID().toString();
        RestClient.RequestBodySpec request = client
                .method(method)
                .uri(uriBuilder.build().encode().toUri())
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-Request-Id", requestId)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        try {
            return request.exchange((clientRequest, clientResponse) -> {
                String responseBody = null;
                if (clientResponse.getBody() != null) {
                    try {
                        responseBody = StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8);
                    } catch (IOException exception) {
                        throw new GatewayTransportException("No se pudo leer la respuesta del microservicio", exception);
                    }
                }
                String upstreamRequestId = clientResponse.getHeaders().getFirst("X-Request-Id");
                return new GatewayResponse(
                        HttpStatusCode.valueOf(clientResponse.getStatusCode().value()),
                        responseBody,
                        clientResponse.getHeaders().getContentType() == null
                                ? null
                                : clientResponse.getHeaders().getContentType().toString(),
                        clientResponse.getHeaders().getFirst(HttpHeaders.LOCATION),
                        upstreamRequestId == null ? requestId : upstreamRequestId
                );
            });
        } catch (GatewayTransportException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new GatewayUnavailableException("No existe conexión con " + upstream.name().toLowerCase(), exception);
        } catch (RestClientException exception) {
            throw new GatewayTransportException("Error al invocar " + upstream.name().toLowerCase(), exception);
        }
    }

    private RestClient clientFor(Upstream upstream) {
        return upstream == Upstream.ORDERS ? ordersClient : catalogClient;
    }

    private String clientUri(Upstream upstream) {
        if (upstream == Upstream.ORDERS) {
            return ordersClientUri;
        }
        return catalogClientUri;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
