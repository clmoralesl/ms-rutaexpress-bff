package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.service.BffProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff/reportes")
public class ReportesBffController {

    private final String reportesUrl;
    private final BffProxyService proxyService;

    public ReportesBffController(
            @Value("${rutaexpress.reportes.url}") String reportesUrl,
            BffProxyService proxyService) {
        this.reportesUrl = reportesUrl;
        this.proxyService = proxyService;
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'OPERADOR')")
    public ResponseEntity<?> obtenerKpisResumen(
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                reportesUrl,
                "/api/reportes/kpis",
                request,
                authentication,
                null,
                "ms-rutaexpress-reportes"
        );
    }

    @GetMapping("/top-servicios")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<?> obtenerTopServicios(
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                reportesUrl,
                "/api/reportes/top-servicios",
                request,
                authentication,
                null,
                "ms-rutaexpress-reportes"
        );
    }

    @GetMapping("/rendimiento-couriers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerRendimientoCouriers(
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                reportesUrl,
                "/api/reportes/rendimiento-couriers",
                request,
                authentication,
                null,
                "ms-rutaexpress-reportes"
        );
    }
}
