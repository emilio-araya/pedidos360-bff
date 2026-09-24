package cl.pedidos360.bff.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {

    @Test
    void rejectsLocalDecoderOutsideLocalProfile() {
        JwtSecurityProperties properties = new JwtSecurityProperties(
                "local",
                "https://login.microsoftonline.com/tenant/v2.0",
                "api://150f51db-4084-4979-b1a1-e6a6e7893a01",
                "",
                "pedidos360-local-secret-change-me-32-bytes-minimum"
        );

        assertThatThrownBy(() -> new SecurityConfig().jwtDecoder(properties, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perfil Spring 'local'");
    }

    @Test
    void mapsEntraRolesClaimToRoleAuthorities() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://login.microsoftonline.com/tenant/v2.0")
                .audience(List.of("api://150f51db-4084-4979-b1a1-e6a6e7893a01"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of("Admin", "Operador"))
                .build();

        var authentication = new SecurityConfig()
                .jwtAuthenticationConverter()
                .convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_Admin", "ROLE_Operador");
    }
}
