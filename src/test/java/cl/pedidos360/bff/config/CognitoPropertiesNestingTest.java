package cl.pedidos360.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * El prefijo de {@link CognitoSecurityProperties} debe coincidir con el
 * anidamiento de application.yml. Si cognito quedara como hermano de jwt, los
 * valores se enlazarian a security.cognito fuera del prefijo esperado,
 * isConfigured() devolveria false y el decodificador lanzaria para todo token
 * de /aws/api/**.
 */
class CognitoPropertiesNestingTest {

    private static final List<String> REQUIRED_KEYS = List.of(
            "security.cognito.issuer",
            "security.cognito.audience",
            "security.cognito.jwk-set-uri");

    @Test
    void declaresCognitoUnderTheSecurityPrefix() throws Exception {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("default", new ClassPathResource("application.yml"));

        assertThat(sources).isNotEmpty();
        PropertySource<?> source = sources.get(0);

        for (String key : REQUIRED_KEYS) {
            assertThat(source.containsProperty(key))
                    .as("falta la propiedad %s en application.yml; CognitoSecurityProperties lee security.cognito", key)
                    .isTrue();
        }
    }
}
