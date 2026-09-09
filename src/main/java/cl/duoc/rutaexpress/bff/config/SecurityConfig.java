package cl.duoc.rutaexpress.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtRoleConverter jwtRoleConverter;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:https://login.microsoftonline.com/common/discovery/keys}")
    private String jwkSetUri;

    public SecurityConfig(JwtRoleConverter jwtRoleConverter) {
        this.jwtRoleConverter = jwtRoleConverter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/api/public/**", "/actuator/health/**").permitAll()
                // Role-restricted endpoints (RBAC)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/despachador/**").hasAnyRole("ADMIN", "DESPACHADOR")
                .requestMatchers("/api/cliente/**").hasAnyRole("ADMIN", "CLIENTE")
                // All other endpoints require generic authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtRoleConverter)
                )
            );
            
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            }, new SecureRandom());

            RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    if (connection instanceof HttpsURLConnection https) {
                        https.setSSLSocketFactory(sslContext.getSocketFactory());
                        https.setHostnameVerifier((hostname, session) -> true);
                    }
                    super.prepareConnection(connection, httpMethod);
                }
            });

            NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                    .restOperations(restTemplate)
                    .build();

            OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
            jwtDecoder.setJwtValidator(jwt -> {
                OAuth2TokenValidatorResult result = defaultValidator.validate(jwt);
                if (result.hasErrors()) {
                    System.out.println(">>> JWT VALIDATION ERROR: " + result.getErrors());
                }
                return result;
            });

            return jwtDecoder;
        } catch (Exception e) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
    }
}
