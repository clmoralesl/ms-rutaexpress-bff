package cl.duoc.rutaexpress.bff.service;

import cl.duoc.rutaexpress.bff.dto.ErrorRespuestaBff;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class BffProxyService {

    private static final Logger log = LoggerFactory.getLogger(BffProxyService.class);

    private final RestTemplate restTemplate;

    public BffProxyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<Object> reenviar(
            String urlBaseServicio,
            String rutaDestino,
            HttpServletRequest request,
            JwtAuthenticationToken authentication,
            byte[] body,
            String nombreServicio) {

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(urlBaseServicio)
                .path(rutaDestino);

        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            builder.query(request.getQueryString());
        }

        URI uriDestino = builder.build(true).toUri();

        HttpHeaders headers = new HttpHeaders();
        if (authentication != null && authentication.getToken() != null) {
            headers.setBearerAuth(authentication.getToken().getTokenValue());
        }

        if (request.getContentType() != null) {
            try {
                headers.setContentType(MediaType.parseMediaType(request.getContentType()));
            } catch (Exception e) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
        }

        String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);
        if (acceptHeader != null && !acceptHeader.isBlank()) {
            headers.set(HttpHeaders.ACCEPT, acceptHeader);
        } else {
            headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        }

        HttpEntity<byte[]> entidad = new HttpEntity<>(body, headers);

        try {
            HttpMethod metodo = HttpMethod.valueOf(request.getMethod());
            ResponseEntity<byte[]> respuesta = restTemplate.exchange(
                    uriDestino,
                    metodo,
                    entidad,
                    byte[].class
            );

            HttpHeaders headersRespuesta = new HttpHeaders();
            if (respuesta.getHeaders().getContentType() != null) {
                headersRespuesta.setContentType(respuesta.getHeaders().getContentType());
            } else {
                headersRespuesta.setContentType(MediaType.APPLICATION_JSON);
            }

            if (respuesta.getHeaders().getContentDisposition() != null) {
                headersRespuesta.setContentDisposition(respuesta.getHeaders().getContentDisposition());
            }

            return ResponseEntity
                    .status(respuesta.getStatusCode())
                    .headers(headersRespuesta)
                    .body(respuesta.getBody());

        } catch (HttpStatusCodeException ex) {
            log.warn("Error HTTP devuelto por el servicio downstream {}: {}", nombreServicio, ex.getStatusCode());
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .headers(errorHeaders)
                    .body(ex.getResponseBodyAsByteArray());

        } catch (ResourceAccessException ex) {
            log.error("Fallo de conexion o timeout alcanzado contra {}: {}", nombreServicio, ex.getMessage());
            ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                    nombreServicio,
                    request.getRequestURI(),
                    "El servicio '" + nombreServicio + "' no se encuentra disponible temporalmente. Reintente en unos instantes."
            );

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error);

        } catch (Exception ex) {
            log.error("Error inesperado en enrutamiento hacia {}: {}", nombreServicio, ex.getMessage(), ex);
            ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                    nombreServicio,
                    request.getRequestURI(),
                    "Ocurrio un error inesperado al comunicarse con '" + nombreServicio + "'."
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error);
        }
    }
}
