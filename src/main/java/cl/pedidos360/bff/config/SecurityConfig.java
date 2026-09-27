package cl.pedidos360.bff.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CognitoSecurityProperties.class)
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain entraSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("entraJwtDecoder") JwtDecoder decoder,
            JwtAuthenticationConverter converter
    ) throws Exception {
        return baseSecurity(http)
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain cognitoSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("cognitoJwtDecoder") JwtDecoder decoder,
            JwtAuthenticationConverter converter
    ) throws Exception {
        return baseSecurity(http)
                .securityMatcher("/aws/api/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/aws/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return baseSecurity(http)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    private HttpSecurity baseSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    @Bean
    @Qualifier("entraJwtDecoder")
    JwtDecoder entraJwtDecoder(JwtSecurityProperties properties, Environment environment) {
        if (properties.isLocalMode() && Arrays.stream(environment.getActiveProfiles()).noneMatch("local"::equals)) {
            throw new IllegalStateException("JWT_MODE=local solo se permite con el perfil Spring 'local'");
        }
        NimbusJwtDecoder decoder = createEntraDecoder(properties);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefault());
        validators.add(new IssuerValidator(properties.issuer()));
        validators.add(new AudienceValidator(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    @Bean
    @Qualifier("cognitoJwtDecoder")
    JwtDecoder cognitoJwtDecoder(CognitoSecurityProperties properties) {
        if (!properties.isConfigured()) {
            return token -> {
                throw new JwtException("Cognito no está configurado para este ambiente");
            };
        }
        NimbusJwtDecoder decoder = properties.jwkSetUri() == null || properties.jwkSetUri().isBlank()
                ? (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuer())
                : NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new IssuerValidator(properties.issuer()),
                new CognitoAudienceValidator(properties.audience()),
                new AccessTokenValidator()
        ));
        return decoder;
    }

    private NimbusJwtDecoder createEntraDecoder(JwtSecurityProperties properties) {
        if (properties.isLocalMode()) {
            byte[] secret = properties.localSecret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalStateException("LOCAL_JWT_SECRET debe tener al menos 32 bytes");
            }
            SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
            return NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                    .build();
        }
        if (properties.jwkSetUri() != null && !properties.jwkSetUri().isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        }
        return (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuer());
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter entraConverter = new JwtGrantedAuthoritiesConverter();
        entraConverter.setAuthoritiesClaimName("roles");
        entraConverter.setAuthorityPrefix("ROLE_");
        JwtGrantedAuthoritiesConverter cognitoConverter = new JwtGrantedAuthoritiesConverter();
        cognitoConverter.setAuthoritiesClaimName("cognito:groups");
        cognitoConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            addAuthorities(authorities, entraConverter.convert(jwt));
            addAuthorities(authorities, cognitoConverter.convert(jwt));
            return authorities;
        });
        return converter;
    }

    private void addAuthorities(Collection<GrantedAuthority> target, Collection<GrantedAuthority> values) {
        if (values != null) target.addAll(values);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${security.cors.allowed-origins}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Request-Id"));
        configuration.setExposedHeaders(List.of("Location", "X-Request-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/aws/api/**", configuration);
        return source;
    }
}
