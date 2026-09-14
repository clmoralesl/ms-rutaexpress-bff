package cl.duoc.rutaexpress.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/bff/envios")
public class EnviosBffController {

    private static final String RUTA_BASE_BFF = "/api/bff/envios";

    private final RestTemplate restTemplate;
    private final String enviosUrl;

    public EnviosBffController(
            RestTemplate restTemplate,
            @Value("${rutaexpress.envios.url}") String enviosUrl) {
        this.restTemplate = restTemplate;
        this.enviosUrl = enviosUrl;
    }

    @RequestMapping(
            value = {"", "/**"},
            method = {
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT
            }
    )
    public ResponseEntity<byte[]> enrutar(
            HttpServletRequest request,
            JwtAuthenticationToken authentication,
            @RequestBody(required = false) byte[] body) {

        String rutaOriginal = request.getRequestURI();
        String rutaRestante = rutaOriginal.substring(RUTA_BASE_BFF.length());

        if (!rutaPermitida(rutaRestante)) {
            return ResponseEntity
                    .badRequest()
                    .body("Ruta de envíos no válida".getBytes());
        }

        URI urlDestino = construirUrlDestino(request, rutaRestante);

        HttpHeaders headers = new HttpHeaders();

        String token = authentication.getToken().getTokenValue();
        headers.setBearerAuth(token);

        if (request.getContentType() != null) {
            headers.setContentType(
                    MediaType.parseMediaType(request.getContentType())
            );
        }

        HttpEntity<byte[]> entidad =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> respuesta =
                    restTemplate.exchange(
                            urlDestino,
                            HttpMethod.valueOf(request.getMethod()),
                            entidad,
                            byte[].class
                    );

            return ResponseEntity
                    .status(respuesta.getStatusCode())
                    .headers(respuesta.getHeaders())
                    .body(respuesta.getBody());

        } catch (HttpStatusCodeException ex) {

            return ResponseEntity
                    .status(ex.getStatusCode())
                    .body(ex.getResponseBodyAsByteArray());

        } catch (ResourceAccessException ex) {

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Servicio de envíos no disponible".getBytes());
        }
    }

    private boolean rutaPermitida(String ruta) {
        return ruta.isEmpty()
                || ruta.equals("/")
                || ruta.matches("/\\d+")
                || ruta.matches("/\\d+/estado");
    }

    private URI construirUrlDestino(
            HttpServletRequest request,
            String rutaRestante) {

        UriComponentsBuilder builder =
                UriComponentsBuilder
                        .fromUriString(enviosUrl)
                        .path("/api/envios")
                        .path(rutaRestante);

        agregarParametroSiExiste(builder, request, "estado");
        agregarParametroSiExiste(builder, request, "fechaDesde");
        agregarParametroSiExiste(builder, request, "fechaHasta");

        return builder
                .build()
                .encode()
                .toUri();
    }

    private void agregarParametroSiExiste(
            UriComponentsBuilder builder,
            HttpServletRequest request,
            String nombre) {

        String valor = request.getParameter(nombre);

        if (valor != null && !valor.isBlank()) {
            builder.queryParam(nombre, valor);
        }
    }
}