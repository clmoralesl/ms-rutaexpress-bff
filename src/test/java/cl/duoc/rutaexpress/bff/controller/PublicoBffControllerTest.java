package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.config.JwtRoleConverter;
import cl.duoc.rutaexpress.bff.config.LimiteTasaConfig;
import cl.duoc.rutaexpress.bff.config.SecurityConfig;
import cl.duoc.rutaexpress.bff.service.BffProxyService;
import cl.duoc.rutaexpress.bff.service.CatalogoPublicoService;
import cl.duoc.rutaexpress.bff.service.ValidadorCotizacionPublica;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicoBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class, LimiteTasaConfig.class,
         CatalogoPublicoService.class, ValidadorCotizacionPublica.class, BffProxyService.class})
@TestPropertySource(properties = {
        "rutaexpress.catalogo.url=http://localhost:8082",
        "rutaexpress.envios.url=http://localhost:8081",
        "rutaexpress.secreto-interno=test-secreto-interno-bff-seguro-32caracteres"})
class PublicoBffControllerTest {

    private static final String COTIZACION =
            "/api/publico/cotizacion?servicioId=1&sucursalOrigenId=2&idComunaDestino=13101&pesoKg=3.5";
    private static final String COTIZACION_JSON = """
            {"servicioId":1,"servicioCodigo":"EXP","sucursalOrigenId":2,"idComunaOrigen":13123,"zona":"REGIONAL",
             "factorZona":1.20,"tarifaBase":3000,"precioPorKg":500,"pesoVolumetricoKg":0,"pesoFacturableKg":3.5,
             "totalClp":5700,"plazoMinDias":1,"plazoMaxDias":2,
             "direccionSucursal":{"nombre":"Centro","calle":"Alameda","numero":"100","complemento":null,"idComuna":13123}}""";

    @Autowired private MockMvc mockMvc;
    @MockBean private RestTemplate restTemplate;
    @MockBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor desdeIp(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    @BeforeEach
    void catalogoResponde() {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class)))
                .thenAnswer(inv -> {
                    String ruta = inv.getArgument(0, URI.class).getPath();
                    String json = switch (ruta) {
                        case "/api/catalogo/cotizaciones" -> COTIZACION_JSON;
                        case "/api/catalogo/servicios" -> "[{\"id\":1,\"codigo\":\"EXP\",\"nombre\":\"Express\",\"descripcion\":\"d\","
                                + "\"tipoVehiculo\":\"VAN\",\"tarifaBase\":3000,\"precioPorKg\":500,\"capacidadMaximaDiaria\":50,"
                                + "\"capacidadDisponible\":7,\"activo\":true,\"fechaCreacion\":\"2026-01-01T00:00:00\"}]";
                        case "/api/catalogo/sucursales" -> "[{\"id\":2,\"codigo\":\"SUC-1\",\"nombre\":\"Centro\",\"calle\":\"Alameda\","
                                + "\"numero\":\"100\",\"idComuna\":13123,\"horario\":\"L-V 9-18\",\"activa\":true,"
                                + "\"fechaCreacion\":\"2026-01-01T00:00:00\"}]";
                        default -> "[{\"idRegion\":13,\"nombre\":\"Metropolitana\",\"comunas\":[]}]";
                    };
                    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                            .body(json.getBytes(StandardCharsets.UTF_8));
                });
    }

    @Test
    void rutasPublicasRespondenSinToken() throws Exception {
        for (String ruta : List.of(COTIZACION, "/api/publico/sucursales", "/api/publico/servicios",
                "/api/publico/geografia/regiones")) {
            mockMvc.perform(get(ruta).with(desdeIp("198.51.100.1"))).andExpect(status().isOk());
        }
    }

    @Test
    void metodosDistintosDeGetSeRechazan() throws Exception {
        mockMvc.perform(post(COTIZACION).with(desdeIp("198.51.100.2"))).andExpect(status().isUnauthorized());
        mockMvc.perform(put(COTIZACION).with(desdeIp("198.51.100.2"))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/publico/sucursales")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(COTIZACION).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void respuestasPublicasNoExponenCamposInternos() throws Exception {
        mockMvc.perform(get("/api/publico/servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Express"))
                .andExpect(jsonPath("$[0].capacidadDisponible").doesNotExist())
                .andExpect(jsonPath("$[0].capacidadMaximaDiaria").doesNotExist())
                .andExpect(jsonPath("$[0].activo").doesNotExist())
                .andExpect(jsonPath("$[0].fechaCreacion").doesNotExist());
        mockMvc.perform(get("/api/publico/sucursales"))
                .andExpect(jsonPath("$[0].horario").value("L-V 9-18"))
                .andExpect(jsonPath("$[0].activa").doesNotExist())
                .andExpect(jsonPath("$[0].codigo").doesNotExist());
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, atLeastOnce()).exchange(uri.capture(), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class));
        assertTrue(uri.getAllValues().stream().anyMatch(u -> "activas=true".equals(u.getQuery())));
    }

    @Test
    void cotizacionSeReenviaComoPostConAvisoYValidez() throws Exception {
        mockMvc.perform(get(COTIZACION + "&largoCm=10&anchoCm=20&altoCm=30").with(desdeIp("198.51.100.3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClp").value(5700))
                .andExpect(jsonPath("$.aviso").value(containsString("referencial")))
                .andExpect(jsonPath("$.direccionSucursal.calle").value("Alameda"));
        ArgumentCaptor<HttpEntity> entidad = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.POST), entidad.capture(), eq(byte[].class));
        HttpHeaders h = entidad.getValue().getHeaders();
        assertNotNull(h.getFirst("X-Servicio-Interno"));
        assertNull(h.getFirst(HttpHeaders.AUTHORIZATION));
        String cuerpo = new String((byte[]) entidad.getValue().getBody(), StandardCharsets.UTF_8);
        assertTrue(cuerpo.contains("\"validarSucursalActiva\":true") && cuerpo.contains("\"largoCm\":10"), cuerpo);

        String json = mockMvc.perform(get(COTIZACION).with(desdeIp("198.51.100.4")))
                .andReturn().getResponse().getContentAsString();
        JsonNode nodo = new ObjectMapper().readTree(json);
        assertEquals(Duration.ofHours(24), Duration.between(
                Instant.parse(nodo.get("calculadaEn").asText()), Instant.parse(nodo.get("validaHasta").asText())));
    }

    @Test
    void rangosInvalidosResponden400SinLlamarAlCatalogo() throws Exception {
        List<String> cadenasInvalidas = List.of(
                "/api/publico/cotizacion?servicioId=1&sucursalOrigenId=2&idComunaDestino=13101&pesoKg=0",
                "/api/publico/cotizacion?servicioId=1&sucursalOrigenId=2&idComunaDestino=13101&pesoKg=-1",
                "/api/publico/cotizacion?servicioId=1&sucursalOrigenId=2&idComunaDestino=13101&pesoKg=500.01",
                "/api/publico/cotizacion?servicioId=1&sucursalOrigenId=2&idComunaDestino=13101&pesoKg=abc",
                "/api/publico/cotizacion?servicioId=1&sucursalOrigenId=2&idComunaDestino=13101&pesoKg=3.5&largoCm=10",
                "/api/publico/cotizacion?servicioId=1&sucursalOrigenId=2&idComunaDestino=13101&pesoKg=3.5&largoCm=0&anchoCm=10&altoCm=10",
                "/api/publico/cotizacion?servicioId=1&sucursalOrigenId=2&idComunaDestino=13101&pesoKg=3.5&largoCm=10&anchoCm=10&altoCm=301",
                "/api/publico/cotizacion?sucursalOrigenId=2&idComunaDestino=13101&pesoKg=3.5"
        );

        int ipOffset = 10;
        for (String url : cadenasInvalidas) {
            mockMvc.perform(get(url).with(desdeIp("198.51.100." + ipOffset++)))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(restTemplate);
    }

    @Test
    void limitesExactosSonValidos() throws Exception {
        mockMvc.perform(get(COTIZACION + "&pesoKg=500&largoCm=300&anchoCm=300&altoCm=300")
                        .with(desdeIp("198.51.100.50")))
                .andExpect(status().isOk());
    }

    @Test
    void peticion26RecibeLimiteDeTasa() throws Exception {
        String ip = "203.0.113.1";
        for (int i = 1; i <= 25; i++) {
            mockMvc.perform(get(COTIZACION).with(desdeIp(ip)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(COTIZACION).with(desdeIp(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9]\\d*")))
                .andExpect(jsonPath("$.codigo").value("LIMITE_TASA_EXCEDIDO"));

        verify(restTemplate, times(25)).exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    void limiteIgnoraXForwardedFor() throws Exception {
        String ip = "203.0.113.2";
        for (int i = 1; i <= 25; i++) {
            mockMvc.perform(get(COTIZACION)
                            .with(desdeIp(ip))
                            .header("X-Forwarded-For", "192.0.2." + i))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(COTIZACION)
                        .with(desdeIp(ip))
                        .header("X-Forwarded-For", "192.0.2.100"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_TASA_EXCEDIDO"));
    }

    @Test
    void otraIpTieneSuPropioCubo() throws Exception {
        String ip1 = "203.0.113.3";
        for (int i = 1; i <= 25; i++) {
            mockMvc.perform(get(COTIZACION).with(desdeIp(ip1)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get(COTIZACION).with(desdeIp(ip1)))
                .andExpect(status().isTooManyRequests());

        String ip2 = "203.0.113.4";
        mockMvc.perform(get(COTIZACION).with(desdeIp(ip2)))
                .andExpect(status().isOk());
    }

    @Test
    void sucursalesYServiciosNoTienenLimite() throws Exception {
        String ip = "203.0.113.5";
        for (int i = 1; i <= 30; i++) {
            mockMvc.perform(get("/api/publico/sucursales").with(desdeIp(ip)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/publico/servicios").with(desdeIp(ip)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void geografiaTambienTieneLimite() throws Exception {
        String ip = "203.0.113.6";
        for (int i = 1; i <= 25; i++) {
            mockMvc.perform(get("/api/publico/geografia/regiones").with(desdeIp(ip)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/publico/geografia/regiones").with(desdeIp(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_TASA_EXCEDIDO"));
    }

    @Test
    void catalogoCaidoResponde503() throws Exception {
        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new ResourceAccessException("caido"));

        mockMvc.perform(get(COTIZACION).with(desdeIp("198.51.100.99")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void catalogoRechazaSecretoResponde503() throws Exception {
        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "x", null, null, null));

        mockMvc.perform(get(COTIZACION).with(desdeIp("198.51.100.98")))
                .andExpect(status().isServiceUnavailable());
    }
}
