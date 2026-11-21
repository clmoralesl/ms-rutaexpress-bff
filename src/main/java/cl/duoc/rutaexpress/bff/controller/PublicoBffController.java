package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.service.BffProxyService;
import cl.duoc.rutaexpress.bff.service.CatalogoPublicoService;
import cl.duoc.rutaexpress.bff.service.ValidadorCotizacionPublica;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/publico")
public class PublicoBffController {

    private final CatalogoPublicoService catalogoPublicoService;
    private final ValidadorCotizacionPublica validadorCotizacionPublica;
    private final BffProxyService bffProxyService;
    private final String enviosUrl;
    private final String secretoInterno;

    public PublicoBffController(
            CatalogoPublicoService catalogoPublicoService,
            ValidadorCotizacionPublica validadorCotizacionPublica,
            BffProxyService bffProxyService,
            @Value("${rutaexpress.envios.url}") String enviosUrl,
            @Value("${rutaexpress.secreto-interno:}") String secretoInterno) {
        this.catalogoPublicoService = catalogoPublicoService;
        this.validadorCotizacionPublica = validadorCotizacionPublica;
        this.bffProxyService = bffProxyService;
        this.enviosUrl = enviosUrl;
        this.secretoInterno = secretoInterno;
    }

    @GetMapping("/cotizacion")
    public ResponseEntity<?> cotizar(
            @RequestParam(required = false) Long servicioId,
            @RequestParam(required = false) Long sucursalOrigenId,
            @RequestParam(required = false) Integer idComunaDestino,
            @RequestParam(required = false) BigDecimal pesoKg,
            @RequestParam(required = false) Integer largoCm,
            @RequestParam(required = false) Integer anchoCm,
            @RequestParam(required = false) Integer altoCm,
            HttpServletRequest request) {

        List<String> errores = validadorCotizacionPublica.validar(
                servicioId,
                sucursalOrigenId,
                idComunaDestino,
                pesoKg,
                largoCm,
                anchoCm,
                altoCm
        );

        if (!errores.isEmpty()) {
            Map<String, Object> respuestaError = new LinkedHashMap<>();
            respuestaError.put("mensaje", "Parametros de cotizacion invalidos");
            respuestaError.put("errores", errores);
            return ResponseEntity.badRequest().body(respuestaError);
        }

        return catalogoPublicoService.cotizar(
                servicioId,
                sucursalOrigenId,
                idComunaDestino,
                pesoKg,
                largoCm,
                anchoCm,
                altoCm,
                request.getRequestURI()
        );
    }

    @GetMapping("/sucursales")
    public ResponseEntity<?> listarSucursales(HttpServletRequest request) {
        return catalogoPublicoService.listarSucursales(request.getRequestURI());
    }

    @GetMapping("/servicios")
    public ResponseEntity<?> listarServicios(HttpServletRequest request) {
        return catalogoPublicoService.listarServicios(request.getRequestURI());
    }

    @GetMapping("/geografia/regiones")
    public ResponseEntity<?> listarRegiones(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (secretoInterno != null && !secretoInterno.isBlank()) {
            headers.set("X-Servicio-Interno", secretoInterno.trim());
        }

        return bffProxyService.reenviar(
                enviosUrl,
                "/api/envios/geografia/regiones",
                request,
                null,
                null,
                "ms-rutaexpress-envios",
                headers
        );
    }
}
