package cl.pedidos360.bff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.pedidos360.bff.gateway.BffProxyService;
import cl.pedidos360.bff.gateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
class BffSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BffProxyService proxyService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(proxyService.exchange(
                        any(),
                        any(),
                        anyString(),
                        nullable(MultiValueMap.class),
                        nullable(String.class),
                        anyString()))
                .thenReturn(new GatewayResponse(
                        HttpStatus.OK,
                        "[]",
                        MediaType.APPLICATION_JSON_VALUE,
                        null,
                        "test-request-id"
                ));
    }

    @Test
    void rejectsProtectedRouteWithoutToken() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAuthenticatedClientToReadOrders() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsClientCatalogAdministration() throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsOperatorCatalogAdministration() throws Exception {
        lenient()
                .when(proxyService.exchange(
                        any(), any(), anyString(), nullable(MultiValueMap.class), nullable(String.class), anyString()))
                .thenReturn(new GatewayResponse(
                        HttpStatus.CREATED,
                        "{}",
                        MediaType.APPLICATION_JSON_VALUE,
                        null,
                        "test-request-id"
                ));

        mockMvc.perform(post("/api/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador"))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsClientOperationalStatusChange() throws Exception {
        mockMvc.perform(patch("/api/orders/123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACEPTADO\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsOperatorOperationalStatusChange() throws Exception {
        mockMvc.perform(patch("/api/orders/123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACEPTADO\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador"))))
                .andExpect(status().isOk());
    }

    @Test
    void answersCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/orders")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }
}
