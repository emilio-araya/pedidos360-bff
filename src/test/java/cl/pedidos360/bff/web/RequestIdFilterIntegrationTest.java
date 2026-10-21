package cl.pedidos360.bff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.pedidos360.bff.gateway.BffProxyService;
import cl.pedidos360.bff.gateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;

@SpringBootTest(properties = {
        "security.jwt.mode=local",
        "security.jwt.issuer=https://login.microsoftonline.com/pedidos360-local/v2.0",
        "security.jwt.audience=api://150f51db-4084-4979-b1a1-e6a6e7893a01",
        "security.jwt.local-secret=pedidos360-local-secret-change-me-32-bytes-minimum",
        "security.cors.allowed-origins=http://localhost:4200"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RequestIdFilterIntegrationTest {

    private static final String HEADER = "X-Request-Id";

    @SuppressWarnings("unchecked")
    private static final Class<MultiValueMap<String, String>> MULTI_VALUE_MAP_TYPE =
            (Class<MultiValueMap<String, String>>) (Class<?>) MultiValueMap.class;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BffProxyService proxyService;

    @BeforeEach
    void setUp() {
        // requestId null: el header lo pone el filtro, no el controlador.
        lenient()
                .when(proxyService.exchange(
                        any(),
                        any(),
                        anyString(),
                        nullable(MULTI_VALUE_MAP_TYPE),
                        nullable(String.class),
                        anyString()))
                .thenReturn(new GatewayResponse(
                        HttpStatus.OK,
                        "{}",
                        MediaType.APPLICATION_JSON_VALUE,
                        null,
                        null));
    }

    @Test
    void generatesARequestIdWhenTheClientDoesNotSendOne() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders").with(client()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(HEADER))
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void preservesAValidIncomingRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders")
                        .header(HEADER, "checkout-abc.123")
                        .with(client()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(HEADER)).isEqualTo("checkout-abc.123");
    }

    @Test
    void replacesAnUnsafeRequestIdInsteadOfLoggingIt() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders")
                        .header(HEADER, "abc\ndef")
                        .with(client()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(HEADER))
                .isNotEqualTo("abc\ndef")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor client() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"));
    }
}
