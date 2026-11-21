package cl.duoc.rutaexpress.bff.service;

import cl.duoc.rutaexpress.bff.dto.ErrorRespuestaBff;
import cl.duoc.rutaexpress.bff.dto.publico.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogoPublicoService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoPublicoService.class);
    private static final String NOMBRE_SERVICIO = "ms-rutaexpress-catalogo";
    private static final Duration VIGENCIA_COTIZACION = Duration.ofHours(24);
    private static final String AVISO_COTIZACION =
            "Precio referencial calculado con el peso y las medidas declarados. El precio final se confirma en la sucursal al verificar el paquete.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String catalogoUrl;
    private final String secretoInterno;

    public CatalogoPublicoService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${rutaexpress.catalogo.url}") String catalogoUrl,
            @Value("${rutaexpress.secreto-interno:}") String secretoInterno) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.catalogoUrl = catalogoUrl;
        this.secretoInterno = secretoInterno;
    }

    public ResponseEntity<?> listarServicios(String rutaPeticion) {
        URI uri = UriComponentsBuilder.fromUriString(catalogoUrl)
                .path("/api/catalogo/servicios")
                .build()
                .toUri();

        HttpHeaders headers = crearCabecerasInternas();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> respuesta = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            List<ServicioPublicoResponse> servicios = objectMapper.readValue(
                    respuesta.getBody(),
                    new TypeReference<List<ServicioPublicoResponse>>() {}
            );

            return ResponseEntity.ok(servicios);

        } catch (HttpStatusCodeException ex) {
            return manejarErrorHttp(ex, rutaPeticion);
        } catch (Exception ex) {
            return manejarErrorGeneral(ex, rutaPeticion);
        }
    }

    public ResponseEntity<?> listarSucursales(String rutaPeticion) {
        URI uri = UriComponentsBuilder.fromUriString(catalogoUrl)
                .path("/api/catalogo/sucursales")
                .queryParam("activas", "true")
                .build()
                .toUri();

        HttpHeaders headers = crearCabecerasInternas();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> respuesta = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            List<SucursalPublicaResponse> sucursales = objectMapper.readValue(
                    respuesta.getBody(),
                    new TypeReference<List<SucursalPublicaResponse>>() {}
            );

            return ResponseEntity.ok(sucursales);

        } catch (HttpStatusCodeException ex) {
            return manejarErrorHttp(ex, rutaPeticion);
        } catch (Exception ex) {
            return manejarErrorGeneral(ex, rutaPeticion);
        }
    }

    public ResponseEntity<?> cotizar(
            Long servicioId,
            Long sucursalOrigenId,
            Integer idComunaDestino,
            BigDecimal pesoKg,
            Integer largoCm,
            Integer anchoCm,
            Integer altoCm,
            String rutaPeticion) {

        URI uri = UriComponentsBuilder.fromUriString(catalogoUrl)
                .path("/api/catalogo/cotizaciones")
                .build()
                .toUri();

        Map<String, Object> cuerpoPeticion = new LinkedHashMap<>();
        cuerpoPeticion.put("servicioId", servicioId);
        cuerpoPeticion.put("sucursalOrigenId", sucursalOrigenId);
        cuerpoPeticion.put("idComunaDestino", idComunaDestino);
        cuerpoPeticion.put("pesoKg", pesoKg);
        if (largoCm != null) cuerpoPeticion.put("largoCm", largoCm);
        if (anchoCm != null) cuerpoPeticion.put("anchoCm", anchoCm);
        if (altoCm != null) cuerpoPeticion.put("altoCm", altoCm);
        cuerpoPeticion.put("validarSucursalActiva", true);

        byte[] cuerpoBytes;
        try {
            cuerpoBytes = objectMapper.writeValueAsBytes(cuerpoPeticion);
        } catch (Exception ex) {
            log.error("Error al serializar cuerpo de cotizacion: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ErrorRespuestaBff.servicioNoDisponible(NOMBRE_SERVICIO, rutaPeticion, "Error al preparar la solicitud de cotizacion."));
        }

        HttpHeaders headers = crearCabecerasInternas();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<byte[]> entity = new HttpEntity<>(cuerpoBytes, headers);

        try {
            ResponseEntity<byte[]> respuesta = restTemplate.exchange(
                    uri,
                    HttpMethod.POST,
                    entity,
                    byte[].class
            );

            CotizacionCatalogo cotizacionCatalogo = objectMapper.readValue(
                    respuesta.getBody(),
                    CotizacionCatalogo.class
            );

            Instant calculadaEn = Instant.now();
            Instant validaHasta = calculadaEn.plus(VIGENCIA_COTIZACION);

            SucursalPublicaResponse dirSucursal = null;
            if (cotizacionCatalogo.direccionSucursal() != null) {
                dirSucursal = new SucursalPublicaResponse(
                        null,
                        cotizacionCatalogo.direccionSucursal().nombre(),
                        cotizacionCatalogo.direccionSucursal().calle(),
                        cotizacionCatalogo.direccionSucursal().numero(),
                        cotizacionCatalogo.direccionSucursal().complemento(),
                        cotizacionCatalogo.direccionSucursal().idComuna(),
                        null
                );
            }

            CotizacionPublicaResponse response = new CotizacionPublicaResponse(
                    cotizacionCatalogo.servicioId(),
                    cotizacionCatalogo.servicioCodigo(),
                    cotizacionCatalogo.sucursalOrigenId(),
                    cotizacionCatalogo.zona(),
                    cotizacionCatalogo.factorZona(),
                    cotizacionCatalogo.tarifaBase(),
                    cotizacionCatalogo.precioPorKg(),
                    cotizacionCatalogo.pesoVolumetricoKg(),
                    cotizacionCatalogo.pesoFacturableKg(),
                    cotizacionCatalogo.totalClp(),
                    cotizacionCatalogo.plazoMinDias(),
                    cotizacionCatalogo.plazoMaxDias(),
                    dirSucursal,
                    AVISO_COTIZACION,
                    calculadaEn,
                    validaHasta
            );

            return ResponseEntity.ok(response);

        } catch (HttpStatusCodeException ex) {
            return manejarErrorHttp(ex, rutaPeticion);
        } catch (Exception ex) {
            return manejarErrorGeneral(ex, rutaPeticion);
        }
    }

    private HttpHeaders crearCabecerasInternas() {
        HttpHeaders headers = new HttpHeaders();
        if (secretoInterno != null && !secretoInterno.isBlank()) {
            headers.set("X-Servicio-Interno", secretoInterno.trim());
        }
        return headers;
    }

    private ResponseEntity<?> manejarErrorHttp(HttpStatusCodeException ex, String rutaPeticion) {
        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
            log.error("Autenticacion interna rechazada por {}: revisar RUTAEXPRESS_SECRETO_INTERNO", NOMBRE_SERVICIO);
            ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                    NOMBRE_SERVICIO,
                    rutaPeticion,
                    "Servicio de catalogo no disponible temporalmente. Reintente mas tarde."
            );
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error);
        }

        if (ex.getStatusCode().is4xxClientError()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(
                    ex.getResponseHeaders() != null && ex.getResponseHeaders().getContentType() != null
                            ? ex.getResponseHeaders().getContentType()
                            : MediaType.APPLICATION_JSON
            );
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .headers(headers)
                    .body(ex.getResponseBodyAsByteArray());
        }

        log.warn("Error 5xx recibido desde {}: {}", NOMBRE_SERVICIO, ex.getStatusCode());
        ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                NOMBRE_SERVICIO,
                rutaPeticion,
                "Servicio de catalogo no disponible temporalmente. Reintente mas tarde."
        );
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error);
    }

    private ResponseEntity<?> manejarErrorGeneral(Exception ex, String rutaPeticion) {
        log.error("Fallo de comunicacion o procesamiento contra {}: {}", NOMBRE_SERVICIO, ex.getMessage());
        ErrorRespuestaBff error = ErrorRespuestaBff.servicioNoDisponible(
                NOMBRE_SERVICIO,
                rutaPeticion,
                "Servicio de catalogo no disponible temporalmente. Reintente mas tarde."
        );
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error);
    }
}
