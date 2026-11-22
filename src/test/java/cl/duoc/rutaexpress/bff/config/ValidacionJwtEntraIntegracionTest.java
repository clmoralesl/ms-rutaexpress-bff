package cl.duoc.rutaexpress.bff.config;

import cl.duoc.rutaexpress.bff.controller.RoleTestController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static cl.duoc.rutaexpress.bff.config.TokensDePrueba.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integracion de la cadena de seguridad con el validador real de emisor, audiencia y vigencia (issue #95).
 * El decodificador usa la clave publica generada en la prueba, sin acceso a red.
 */
@WebMvcTest(RoleTestController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class,
        ValidacionJwtEntraIntegracionTest.DecodificadorDePrueba.class})
@ExtendWith(OutputCaptureExtension.class)
class ValidacionJwtEntraIntegracionTest {

    private static final String RUTA_ADMIN = "/api/administrador/dashboard";

    @TestConfiguration
    static class DecodificadorDePrueba {
        @Bean
        JwtDecoder jwtDecoder() {
            NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey(publica()).build();
            d.setJwtValidator(EntraJwtConfig.validador(propiedades()));
            return d;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("C1: token valido con emisor y audiencia propios y rol ADMIN retorna 200")
    void tokenValidoTenantPropioRetorna200() throws Exception {
        mockMvc.perform(get(RUTA_ADMIN).header(HttpHeaders.AUTHORIZATION, bearer(valido(List.of("ADMIN")))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("C2: emisor de otro tenant con firma y audiencia validas retorna 401")
    void emisorOtroTenantRetorna401() throws Exception {
        String t = firmar(emisorV1(OTRO_TENANT), AUDIENCIA, Instant.now().plusSeconds(3600), null,
                List.of("ADMIN"), "usuario-prueba");
        mockMvc.perform(get(RUTA_ADMIN).header(HttpHeaders.AUTHORIZATION, bearer(t)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    @DisplayName("C5: audiencia distinta (Graph) retorna 401")
    void audienciaDistintaRetorna401() throws Exception {
        String t = firmar(emisorV1(TENANT), AUDIENCIA_GRAPH, Instant.now().plusSeconds(3600), null,
                List.of("ADMIN"), "usuario-prueba");
        mockMvc.perform(get(RUTA_ADMIN).header(HttpHeaders.AUTHORIZATION, bearer(t)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    @DisplayName("C8: token vencido hace 5 minutos retorna 401")
    void tokenVencidoRetorna401() throws Exception {
        String t = firmar(emisorV1(TENANT), AUDIENCIA, Instant.now().minusSeconds(300), null,
                List.of("ADMIN"), "usuario-prueba");
        mockMvc.perform(get(RUTA_ADMIN).header(HttpHeaders.AUTHORIZATION, bearer(t)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    @DisplayName("C18: el rechazo se registra sin incluir el token")
    void rechazoNoRegistraElToken(CapturedOutput salida) throws Exception {
        String t = firmar(emisorV1(OTRO_TENANT), AUDIENCIA, Instant.now().plusSeconds(3600), null,
                List.of("ADMIN"), "usuario-prueba");
        mockMvc.perform(get(RUTA_ADMIN).header(HttpHeaders.AUTHORIZATION, bearer(t)))
                .andExpect(status().isUnauthorized());

        assertThat(salida.getOut()).contains("JWT rechazado");
        assertThat(salida.getOut()).doesNotContain(t);
    }

    @Test
    @DisplayName("C20: token valido sin rol ADMIN en ruta ADMIN retorna 403")
    void tokenValidoSinRolAdminRetorna403() throws Exception {
        mockMvc.perform(get(RUTA_ADMIN).header(HttpHeaders.AUTHORIZATION, bearer(valido(List.of("CLIENTE")))))
                .andExpect(status().isForbidden());
    }
}
