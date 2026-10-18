package cl.duoc.rutaexpress.bff.service;

import cl.duoc.rutaexpress.bff.dto.ErrorRespuestaBff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BffProxyServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private BffProxyService proxyService;

    @BeforeEach
    void setUp() {
        proxyService = new BffProxyService(restTemplate);
    }

    @Test
    @DisplayName("Debe reenviar peticion exitosa con token relay hacia downstream")
    void reenviarExitosoConTokenRelay() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bff/reportes/kpis");
        request.setQueryString("rango=ultimas24h");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Jwt jwt = new Jwt(
                "token-ficticio-123",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Collections.singletonMap("alg", "none"),
                Collections.singletonMap("sub", "usuario@rutaexpress.cl")
        );
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, Collections.emptyList());

        HttpHeaders headersRespuesta = new HttpHeaders();
        headersRespuesta.setContentType(MediaType.APPLICATION_JSON);
        byte[] payloadRespuesta = "{\"kpi\":\"ok\"}".getBytes();

        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenReturn(new ResponseEntity<>(payloadRespuesta, headersRespuesta, HttpStatus.OK));

        ResponseEntity<?> respuesta = proxyService.reenviar(
                "http://localhost:8086",
                "/api/reportes/kpis",
                request,
                auth,
                null,
                "ms-rutaexpress-reportes"
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(payloadRespuesta);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.GET), captor.capture(), eq(byte[].class));
        assertThat(captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-ficticio-123");
    }

    @Test
    @DisplayName("Debe retornar HTTP 503 con formato JSON estandar ante caida del servicio downstream (ResourceAccessException)")
    void reenviarServicioCaidoRetorna503() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bff/reportes/kpis");

        when(restTemplate.exchange(
                any(URI.class),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenThrow(new ResourceAccessException("Connection refused: connect"));

        ResponseEntity<?> respuesta = proxyService.reenviar(
                "http://localhost:8086",
                "/api/reportes/kpis",
                request,
                null,
                null,
                "ms-rutaexpress-reportes"
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(respuesta.getBody()).isInstanceOf(ErrorRespuestaBff.class);

        ErrorRespuestaBff error = (ErrorRespuestaBff) respuesta.getBody();
        assertThat(error.estado()).isEqualTo(503);
        assertThat(error.servicio()).isEqualTo("ms-rutaexpress-reportes");
        assertThat(error.ruta()).isEqualTo("/api/bff/reportes/kpis");
        assertThat(error.mensaje()).contains("no se encuentra disponible temporalmente");
    }

    @Test
    @DisplayName("Debe propagar status de error y cuerpo JSON devuelto por downstream (HttpStatusCodeException)")
    void reenviarPropagaErrorDownstream() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/bff/catalogo/servicios/1/descontar-capacidad");

        byte[] cuerpoError = "{\"error\":\"Capacidad agotada\"}".getBytes();
        HttpClientErrorException downstreamError = HttpClientErrorException.create(
                HttpStatus.CONFLICT,
                "Conflict",
                HttpHeaders.EMPTY,
                cuerpoError,
                null
        );

        when(restTemplate.exchange(
                any(URI.class),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(byte[].class)
        )).thenThrow(downstreamError);

        ResponseEntity<?> respuesta = proxyService.reenviar(
                "http://localhost:8082",
                "/api/catalogo/servicios/1/descontar-capacidad",
                request,
                null,
                null,
                "ms-rutaexpress-catalogo"
        );

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isEqualTo(cuerpoError);
    }
}
