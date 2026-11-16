package cl.duoc.rutaexpress.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtRoleConverter jwtRoleConverter;

    public SecurityConfig(JwtRoleConverter jwtRoleConverter) {
        this.jwtRoleConverter = jwtRoleConverter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Endpoints públicos en español
                .requestMatchers("/api/publico/**", "/actuator/health/**").permitAll()
                // Reglas de acceso por roles (RBAC) en español
                .requestMatchers("/api/administrador/**").hasRole("ADMIN")
                .requestMatchers("/api/despachador/**").hasAnyRole("ADMIN", "DESPACHADOR")
                .requestMatchers("/api/cliente/**").hasAnyRole("ADMIN", "CLIENTE")

                // Reglas RBAC para microservicio de envios (Issue #69)
                .requestMatchers(HttpMethod.PUT,  "/api/bff/envios/*/estado").hasAnyRole("ADMIN", "DESPACHADOR")
                .requestMatchers(HttpMethod.POST, "/api/bff/envios/*/etiqueta").hasAnyRole("ADMIN", "DESPACHADOR", "OPERADOR")
                .requestMatchers(HttpMethod.GET,  "/api/bff/envios", "/api/bff/envios/", "/api/bff/envios/*",
                                                   "/api/bff/envios/geografia/regiones").hasAnyRole("ADMIN", "DESPACHADOR", "CLIENTE", "OPERADOR")
                .requestMatchers(HttpMethod.POST, "/api/bff/envios", "/api/bff/envios/").hasAnyRole("ADMIN", "DESPACHADOR", "CLIENTE")
                .requestMatchers("/api/bff/envios/**").denyAll()

                // Todo lo demás requiere autenticación
                .anyRequest().authenticated()
            )
            // El JwtDecoder (firma, vigencia, emisor y audiencia) lo aporta EntraJwtConfig (issue #95)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRoleConverter))
            );

        return http.build();
    }
}
