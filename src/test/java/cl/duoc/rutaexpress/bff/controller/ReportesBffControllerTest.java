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

@WebMvcTest(ReportesBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
class ReportesBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BffProxyService proxyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/bff/reportes/kpis con ROLE_OPERADOR retorna 200 OK")
    void kpisOperadorRetornaOk() throws Exception {
        byte[] cuerpo = "{\"totalEnvios\":150,\"leadTimePromedioHoras\":4.5}".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-reportes")))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(get("/api/bff/reportes/kpis")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(cuerpo));
    }

    @Test
    @DisplayName("GET /api/bff/reportes/kpis sin autenticacion retorna 401 Unauthorized")
    void kpisSinAutenticacionRetornaUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bff/reportes/kpis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/bff/reportes/top-servicios con ROLE_OPERADOR retorna 200 OK")
    void topServiciosOperador() throws Exception {
        byte[] cuerpo = "[{\"nombreServicio\":\"Express\",\"totalEnvios\":50}]".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-reportes")))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(get("/api/bff/reportes/top-servicios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/bff/reportes/top-servicios con ROLE_CLIENTE retorna 403 Forbidden")
    void topServiciosClienteForbidden() throws Exception {
        mockMvc.perform(get("/api/bff/reportes/top-servicios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/bff/reportes/rendimiento-couriers con ROLE_ADMIN retorna 200 OK")
    void rendimientoCouriersAdmin() throws Exception {
        byte[] cuerpo = "[{\"nombreCourier\":\"Courier 1\",\"tasaExito\":98.5}]".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-reportes")))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(get("/api/bff/reportes/rendimiento-couriers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/bff/reportes/rendimiento-couriers con ROLE_OPERADOR retorna 403 Forbidden")
    void rendimientoCouriersOperadorForbidden() throws Exception {
        mockMvc.perform(get("/api/bff/reportes/rendimiento-couriers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/bff/reportes/kpis maneja fallback amigable HTTP 503 JSON ante caida downstream")
    void kpisReportesCaidoRetorna503Json() throws Exception {
        ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                "ms-rutaexpress-reportes",
                "/api/bff/reportes/kpis",
                "El servicio no responde"
        );
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-reportes")))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));

        mockMvc.perform(get("/api/bff/reportes/kpis")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.servicio").value("ms-rutaexpress-reportes"));
    }
}
