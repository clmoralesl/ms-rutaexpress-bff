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
                // Rutas publicas: solo GET (issue #89). Cualquier otro metodo se rechaza.
                .requestMatchers(HttpMethod.GET, "/api/publico/**").permitAll()
                .requestMatchers("/api/publico/**").denyAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/api/administrador/**").hasRole("ADMIN")
                .requestMatchers("/api/despachador/**").hasAnyRole("ADMIN", "DESPACHADOR")
                .requestMatchers("/api/cliente/**").hasAnyRole("ADMIN", "CLIENTE")
                // Envios (issue #69); la aceptacion (issue #88) va antes del denyAll
                .requestMatchers(HttpMethod.POST, "/api/bff/envios/*/aceptacion").hasAnyRole("ADMIN", "DESPACHADOR")
                .requestMatchers(HttpMethod.PUT,  "/api/bff/envios/*/estado").hasAnyRole("ADMIN", "DESPACHADOR")
                .requestMatchers(HttpMethod.POST, "/api/bff/envios/*/etiqueta").hasAnyRole("ADMIN", "DESPACHADOR", "OPERADOR")
                .requestMatchers(HttpMethod.GET,  "/api/bff/envios", "/api/bff/envios/", "/api/bff/envios/*",
                                                   "/api/bff/envios/geografia/regiones").hasAnyRole("ADMIN", "DESPACHADOR", "CLIENTE", "OPERADOR")
                .requestMatchers(HttpMethod.POST, "/api/bff/envios", "/api/bff/envios/").hasAnyRole("ADMIN", "DESPACHADOR", "CLIENTE")
                .requestMatchers("/api/bff/envios/**").denyAll()
                // Administracion de sucursales y factores por zona (issue #89)
                .requestMatchers("/api/bff/catalogo/sucursales", "/api/bff/catalogo/sucursales/**",
                                 "/api/bff/catalogo/tarifas-zonas", "/api/bff/catalogo/tarifas-zonas/**").hasRole("ADMIN")
                // Administracion de RabbitMQ (issue #97)
                .requestMatchers("/api/bff/mensajeria", "/api/bff/mensajeria/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // El JwtDecoder (firma, vigencia, emisor y audiencia) lo aporta EntraJwtConfig (issue #95)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRoleConverter))
            );

        return http.build();
    }
}
