package cl.duoc.rutaexpress.bff.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class RoleTestController {

    @GetMapping("/admin/dashboard")
    public ResponseEntity<Map<String, Object>> adminDashboard(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "message", "Acceso concedido al Dashboard de Administración",
            "user", authentication.getName(),
            "roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList())
        ));
    }

    @GetMapping("/despachador/pedidos")
    public ResponseEntity<Map<String, Object>> despachadorPedidos(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "message", "Acceso concedido al panel de Despachador",
            "user", authentication.getName(),
            "roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList())
        ));
    }

    @GetMapping("/cliente/mis-envios")
    public ResponseEntity<Map<String, Object>> clienteEnvios(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "message", "Acceso concedido al panel de Cliente",
            "user", authentication.getName(),
            "roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList())
        ));
    }
}
