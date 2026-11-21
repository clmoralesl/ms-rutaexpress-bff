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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogoBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
@TestPropertySource(properties = "rutaexpress.secreto-interno=test-secreto-interno-bff-seguro-32caracteres")
class CatalogoBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BffProxyService proxyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/bff/catalogo/servicios con usuario autenticado retorna 200 OK y envia X-Servicio-Interno")
    void listarServiciosAutenticado() throws Exception {
        byte[] cuerpo = "[{\"id\":1,\"nombre\":\"Express\"}]".getBytes();
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-catalogo"), any()))
                .thenReturn(ResponseEntity.ok(cuerpo));

        mockMvc.perform(get("/api/bff/catalogo/servicios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(cuerpo));

        verify(proxyService).reenviar(
                any(),
                eq("/api/catalogo/servicios"),
                any(),
                any(),
                isNull(),
                eq("ms-rutaexpress-catalogo"),
                argThat(headers -> headers != null && "test-secreto-interno-bff-seguro-32caracteres".equals(headers.getFirst("X-Servicio-Interno")))
        );
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
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-catalogo"), any()))
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
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-catalogo"), any()))
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
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-catalogo"), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));

        mockMvc.perform(get("/api/bff/catalogo/servicios")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.estado").value(503))
                .andExpect(jsonPath("$.servicio").value("ms-rutaexpress-catalogo"));
    }

    @Test
    @DisplayName("Las siete rutas de administracion de sucursales y factores responden 403 Forbidden para roles no ADMIN (C17)")
    void administracionSoloAdmin() throws Exception {
        List<String> rolesNoAdmin = List.of("DESPACHADOR", "OPERADOR", "CLIENTE", "AUDITOR");

        for (String rol : rolesNoAdmin) {
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + rol);

            mockMvc.perform(get("/api/bff/catalogo/sucursales")
                            .with(jwt().authorities(authority)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/bff/catalogo/sucursales")
                            .with(jwt().authorities(authority))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Sucursal Central\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/bff/catalogo/sucursales/1")
                            .with(jwt().authorities(authority)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/bff/catalogo/sucursales/1")
                            .with(jwt().authorities(authority))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Sucursal Actualizada\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/bff/catalogo/tarifas-zonas")
                            .with(jwt().authorities(authority)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/bff/catalogo/tarifas-zonas/REGIONAL")
                            .with(jwt().authorities(authority)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/bff/catalogo/tarifas-zonas/REGIONAL")
                            .with(jwt().authorities(authority))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"factor\":1.5}"))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(proxyService);
    }

    @Test
    @DisplayName("ROLE_ADMIN administra sucursales y factores con X-Servicio-Interno (C18)")
    void adminAdministraSucursalesYFactores() throws Exception {
        byte[] respuestaSucursal = "{\"id\":1,\"nombre\":\"Sucursal Central\"}".getBytes();
        byte[] respuestaZona = "{\"zona\":\"REGIONAL\",\"factor\":1.5}".getBytes();

        when(proxyService.reenviar(any(), eq("/api/catalogo/sucursales"), any(), any(), any(), eq("ms-rutaexpress-catalogo"), any()))
                .thenReturn(ResponseEntity.ok(respuestaSucursal));
        when(proxyService.reenviar(any(), eq("/api/catalogo/tarifas-zonas/REGIONAL"), any(), any(), any(), eq("ms-rutaexpress-catalogo"), any()))
                .thenReturn(ResponseEntity.ok(respuestaZona));

        mockMvc.perform(post("/api/bff/catalogo/sucursales")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sucursal Central\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/bff/catalogo/tarifas-zonas/REGIONAL")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factor\":1.5}"))
                .andExpect(status().isOk());

        verify(proxyService).reenviar(
                any(),
                eq("/api/catalogo/sucursales"),
                any(),
                any(),
                any(),
                eq("ms-rutaexpress-catalogo"),
                argThat(headers -> headers != null && "test-secreto-interno-bff-seguro-32caracteres".equals(headers.getFirst("X-Servicio-Interno")))
        );

        verify(proxyService).reenviar(
                any(),
                eq("/api/catalogo/tarifas-zonas/REGIONAL"),
                any(),
                any(),
                any(),
                eq("ms-rutaexpress-catalogo"),
                argThat(headers -> headers != null && "test-secreto-interno-bff-seguro-32caracteres".equals(headers.getFirst("X-Servicio-Interno")))
        );
    }

    @Test
    @DisplayName("Parametro {zona} invalido responde 400 Bad Request sin llamar al catalogo (C19)")
    void zonaInvalidaResponde400() throws Exception {
        mockMvc.perform(put("/api/bff/catalogo/tarifas-zonas/regional")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factor\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(put("/api/bff/catalogo/tarifas-zonas/REG-1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factor\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verifyNoInteractions(proxyService);
    }
}
