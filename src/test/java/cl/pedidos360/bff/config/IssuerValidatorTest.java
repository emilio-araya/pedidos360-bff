package cl.pedidos360.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class IssuerValidatorTest {

    private final IssuerValidator validator = new IssuerValidator(
            "https://login.microsoftonline.com/1feca74f-8331-414a-bd8d-2d687b22a7b3/v2.0"
    );

    @Test
    void acceptsV2Issuer() {
        assertThat(validator.validate(token(
                "https://login.microsoftonline.com/1feca74f-8331-414a-bd8d-2d687b22a7b3/v2.0"
        )).hasErrors()).isFalse();
    }

    @Test
    void acceptsTenantV1Issuer() {
        assertThat(validator.validate(token(
                "https://sts.windows.net/1feca74f-8331-414a-bd8d-2d687b22a7b3/"
        )).hasErrors()).isFalse();
    }

    @Test
    void rejectsAnotherTenantIssuer() {
        assertThat(validator.validate(token(
                "https://sts.windows.net/00000000-0000-0000-0000-000000000000/"
        )).hasErrors()).isTrue();
    }

    private Jwt token(String issuer) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(java.util.List.of("api://150f51db-4084-4979-b1a1-e6a6e7893a01"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
