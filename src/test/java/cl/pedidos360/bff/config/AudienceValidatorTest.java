package cl.pedidos360.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("api://150f51db-4084-4979-b1a1-e6a6e7893a01");

    @Test
    void acceptsTokenWithRequiredAudience() {
        Jwt jwt = token(List.of("api://150f51db-4084-4979-b1a1-e6a6e7893a01"));

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void acceptsEquivalentGuidAudience() {
        Jwt jwt = token(List.of("150f51db-4084-4979-b1a1-e6a6e7893a01"));

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithoutRequiredAudience() {
        Jwt jwt = token(List.of("api://otra-api"));

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private Jwt token(List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://login.microsoftonline.com/tenant/v2.0")
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject("user-oid")
                .claim("roles", List.of("Cliente"))
                .build();
    }
}
