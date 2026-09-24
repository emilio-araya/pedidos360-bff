package cl.pedidos360.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtSecurityProperties(
        String mode,
        String issuer,
        String audience,
        String jwkSetUri,
        String localSecret
) {
    public boolean isLocalMode() {
        return "local".equalsIgnoreCase(mode);
    }
}
