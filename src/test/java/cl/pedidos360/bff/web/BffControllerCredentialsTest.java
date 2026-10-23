package cl.pedidos360.bff.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cl.pedidos360.bff.gateway.BffProxyService;
import cl.pedidos360.bff.gateway.GatewayResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Fija el invariante de credenciales en el propio controlador, sin depender
 * de que SecurityConfig bloquee antes: solo la vitrina reenvia sin token.
 */
class BffControllerCredentialsTest {

    private final BffProxyService proxyService = mock(BffProxyService.class);
    private final BffController controller = new BffController(proxyService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void protectedRoutesNeverForwardWithoutCredentials() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> controller.listOrders(null, new LinkedMultiValueMap<>()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(proxyService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicCatalogReadsForwardWithoutCredentials() {
        SecurityContextHolder.clearContext();
        when(proxyService.exchange(any(), any(), anyString(),
                nullable(MultiValueMap.class), nullable(String.class), nullable(String.class)))
                .thenReturn(new GatewayResponse(HttpStatus.OK, "[]", "application/json", null, "rid"));

        controller.listProducts(null, new LinkedMultiValueMap<>());

        verify(proxyService).exchange(any(), any(), anyString(),
                nullable(MultiValueMap.class), isNull(), isNull());
    }
}
