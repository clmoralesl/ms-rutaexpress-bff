package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.service.BffProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bff/auditoria")
public class AuditoriaBffController {

    private final String auditoriaUrl;
    private final BffProxyService proxyService;

    public AuditoriaBffController(
            @Value("${rutaexpress.auditoria.url}") String auditoriaUrl,
            BffProxyService proxyService) {
        this.auditoriaUrl = auditoriaUrl;
        this.proxyService = proxyService;
    }

    @GetMapping("/envios/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'OPERADOR', 'DESPACHADOR')")
    public ResponseEntity<?> obtenerTimelineEnvio(
            @PathVariable Long id,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                auditoriaUrl,
                "/api/auditoria/envios/" + id,
                request,
                authentication,
                null,
                "ms-rutaexpress-auditoria"
        );
    }

    @GetMapping("/eventos")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<?> consultarEventos(
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                auditoriaUrl,
                "/api/auditoria/eventos",
                request,
                authentication,
                null,
                "ms-rutaexpress-auditoria"
        );
    }
}
