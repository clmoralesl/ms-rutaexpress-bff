package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.service.BffProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publico/seguimiento")
public class SeguimientoPublicoBffController {

    private static final String SERVICIO_ENVIOS = "ms-rutaexpress-envios";

    private final BffProxyService bffProxyService;
    private final String enviosUrl;

    public SeguimientoPublicoBffController(
            BffProxyService bffProxyService,
            @Value("${rutaexpress.envios.url}") String enviosUrl) {
        this.bffProxyService = bffProxyService;
        this.enviosUrl = enviosUrl;
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<Object> consultarSeguimientoPublico(
            @PathVariable String codigo,
            HttpServletRequest request) {

        String rutaDestino = "/api/envios/publico/seguimiento/" + codigo;
        return bffProxyService.reenviar(
                enviosUrl,
                rutaDestino,
                request,
                null,
                null,
                SERVICIO_ENVIOS
        );
    }
}
