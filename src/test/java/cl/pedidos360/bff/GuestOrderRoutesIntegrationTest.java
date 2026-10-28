package cl.pedidos360.bff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.pedidos360.bff.gateway.BffProxyService;
import cl.pedidos360.bff.gateway.GatewayResponse;
import cl.pedidos360.bff.gateway.Upstream;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.MultiValueMap;

/**
 * Guest checkout routes (S1, S4, S5, S6 via S7): public in the Entra chain and
 * always forwarded to orders without an Authorization header.
 */
@SpringBootTest(properties = {
        "security.jwt.mode=local",
        "security.jwt.issuer=https://login.microsoftonline.com/pedidos360-local/v2.0",
        "security.jwt.audience=api://150f51db-4084-4979-b1a1-e6a6e7893a01",
        "security.jwt.local-secret=pedidos360-local-secret-change-me-32-bytes-minimum",
        "security.cors.allowed-origins=http://localhost:4200"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class GuestOrderRoutesIntegrationTest {

    @SuppressWarnings("unchecked")
    private static final Class<MultiValueMap<String, String>> MULTI_VALUE_MAP_TYPE =
            (Class<MultiValueMap<String, String>>) (Class<?>) MultiValueMap.class;

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ORDER_BODY =
            "{\"email\":\"cliente@duocuc.cl\",\"items\":[{\"productId\":\"6f1c2a10-0000-4000-8000-000000000001\",\"quantity\":2}]}";
    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
    // The real proxy sends the request's own correlation id upstream and orders echoes it.
    private static final String REQUEST_ID = "guest-request-id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BffProxyService proxyService;

    private void upstreamAnswers(HttpStatus status, String body, String contentType) {
        when(proxyService.exchange(any(), any(), anyString(), nullable(MULTI_VALUE_MAP_TYPE),
                nullable(String.class), nullable(String.class)))
                .thenReturn(new GatewayResponse(status, body, contentType, null, REQUEST_ID));
    }

    @Test
    void forwardsAnonymousGuestOrderCreationWithBodyAndWithoutCredentials() throws Exception {
        upstreamAnswers(HttpStatus.CREATED, "{\"orderNumber\":\"P-1\",\"trackingToken\":\"" + TOKEN + "\"}",
                MediaType.APPLICATION_JSON_VALUE);

        mockMvc.perform(post("/api/guest-orders")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(content().json("{\"orderNumber\":\"P-1\",\"trackingToken\":\"" + TOKEN + "\"}"));

        verify(proxyService).exchange(eq(Upstream.ORDERS), eq(HttpMethod.POST), eq("/api/guest-orders"),
                isNull(), eq(ORDER_BODY), isNull());
    }

    @Test
    void dropsAStaleBearerOnGuestOrderCreation() throws Exception {
        upstreamAnswers(HttpStatus.CREATED, "{}", MediaType.APPLICATION_JSON_VALUE);

        mockMvc.perform(post("/api/guest-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY))
                .andExpect(status().isCreated());

        verify(proxyService).exchange(eq(Upstream.ORDERS), eq(HttpMethod.POST), eq("/api/guest-orders"),
                isNull(), eq(ORDER_BODY), isNull());
    }

    @Test
    void dropsAValidStaffTokenOnGuestOrderCreation() throws Exception {
        upstreamAnswers(HttpStatus.CREATED, "{}", MediaType.APPLICATION_JSON_VALUE);

        mockMvc.perform(post("/api/guest-orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY))
                .andExpect(status().isCreated());

        verify(proxyService).exchange(eq(Upstream.ORDERS), eq(HttpMethod.POST), eq("/api/guest-orders"),
                isNull(), eq(ORDER_BODY), isNull());
    }

    @Test
    void forwardsGuestOrderLookupByToken() throws Exception {
        upstreamAnswers(HttpStatus.OK, "{\"orderNumber\":\"P-1\",\"status\":\"CREADO\"}",
                MediaType.APPLICATION_JSON_VALUE);

        mockMvc.perform(get("/api/guest-orders/" + TOKEN).header(HttpHeaders.AUTHORIZATION, "Bearer x"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"orderNumber\":\"P-1\",\"status\":\"CREADO\"}"));

        verify(proxyService).exchange(eq(Upstream.ORDERS), eq(HttpMethod.GET), eq("/api/guest-orders/" + TOKEN),
                isNull(), isNull(), isNull());
    }

    @Test
    void passesUpstreamNotFoundProblemThrough() throws Exception {
        String problem = "{\"status\":404,\"title\":\"Not Found\"}";
        upstreamAnswers(HttpStatus.NOT_FOUND, problem, PROBLEM_JSON);

        mockMvc.perform(get("/api/guest-orders/" + TOKEN).header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(content().json(problem));
    }

    @Test
    void forwardsGuestCancellationWithoutBodyOrCredentials() throws Exception {
        String problem = "{\"status\":409,\"title\":\"Conflict\"}";
        upstreamAnswers(HttpStatus.CONFLICT, problem, PROBLEM_JSON);

        mockMvc.perform(post("/api/guest-orders/" + TOKEN + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON))
                .andExpect(content().json(problem));

        verify(proxyService).exchange(eq(Upstream.ORDERS), eq(HttpMethod.POST),
                eq("/api/guest-orders/" + TOKEN + "/cancel"), isNull(), isNull(), isNull());
    }

    @Test
    void forwardsLinkRequestsToTheLiteralRouteWithBody() throws Exception {
        upstreamAnswers(HttpStatus.ACCEPTED, null, null);
        String body = "{\"email\":\"cliente@duocuc.cl\",\"orderNumber\":\"P-1\"}";

        mockMvc.perform(post("/api/guest-orders/link-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(proxyService).exchange(eq(Upstream.ORDERS), eq(HttpMethod.POST),
                eq("/api/guest-orders/link-requests"), isNull(), eq(body), isNull());
    }

    @Test
    void rejectsMalformedTokenSegmentWithoutForwarding() throws Exception {
        mockMvc.perform(get("/api/guest-orders/bad_token"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(proxyService);
    }

    @Test
    void protectedRoutesStillReadTheBearerHeader() throws Exception {
        upstreamAnswers(HttpStatus.OK, "[]", MediaType.APPLICATION_JSON_VALUE);
        String token = localToken();

        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer x"))
                .andExpect(status().isUnauthorized());

        verify(proxyService).exchange(eq(Upstream.ORDERS), eq(HttpMethod.GET), eq("/api/orders"),
                any(), isNull(), eq("Bearer " + token));
    }

    @Test
    void guestRoutesAreNotPublicInTheCognitoNamespace() throws Exception {
        mockMvc.perform(post("/aws/api/guest-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(proxyService);
    }

    private static String localToken() {
        SecretKey key = new SecretKeySpec(
                "pedidos360-local-secret-change-me-32-bytes-minimum".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://login.microsoftonline.com/pedidos360-local/v2.0")
                .audience(List.of("api://150f51db-4084-4979-b1a1-e6a6e7893a01"))
                .subject("guest-routes-test")
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of("Operador"))
                .build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
