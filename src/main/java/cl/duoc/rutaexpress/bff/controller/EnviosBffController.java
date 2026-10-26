package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.dto.ErrorRespuestaBff;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bff/envios")
public class EnviosBffController {

    private static final String RUTA_BASE_BFF = "/api/bff/envios";
    private static final int LONGITUD_MINIMA_SECRETO = 32;
    private static final Set<String> MARCADORES_INVALIDOS = Set.of(
            "xxxxxxxxxxxx",
            "changeme",
            "secret",
            "secreto",
            "12345678"
    );

    private final RestTemplate restTemplate;
    private final String enviosUrl;
    private final String secretoInterno;

    public EnviosBffController(
            RestTemplate restTemplate,
            @Value("${rutaexpress.envios.url}") String enviosUrl,
            @Value("${rutaexpress.secreto-interno:}") String secretoInterno) {
        this.restTemplate = restTemplate;
        this.enviosUrl = enviosUrl;
        this.secretoInterno = secretoInterno;
    }

    @PostConstruct
    public void validarConfiguracionSecreto() {
        if (secretoInterno == null || secretoInterno.trim().isEmpty()) {
            throw new IllegalStateException(
                    "CRÍTICO: La propiedad rutaexpress.secreto-interno no está configurada o se encuentra vacía. ms-rutaexpress-bff no puede iniciar sin el secreto compartido interno."
            );
        }
        String limpio = secretoInterno.trim();
        if (limpio.length() < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException(
                    "CRÍTICO: El secreto compartido interno debe tener una longitud mínima de " + LONGITUD_MINIMA_SECRETO + " caracteres."
            );
        }
        if (MARCADORES_INVALIDOS.contains(limpio.toLowerCase())) {
            throw new IllegalStateException(
                    "CRÍTICO: El secreto compartido interno configurado contiene un valor de ejemplo o marcador no permitido."
            );
        }
    }

    @RequestMapping(
            value = {"", "/**"},
            method = {
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT
            }
    )
    public ResponseEntity<?> enrutar(
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

        Jwt jwt = authentication.getToken();
        String oid = jwt.getClaimAsString("oid");
        if (oid == null || oid.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Token no contiene oid de usuario".getBytes());
        }

        String email = extraerEmail(jwt);
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(rol -> rol.startsWith("ROLE_") ? rol.substring(5) : rol)
                .filter(rol -> !rol.isBlank())
                .collect(Collectors.joining(","));

        URI urlDestino = construirUrlDestino(request, rutaRestante);

        // Construir cabeceras nuevas desde la identidad validada, descartando cabeceras arbitrarias del cliente
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.getTokenValue());
        headers.set("X-Usuario-Oid", oid);

        if (email != null && !email.isBlank()) {
            headers.set("X-Usuario-Email", email);
        }

        if (roles != null && !roles.isBlank()) {
            headers.set("X-Usuario-Roles", roles);
        }

        headers.set("X-Servicio-Interno", secretoInterno.trim());

        if (request.getContentType() != null) {
            headers.setContentType(
                    MediaType.parseMediaType(request.getContentType())
            );
        }

        HttpEntity<byte[]> entidad = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> respuesta =
                    restTemplate.exchange(
                            urlDestino,
                            HttpMethod.valueOf(request.getMethod()),
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

            HttpHeaders errorHeaders = new HttpHeaders();
            if (ex.getResponseHeaders() != null && ex.getResponseHeaders().getContentType() != null) {
                errorHeaders.setContentType(ex.getResponseHeaders().getContentType());
            } else {
                errorHeaders.setContentType(MediaType.APPLICATION_JSON);
            }

            return ResponseEntity
                    .status(ex.getStatusCode())
                    .headers(errorHeaders)
                    .body(ex.getResponseBodyAsByteArray());

        } catch (ResourceAccessException ex) {

            ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                    "ms-rutaexpress-envios",
                    request.getRequestURI(),
                    "Servicio de envios no disponible temporalmente. Reintente mas tarde."
            );
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error);
        }
    }

    private String extraerEmail(Jwt jwt) {
        String[] claims = {"email", "upn", "unique_name", "preferred_username"};
        for (String claim : claims) {
            String valor = jwt.getClaimAsString(claim);
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }

    private boolean rutaPermitida(String ruta) {
        return ruta.isEmpty()
                || ruta.equals("/")
                || ruta.matches("/\\d+")
                || ruta.matches("/\\d+/estado")
                || ruta.matches("/\\d+/etiqueta")
                || ruta.equals("/geografia/regiones");
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
