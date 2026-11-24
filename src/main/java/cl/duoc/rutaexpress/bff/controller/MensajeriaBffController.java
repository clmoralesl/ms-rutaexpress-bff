package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.service.BffProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bff/mensajeria")
@PreAuthorize("hasRole('ADMIN')")
public class MensajeriaBffController {

    private static final String PATRON_NOMBRE = "^[a-z0-9][a-z0-9._-]{0,254}$";

    private final String mensajeriaUrl;
    private final String secretoInterno;
    private final BffProxyService proxyService;

    public MensajeriaBffController(
            @Value("${rutaexpress.mensajeria.url}") String mensajeriaUrl,
            @Value("${rutaexpress.secreto-interno:}") String secretoInterno,
            BffProxyService proxyService) {
        this.mensajeriaUrl = mensajeriaUrl;
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
            headers.set("X-Servicio-Interno", secretoInterno.trim());
        }
        return proxyService.reenviar(
                mensajeriaUrl,
                rutaDestino,
                request,
                authentication,
                body,
                "ms-rutaexpress-mensajeria",
                headers
        );
    }

    @GetMapping("/colas")
    public ResponseEntity<?> listarColas(HttpServletRequest request, JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna("/api/mensajeria/colas", request, authentication, null);
    }

    @PostMapping("/colas")
    public ResponseEntity<?> crearCola(
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna("/api/mensajeria/colas", request, authentication, body);
    }

    @DeleteMapping("/colas/{nombre}")
    public ResponseEntity<?> eliminarCola(
            @PathVariable String nombre,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        if (nombre == null || !nombre.matches(PATRON_NOMBRE)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"mensaje\":\"Nombre de cola invalido\"}".getBytes());
        }
        return reenviarConAutenticacionInterna("/api/mensajeria/colas/" + nombre, request, authentication, null);
    }

    @GetMapping("/exchanges")
    public ResponseEntity<?> listarExchanges(HttpServletRequest request, JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna("/api/mensajeria/exchanges", request, authentication, null);
    }

    @PostMapping("/exchanges")
    public ResponseEntity<?> crearExchange(
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna("/api/mensajeria/exchanges", request, authentication, body);
    }

    @DeleteMapping("/exchanges/{nombre}")
    public ResponseEntity<?> eliminarExchange(
            @PathVariable String nombre,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        if (nombre == null || !nombre.matches(PATRON_NOMBRE)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"mensaje\":\"Nombre de exchange invalido\"}".getBytes());
        }
        return reenviarConAutenticacionInterna("/api/mensajeria/exchanges/" + nombre, request, authentication, null);
    }

    @GetMapping("/bindings")
    public ResponseEntity<?> listarBindings(HttpServletRequest request, JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna("/api/mensajeria/bindings", request, authentication, null);
    }

    @PostMapping("/bindings")
    public ResponseEntity<?> crearBinding(
            @RequestBody(required = false) byte[] body,
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna("/api/mensajeria/bindings", request, authentication, body);
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<?> eliminarBinding(
            HttpServletRequest request,
            JwtAuthenticationToken authentication) {
        return reenviarConAutenticacionInterna("/api/mensajeria/bindings", request, authentication, null);
    }
}
