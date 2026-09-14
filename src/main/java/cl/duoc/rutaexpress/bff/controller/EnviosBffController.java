package cl.duoc.rutaexpress.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/bff/envios")
public class EnviosBffController {

    private final RestTemplate restTemplate;
    private final String enviosUrl;

    public EnviosBffController(
            RestTemplate restTemplate,
            @Value("${rutaexpress.envios.url}") String enviosUrl) {
        this.restTemplate = restTemplate;
        this.enviosUrl = enviosUrl;
    }

    @RequestMapping(value = {"", "/**"})
    public ResponseEntity<byte[]> enrutar(
            HttpServletRequest request,
            JwtAuthenticationToken authentication,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            byte[] body) {

        String rutaOriginal = request.getRequestURI();
        String rutaBaseBff = "/api/bff/envios";

        String rutaRestante =
                rutaOriginal.substring(rutaBaseBff.length());

        String urlDestino =
                enviosUrl + "/api/envios" + rutaRestante;

        if (request.getQueryString() != null) {
            urlDestino += "?" + request.getQueryString();
        }

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
                    .body(
                            "Servicio de envíos no disponible"
                                    .getBytes()
                    );
        }
    }
}
