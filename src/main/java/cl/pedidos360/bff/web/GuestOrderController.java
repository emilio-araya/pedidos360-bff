package cl.pedidos360.bff.web;

import cl.pedidos360.bff.gateway.BffProxyService;
import cl.pedidos360.bff.gateway.Upstream;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guest checkout: public routes of the Entra namespace only. They are never
 * forwarded with credentials, not even when the client sent a token: the
 * tracking token in the path is the only proof, and a stale bearer would make
 * orders answer 401.
 */
@RestController
@RequestMapping("/api/guest-orders")
public class GuestOrderController {

    private static final String BASE_PATH = "/api/guest-orders";

    private final BffProxyService proxyService;

    public GuestOrderController(BffProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping
    public ResponseEntity<String> createGuestOrder(@RequestBody String body) {
        return forward(HttpMethod.POST, BASE_PATH, body);
    }

    @PostMapping("/link-requests")
    public ResponseEntity<String> requestTrackingLink(@RequestBody String body) {
        return forward(HttpMethod.POST, BASE_PATH + "/link-requests", body);
    }

    @GetMapping("/{token}")
    public ResponseEntity<String> getGuestOrder(@PathVariable String token) {
        return forward(HttpMethod.GET, tokenPath(token), null);
    }

    @PostMapping("/{token}/cancel")
    public ResponseEntity<String> cancelGuestOrder(@PathVariable String token) {
        return forward(HttpMethod.POST, tokenPath(token) + "/cancel", null);
    }

    private ResponseEntity<String> forward(HttpMethod method, String path, String body) {
        return ForwardingSupport.toResponseEntity(
                proxyService.exchange(Upstream.ORDERS, method, path, null, body, null));
    }

    private String tokenPath(String token) {
        return BASE_PATH + "/" + ForwardingSupport.segment(token);
    }
}
