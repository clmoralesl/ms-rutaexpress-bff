package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.service.BffProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff/notificaciones")
public class NotificacionesBffController {

    private static final String SERVICIO_NOTIFICACIONES = "ms-rutaexpress-notificaciones";

    private final BffProxyService bffProxyService;
    private final String notificacionesUrl;

    public NotificacionesBffController(
            BffProxyService bffProxyService,
            @Value("${rutaexpress.notificaciones.url}") String notificacionesUrl) {
        this.bffProxyService = bffProxyService;
        this.notificacionesUrl = notificacionesUrl;
    }

    @GetMapping("/etiquetas/{codigoSeguimiento}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> obtenerEtiquetaPdf(
            @PathVariable String codigoSeguimiento,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {

        String rutaDestino = "/api/notificaciones/etiquetas/" + codigoSeguimiento + "/pdf";
        return bffProxyService.reenviar(
                notificacionesUrl,
                rutaDestino,
                request,
                authentication,
                null,
                SERVICIO_NOTIFICACIONES
        );
    }

    @GetMapping("/etiquetas/{codigoSeguimiento}/estado")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> obtenerEstadoEtiqueta(
            @PathVariable String codigoSeguimiento,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {

        String rutaDestino = "/api/notificaciones/etiquetas/" + codigoSeguimiento + "/estado";
        return bffProxyService.reenviar(
                notificacionesUrl,
                rutaDestino,
                request,
                authentication,
                null,
                SERVICIO_NOTIFICACIONES
        );
    }

    @PostMapping("/etiquetas/generar")
    @PreAuthorize("hasAnyAuthority('ROLE_OPERADOR', 'ROLE_ADMIN')")
    public ResponseEntity<Object> generarEtiquetaManual(
            HttpServletRequest request,
            JwtAuthenticationToken authentication,
            @RequestBody byte[] body) {

        String rutaDestino = "/api/notificaciones/etiquetas/generar";
        return bffProxyService.reenviar(
                notificacionesUrl,
                rutaDestino,
                request,
                authentication,
                body,
                SERVICIO_NOTIFICACIONES
        );
    }
}
