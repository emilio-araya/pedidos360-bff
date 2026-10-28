package cl.pedidos360.bff.web;

import cl.pedidos360.bff.gateway.BffProxyService;
import cl.pedidos360.bff.gateway.GatewayResponse;
import cl.pedidos360.bff.gateway.Upstream;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api", "/aws/api"})
public class BffController {

    private static final String STAFF = "hasAnyRole('Admin', 'Operador')";

    private final BffProxyService proxyService;

    public BffController(BffProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @GetMapping("/catalog/products")
    public ResponseEntity<String> listProducts(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> query
    ) {
        return exchangePublic(Upstream.CATALOG, HttpMethod.GET, "/api/catalog/products", query, authorization);
    }

    @GetMapping("/catalog/products/{id}")
    public ResponseEntity<String> getProduct(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchangePublic(Upstream.CATALOG, HttpMethod.GET, productPath(id), null, authorization);
    }

    @PostMapping("/catalog/products")
    @PreAuthorize(STAFF)
    public ResponseEntity<String> createProduct(
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.CATALOG, HttpMethod.POST, "/api/catalog/products", null, body, authorization);
    }

    @PutMapping("/catalog/products/{id}")
    @PreAuthorize(STAFF)
    public ResponseEntity<String> updateProduct(
            @PathVariable String id,
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.CATALOG, HttpMethod.PUT, productPath(id), null, body, authorization);
    }

    @DeleteMapping("/catalog/products/{id}")
    @PreAuthorize(STAFF)
    public ResponseEntity<String> deleteProduct(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.CATALOG, HttpMethod.DELETE, productPath(id), null, null, authorization);
    }

    @PatchMapping("/catalog/products/{id}/stock")
    @PreAuthorize(STAFF)
    public ResponseEntity<String> updateStock(
            @PathVariable String id,
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.CATALOG, HttpMethod.PATCH, productPath(id) + "/stock", null, body, authorization);
    }

    @GetMapping("/orders")
    public ResponseEntity<String> listOrders(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> query
    ) {
        return exchange(Upstream.ORDERS, HttpMethod.GET, "/api/orders", query, null, authorization);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<String> getOrder(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.ORDERS, HttpMethod.GET, orderPath(id), null, null, authorization);
    }

    @PostMapping("/orders")
    public ResponseEntity<String> createOrder(
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.ORDERS, HttpMethod.POST, "/api/orders", null, body, authorization);
    }

    @PutMapping("/orders/{id}")
    public ResponseEntity<String> updateOrder(
            @PathVariable String id,
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.ORDERS, HttpMethod.PUT, orderPath(id), null, body, authorization);
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<String> cancelCreatedOrder(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.ORDERS, HttpMethod.DELETE, orderPath(id), null, null, authorization);
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize(STAFF)
    public ResponseEntity<String> changeOrderStatus(
            @PathVariable String id,
            @RequestBody String body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return exchange(Upstream.ORDERS, HttpMethod.PATCH, orderPath(id) + "/status", null, body, authorization);
    }

    /** Rutas protegidas: sin credenciales no se reenvia nada. */
    private ResponseEntity<String> exchange(
            Upstream upstream,
            HttpMethod method,
            String path,
            MultiValueMap<String, String> query,
            String body,
            String authorization
    ) {
        return forward(upstream, method, path, query, body, requiredBearer(authorization));
    }

    /** Solo la vitrina del catalogo: lectura que se reenvia con o sin usuario. */
    private ResponseEntity<String> exchangePublic(
            Upstream upstream,
            HttpMethod method,
            String path,
            MultiValueMap<String, String> query,
            String authorization
    ) {
        return forward(upstream, method, path, query, null, optionalBearer(authorization));
    }

    private ResponseEntity<String> forward(
            Upstream upstream,
            HttpMethod method,
            String path,
            MultiValueMap<String, String> query,
            String body,
            String bearer
    ) {
        GatewayResponse response = proxyService.exchange(
                upstream, method, preserveProviderPrefix(path), query, body, bearer);
        return ForwardingSupport.toResponseEntity(response);
    }

    private String requiredBearer(String authorizationHeader) {
        String bearer = optionalBearer(authorizationHeader);
        if (bearer == null) {
            throw new IllegalStateException("No hay un token JWT autenticado");
        }
        return bearer;
    }

    private String optionalBearer(String authorizationHeader) {
        if (SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken authentication) {
            return "Bearer " + authentication.getToken().getTokenValue();
        }
        if (StringUtils.hasText(authorizationHeader)) {
            return authorizationHeader;
        }
        return null;
    }

    private String preserveProviderPrefix(String path) {
        // Se compara la ruta dentro de la aplicacion, no el URI completo:
        // getRequestURI() incluye el context path, asi que con
        // server.servlet.context-path configurado una peticion a
        // /bff/aws/api/orders no reconoceria el prefijo y se reenviaria a
        // /api/orders, es decir al namespace equivocado.
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String uri = request.getRequestURI();
            String contextPath = request.getContextPath();
            String pathWithinApplication =
                    contextPath == null || contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
            if (pathWithinApplication.startsWith("/aws/api")) {
                return "/aws/api" + path.substring("/api".length());
            }
        }
        return path;
    }

    private String productPath(String id) {
        return "/api/catalog/products/" + ForwardingSupport.segment(id);
    }

    private String orderPath(String id) {
        return "/api/orders/" + ForwardingSupport.segment(id);
    }
}
