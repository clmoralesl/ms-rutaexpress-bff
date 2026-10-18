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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogoBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
class CatalogoBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BffProxyService proxyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/bff/catalogo/servicios con usuario autenticado retorna 200 OK")
    void listarServiciosAutenticado() throws Exception {
        byte[] cuerpo = "[{\"id\":1,\"nombre\":\"Express\"}]".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-catalogo")))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(get("/api/bff/catalogo/servicios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(cuerpo));
    }

    @Test
    @DisplayName("GET /api/bff/catalogo/servicios sin autenticacion retorna 401 Unauthorized")
    void listarServiciosSinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/bff/catalogo/servicios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/bff/catalogo/servicios con ROLE_ADMIN retorna 200 OK")
    void crearServicioConRolAdmin() throws Exception {
        byte[] cuerpo = "{\"id\":1,\"nombre\":\"Nuevo\"}".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-catalogo")))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(post("/api/bff/catalogo/servicios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"TEST\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/bff/catalogo/servicios con ROLE_CLIENTE retorna 403 Forbidden")
    void crearServicioConRolClienteRetornaForbidden() throws Exception {
        mockMvc.perform(post("/api/bff/catalogo/servicios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"TEST\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/bff/catalogo/servicios/1/descontar-capacidad con ROLE_OPERADOR retorna 200 OK")
    void descontarCapacidadOperador() throws Exception {
        byte[] cuerpo = "{\"id\":1,\"capacidadDisponible\":9}".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-catalogo")))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(patch("/api/bff/catalogo/servicios/1/descontar-capacidad")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/bff/catalogo/servicios/1/descontar-capacidad con ROLE_CLIENTE retorna 403 Forbidden")
    void descontarCapacidadClienteForbidden() throws Exception {
        mockMvc.perform(patch("/api/bff/catalogo/servicios/1/descontar-capacidad")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/bff/catalogo/servicios propaga HTTP 503 JSON cuando catalogo no esta disponible")
    void listarServiciosCatalogoCaidoRetorna503() throws Exception {
        ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                "ms-rutaexpress-catalogo",
                "/api/bff/catalogo/servicios",
                "El servicio no esta disponible"
        );
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-catalogo")))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));

        mockMvc.perform(get("/api/bff/catalogo/servicios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value(503))
                .andExpect(jsonPath("$.servicio").value("ms-rutaexpress-catalogo"));
    }
}
