package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.service.BffProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bff/catalogo")
public class CatalogoBffController {

    private final String catalogoUrl;
    private final String secretoInterno;
    private final BffProxyService proxyService;

    public CatalogoBffController(
            @Value("${rutaexpress.catalogo.url}") String catalogoUrl,
            @Value("${rutaexpress.secreto-interno:}") String secretoInterno,
            BffProxyService proxyService) {
        this.catalogoUrl = catalogoUrl;
        this.secretoInterno = secretoInterno;
        this.proxyService = proxyService;
    }

    private ResponseEntity<?> reenviarConAutenticacionInterna(
            String rutaDestino,
            HttpServletRequest request,
            JwtAuthenticationToken authentication,
            byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        if (secretoInterno != null && !secretoInterno.isBlank()) {
            headers.set("X-Servicio-Interno", secretoInterno);
        }
        return proxyService.reenviar(
                catalogoUrl,
                rutaDestino,
                request,
                authentication,
                body,
                "ms-rutaexpress-catalogo",
                headers
        );
    }

    @GetMapping("/servicios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listarServicios(
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna(
                "/api/catalogo/servicios",
                request,
                authentication,
                null
        );
    }

    @GetMapping("/servicios/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> obtenerServicioPorId(
            @PathVariable Long id,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna(
                "/api/catalogo/servicios/" + id,
                request,
                authentication,
                null
        );
    }

    @PostMapping("/servicios")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> crearServicio(
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna(
                "/api/catalogo/servicios",
                request,
                authentication,
                body
        );
    }

    @PutMapping("/servicios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> actualizarServicio(
            @PathVariable Long id,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna(
                "/api/catalogo/servicios/" + id,
                request,
                authentication,
                body
        );
    }

    @PatchMapping("/servicios/{id}/descontar-capacidad")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'DESPACHADOR')")
    public ResponseEntity<?> descontarCapacidad(
            @PathVariable Long id,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna(
                "/api/catalogo/servicios/" + id + "/descontar-capacidad",
                request,
                authentication,
                body
        );
    }

    @PatchMapping("/servicios/{id}/restablecer-capacidad")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> restablecerCapacidad(
            @PathVariable Long id,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna(
                "/api/catalogo/servicios/" + id + "/restablecer-capacidad",
                request,
                authentication,
                body
        );
    }
}
