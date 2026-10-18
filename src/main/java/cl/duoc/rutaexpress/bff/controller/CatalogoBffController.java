package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.service.BffProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bff/catalogo")
public class CatalogoBffController {

    private final String catalogoUrl;
    private final BffProxyService proxyService;

    public CatalogoBffController(
            @Value("${rutaexpress.catalogo.url}") String catalogoUrl,
            BffProxyService proxyService) {
        this.catalogoUrl = catalogoUrl;
        this.proxyService = proxyService;
    }

    @GetMapping("/servicios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listarServicios(
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                catalogoUrl,
                "/api/catalogo/servicios",
                request,
                authentication,
                null,
                "ms-rutaexpress-catalogo"
        );
    }

    @GetMapping("/servicios/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> obtenerServicioPorId(
            @PathVariable Long id,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                catalogoUrl,
                "/api/catalogo/servicios/" + id,
                request,
                authentication,
                null,
                "ms-rutaexpress-catalogo"
        );
    }

    @PostMapping("/servicios")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> crearServicio(
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                catalogoUrl,
                "/api/catalogo/servicios",
                request,
                authentication,
                body,
                "ms-rutaexpress-catalogo"
        );
    }

    @PutMapping("/servicios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> actualizarServicio(
            @PathVariable Long id,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                catalogoUrl,
                "/api/catalogo/servicios/" + id,
                request,
                authentication,
                body,
                "ms-rutaexpress-catalogo"
        );
    }

    @PatchMapping("/servicios/{id}/descontar-capacidad")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'DESPACHADOR')")
    public ResponseEntity<?> descontarCapacidad(
            @PathVariable Long id,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                catalogoUrl,
                "/api/catalogo/servicios/" + id + "/descontar-capacidad",
                request,
                authentication,
                body,
                "ms-rutaexpress-catalogo"
        );
    }

    @PatchMapping("/servicios/{id}/restablecer-capacidad")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> restablecerCapacidad(
            @PathVariable Long id,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return proxyService.reenviar(
                catalogoUrl,
                "/api/catalogo/servicios/" + id + "/restablecer-capacidad",
                request,
                authentication,
                body,
                "ms-rutaexpress-catalogo"
        );
    }
}
