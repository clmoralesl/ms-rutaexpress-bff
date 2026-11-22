package cl.duoc.rutaexpress.bff.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
@EnableConfigurationProperties(EntraJwtProperties.class)
public class EntraJwtConfig {

    private static final Logger log = LoggerFactory.getLogger(EntraJwtConfig.class);
    private static final int LARGO_MAXIMO_REGISTRO = 200;

    @Bean
    public JwtDecoder jwtDecoder(EntraJwtProperties propiedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3000);
        fabrica.setReadTimeout(5000);

        return construirDecodificador(propiedades, new RestTemplate(fabrica));
    }

    /** Ensambla el decodificador de produccion; separado del bean para poder probarlo sin red. */
    static NimbusJwtDecoder construirDecodificador(EntraJwtProperties propiedades, RestOperations rest) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withJwkSetUri(propiedades.jwkSetUri())
                .restOperations(rest)
                .build();
        decodificador.setJwtValidator(validador(propiedades));
        return decodificador;
    }

    /** Validador compartido por el bean y por las pruebas: vigencia, emisor y audiencia. */
    public static OAuth2TokenValidator<Jwt> validador(EntraJwtProperties propiedades) {
        List<String> permitidas = propiedades.audiencias();
        OAuth2TokenValidator<Jwt> compuesto = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(propiedades.emisor()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.stream().anyMatch(permitidas::contains)));

        return jwt -> {
            OAuth2TokenValidatorResult resultado = compuesto.validate(jwt);
            if (resultado.hasErrors()) {
                log.warn("JWT rechazado: iss={}, aud={}, motivos={}",
                        sanear(String.valueOf(jwt.getClaims().get(JwtClaimNames.ISS))),
                        sanear(String.valueOf(jwt.getClaims().get(JwtClaimNames.AUD))),
                        resultado.getErrors().stream().map(OAuth2Error::getDescription).toList());
            }
            return resultado;
        };
    }

    static String sanear(String valor) {
        String limpio = valor.replaceAll("[\\p{Cntrl}\\u0085\\u2028\\u2029]", "_");
        return limpio.length() > LARGO_MAXIMO_REGISTRO ? limpio.substring(0, LARGO_MAXIMO_REGISTRO) : limpio;
    }
}
