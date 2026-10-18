package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.config.JwtRoleConverter;
import cl.duoc.rutaexpress.bff.config.SecurityConfig;
import cl.duoc.rutaexpress.bff.dto.ErrorRespuestaBff;
import cl.duoc.rutaexpress.bff.service.BffProxyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditoriaBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
class AuditoriaBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BffProxyService proxyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/bff/auditoria/envios/1 con ROLE_AUDITOR retorna 200 OK")
    void timelineEnvioAuditor() throws Exception {
        byte[] cuerpo = "[{\"id\":10,\"tipoEvento\":\"ENVIO_CREADO\"}]".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-auditoria")))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(get("/api/bff/auditoria/envios/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(cuerpo));
    }

    @Test
    @DisplayName("GET /api/bff/auditoria/envios/1 sin autenticacion retorna 401 Unauthorized")
    void timelineEnvioSinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/bff/auditoria/envios/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/bff/auditoria/eventos con ROLE_ADMIN retorna 200 OK")
    void consultarEventosAdmin() throws Exception {
        byte[] cuerpo = "{\"content\":[]}".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-auditoria")))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(get("/api/bff/auditoria/eventos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/bff/auditoria/eventos con ROLE_CLIENTE retorna 403 Forbidden")
    void consultarEventosClienteForbidden() throws Exception {
        mockMvc.perform(get("/api/bff/auditoria/eventos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/bff/auditoria/envios/1 propaga HTTP 503 JSON cuando auditoria no responde")
    void timelineAuditoriaCaidoRetorna503() throws Exception {
        ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                "ms-rutaexpress-auditoria",
                "/api/bff/auditoria/envios/1",
                "Servicio caido"
        );
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-auditoria")))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));

        mockMvc.perform(get("/api/bff/auditoria/envios/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value(503))
                .andExpect(jsonPath("$.servicio").value("ms-rutaexpress-auditoria"));
    }
}
