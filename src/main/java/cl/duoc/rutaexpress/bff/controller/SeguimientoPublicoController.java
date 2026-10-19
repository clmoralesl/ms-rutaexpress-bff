package cl.duoc.rutaexpress.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/publico/seguimiento")
public class SeguimientoPublicoController {

    private final RestTemplate restTemplate;
    private final String enviosUrl;
    private final String auditoriaUrl;

    public SeguimientoPublicoController(
            RestTemplate restTemplate,
            @Value("${rutaexpress.envios.url}") String enviosUrl,
            @Value("${rutaexpress.auditoria.url}") String auditoriaUrl) {
        this.restTemplate = restTemplate;
        this.enviosUrl = enviosUrl;
        this.auditoriaUrl = auditoriaUrl;
    }

    public record EnvioPublico(
            String codigoSeguimiento,
            String estado,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaActualizacion
    ) {}

    public record EventoPublico(
            String estado,
            LocalDateTime fecha
    ) {}

    public record SeguimientoResponse(
            String codigoSeguimiento,
            String estadoActual,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaActualizacion,
            List<EventoPublico> historial
    ) {}

    @GetMapping("/{codigo}")
    public ResponseEntity<?> consultar(@PathVariable String codigo) {

        if (!codigo.matches("[A-Za-z0-9-]{4,50}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("mensaje", "Codigo de seguimiento invalido"));
        }

        try {
            EnvioPublico envio = restTemplate.getForObject(
                    enviosUrl + "/api/envios/seguimiento/{codigo}",
                    EnvioPublico.class,
                    codigo
            );

            if (envio == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("mensaje", "Respuesta invalida del servicio de envios"));
            }

            List<EventoPublico> historial;

            try {
                ResponseEntity<EventoPublico[]> respuestaAuditoria =
                        restTemplate.getForEntity(
                                auditoriaUrl + "/api/auditoria/seguimiento/{codigo}",
                                EventoPublico[].class,
                                codigo
                        );

                EventoPublico[] eventos = respuestaAuditoria.getBody();
                historial = eventos == null ? List.of() : List.of(eventos);

            } catch (RestClientException ex) {
                historial = List.of();
            }

            return ResponseEntity.ok(new SeguimientoResponse(
                    envio.codigoSeguimiento(),
                    envio.estado(),
                    envio.fechaCreacion(),
                    envio.fechaActualizacion(),
                    historial
            ));

        } catch (HttpClientErrorException.NotFound ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("mensaje", "Codigo de seguimiento no encontrado"));

        } catch (RestClientException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("mensaje", "Servicio de seguimiento no disponible"));
        }
    }
}