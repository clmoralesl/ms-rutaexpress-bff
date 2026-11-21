package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.config.JwtRoleConverter;
import cl.duoc.rutaexpress.bff.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnviosBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
@TestPropertySource(properties = {
        "rutaexpress.envios.url=http://localhost:8081",
        "rutaexpress.secreto-interno=test-secreto-interno-bff-seguro-32caracteres"
})
class EnviosBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/bff/envios sin autenticacion retorna 401 Unauthorized")
    void listarEnviosSinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/bff/envios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/bff/envios con token sin claim oid retorna 401 Unauthorized")
    void peticionConTokenSinOidRetorna401() throws Exception {
        mockMvc.perform(get("/api/bff/envios")
                        .with(jwt().jwt(builder -> builder
                                .claim("sub", "usuario-sin-oid")
                                .claim("roles", List.of("CLIENTE"))
                        ).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("GET /api/bff/envios con ROLE_CLIENTE retorna 200 OK y propaga identidad y secreto")
    void listarEnviosCliente() throws Exception {
        byte[] respuestaServicio = "[{\"id\":1}]".getBytes();
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(respuestaServicio));

        mockMvc.perform(get("/api/bff/envios")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-cliente00001")
                                .claim("upn", "cliente@empresa.cl")
                        ).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());

        ArgumentCaptor<HttpEntity<byte[]>> entidadCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.GET), entidadCaptor.capture(), eq(byte[].class));

        HttpHeaders headers = entidadCaptor.getValue().getHeaders();
        assertEquals("00000000-0000-0000-0000-cliente00001", headers.getFirst("X-Usuario-Oid"));
        assertEquals("cliente@empresa.cl", headers.getFirst("X-Usuario-Email"));
        assertEquals("CLIENTE", headers.getFirst("X-Usuario-Roles"));
        assertEquals("test-secreto-interno-bff-seguro-32caracteres", headers.getFirst("X-Servicio-Interno"));
    }

    @Test
    @DisplayName("POST /api/bff/envios con ROLE_CLIENTE retorna 200 OK al crear envio")
    void crearEnvioCliente() throws Exception {
        byte[] cuerpoEnvio = "{\"pesoKg\":2.5}".getBytes();
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("{\"id\":1}".getBytes()));

        mockMvc.perform(post("/api/bff/envios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEnvio)
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-cliente00001")
                                .claim("upn", "cliente@empresa.cl")
                        ).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/bff/envios/1/estado con ROLE_CLIENTE es denegado con 403 Forbidden por Spring Security")
    void actualizarEstadoClienteEsDenegado() throws Exception {
        mockMvc.perform(put("/api/bff/envios/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"EN_BODEGA\"}")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-cliente00001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("POST /api/bff/envios/1/etiqueta con ROLE_CLIENTE es denegado con 403 Forbidden")
    void solicitarEtiquetaClienteEsDenegado() throws Exception {
        mockMvc.perform(post("/api/bff/envios/1/etiqueta")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-cliente00001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("GET /api/bff/envios con ROLE_AUDITOR es denegado con 403 Forbidden")
    void listarEnviosAuditorEsDenegado() throws Exception {
        mockMvc.perform(get("/api/bff/envios")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-auditor00001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("GET /api/bff/envios con ROLE_OPERADOR retorna 200 OK (modo consulta D5)")
    void listarEnviosOperador() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("[]".getBytes()));

        mockMvc.perform(get("/api/bff/envios")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-operador0001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/bff/envios/1/etiqueta con ROLE_OPERADOR retorna 200 OK (D5)")
    void solicitarEtiquetaOperador() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.accepted().body("{}".getBytes()));

        mockMvc.perform(post("/api/bff/envios/1/etiqueta")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-operador0001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /api/bff/envios (crear) con ROLE_OPERADOR es denegado con 403 Forbidden")
    void crearEnvioOperadorEsDenegado() throws Exception {
        mockMvc.perform(post("/api/bff/envios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pesoKg\":1.0}")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-operador0001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("PUT /api/bff/envios/1/estado con ROLE_OPERADOR es denegado con 403 Forbidden")
    void actualizarEstadoOperadorEsDenegado() throws Exception {
        mockMvc.perform(put("/api/bff/envios/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"EN_BODEGA\"}")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-operador0001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("PUT /api/bff/envios/1/estado con ROLE_ADMIN retorna 200 OK")
    void actualizarEstadoAdmin() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("{\"id\":1,\"estado\":\"EN_BODEGA\"}".getBytes()));

        mockMvc.perform(put("/api/bff/envios/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"EN_BODEGA\"}")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-admin0000001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/bff/envios/1/estado con ROLE_DESPACHADOR retorna 200 OK")
    void actualizarEstadoDespachador() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("{\"id\":1,\"estado\":\"EN_BODEGA\"}".getBytes()));

        mockMvc.perform(put("/api/bff/envios/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"EN_BODEGA\"}")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-despachador1")
                        ).authorities(new SimpleGrantedAuthority("ROLE_DESPACHADOR"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cabecera X-Usuario-Oid arbitraria enviada por el cliente es descartada y reemplazada por el token")
    void cabecerasArbitrariasDelClienteSonDescartadas() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("[]".getBytes()));

        mockMvc.perform(get("/api/bff/envios")
                        .header("X-Usuario-Oid", "hacker-impostor-oid")
                        .header("X-Usuario-Roles", "ADMIN")
                        .header("X-Servicio-Interno", "intruso")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "oid-legitimo-validado-123")
                                .claim("upn", "usuario@dominio.cl")
                        ).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());

        ArgumentCaptor<HttpEntity<byte[]>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.GET), captor.capture(), eq(byte[].class));

        HttpHeaders headers = captor.getValue().getHeaders();
        assertEquals("oid-legitimo-validado-123", headers.getFirst("X-Usuario-Oid"));
        assertEquals("usuario@dominio.cl", headers.getFirst("X-Usuario-Email"));
        assertEquals("CLIENTE", headers.getFirst("X-Usuario-Roles"));
        assertEquals("test-secreto-interno-bff-seguro-32caracteres", headers.getFirst("X-Servicio-Interno"));
    }

    @Test
    @DisplayName("DELETE /api/bff/envios/1 es rechazado con 403 Forbidden por regla denyAll")
    void metodoDeleteRechazadoPorDenyAll() throws Exception {
        mockMvc.perform(delete("/api/bff/envios/1")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-admin0000001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("PUT /api/bff/envios/1 (sin subruta /estado) es rechazado con 403 Forbidden por denyAll")
    void metodoPutSinEstadoRechazadoPorDenyAll() throws Exception {
        mockMvc.perform(put("/api/bff/envios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campo\":\"valor\"}")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-admin0000001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("GET /api/bff/envios/1 con ROLE_CLIENTE retorna 200 OK y enruta a /api/envios/1")
    void obtenerEnvioPorIdCliente() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("{\"id\":1,\"codigoSeguimiento\":\"RTX-1\"}".getBytes()));

        mockMvc.perform(get("/api/bff/envios/1")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-cliente00001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());

        verify(restTemplate).exchange(
                eq(URI.create("http://localhost:8081/api/envios/1")),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)
        );
    }

    @Test
    @DisplayName("POST /api/bff/envios/5/aceptacion permitido para ROLE_ADMIN y ROLE_DESPACHADOR")
    void aceptacionPermitidaParaAdminYDespachador() throws Exception {
        byte[] respuestaEnvio = "{\"id\":5,\"estado\":\"ACEPTADO\"}".getBytes();
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(respuestaEnvio));

        for (String rol : List.of("ADMIN", "DESPACHADOR")) {
            mockMvc.perform(post("/api/bff/envios/5/aceptacion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pesoVerificadoKg\":2.0}")
                            .with(jwt().jwt(builder -> builder
                                    .claim("oid", "00000000-0000-0000-0000-operador0001")
                                    .claim("upn", "operador@empresa.cl")
                            ).authorities(new SimpleGrantedAuthority("ROLE_" + rol))))
                    .andExpect(status().isOk());
        }

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, times(2)).exchange(uriCaptor.capture(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class));
        for (URI uri : uriCaptor.getAllValues()) {
            assertTrue(uri.getPath().endsWith("/api/envios/5/aceptacion"));
        }
    }

    @Test
    @DisplayName("POST /api/bff/envios/5/aceptacion denegado para roles distintos de ADMIN y DESPACHADOR")
    void aceptacionDenegadaParaOtrosRoles() throws Exception {
        for (String rol : List.of("CLIENTE", "OPERADOR", "AUDITOR")) {
            mockMvc.perform(post("/api/bff/envios/5/aceptacion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pesoVerificadoKg\":2.0}")
                            .with(jwt().jwt(builder -> builder
                                    .claim("oid", "00000000-0000-0000-0000-usuario00001")
                            ).authorities(new SimpleGrantedAuthority("ROLE_" + rol))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("PUT y GET /api/bff/envios/5/aceptacion denegados con 403 Forbidden para ROLE_ADMIN")
    void aceptacionConOtroMetodoDenegada() throws Exception {
        mockMvc.perform(put("/api/bff/envios/5/aceptacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pesoVerificadoKg\":2.0}")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-admin0000001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/bff/envios/5/aceptacion")
                        .with(jwt().jwt(builder -> builder
                                .claim("oid", "00000000-0000-0000-0000-admin0000001")
                        ).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Cabecera X-Usuario-Email solo se propaga desde upn valido sin #EXT# (D10, R1)")
    void correoSoloDesdeUpn() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("[]".getBytes()));

        // 1. Solo preferred_username
        mockMvc.perform(get("/api/bff/envios")
                        .with(jwt().jwt(b -> b.claim("oid", "oid-1").claim("preferred_username", "ana@empresa.cl"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());

        // 2. Solo email
        mockMvc.perform(get("/api/bff/envios")
                        .with(jwt().jwt(b -> b.claim("oid", "oid-2").claim("email", "ana@empresa.cl"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());

        // 3. upn valido
        mockMvc.perform(get("/api/bff/envios")
                        .with(jwt().jwt(b -> b.claim("oid", "oid-3").claim("upn", "ana@empresa.cl"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());

        // 4. upn con #EXT# (cuenta invitada)
        mockMvc.perform(get("/api/bff/envios")
                        .with(jwt().jwt(b -> b.claim("oid", "oid-4").claim("upn", "ana_gmail.com#EXT#@tenant.onmicrosoft.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isOk());

        ArgumentCaptor<HttpEntity<byte[]>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(4)).exchange(any(URI.class), eq(HttpMethod.GET), captor.capture(), eq(byte[].class));

        List<HttpEntity<byte[]>> llamadas = captor.getAllValues();
        assertNull(llamadas.get(0).getHeaders().getFirst("X-Usuario-Email"));
        assertNull(llamadas.get(1).getHeaders().getFirst("X-Usuario-Email"));
        assertEquals("ana@empresa.cl", llamadas.get(2).getHeaders().getFirst("X-Usuario-Email"));
        assertNull(llamadas.get(3).getHeaders().getFirst("X-Usuario-Email"));
    }
}
