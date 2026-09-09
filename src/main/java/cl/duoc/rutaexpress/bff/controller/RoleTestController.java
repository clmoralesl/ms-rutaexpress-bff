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

    @GetMapping("/administrador/dashboard")
    public ResponseEntity<Map<String, Object>> adminDashboard(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "mensaje", "Acceso concedido al Dashboard de Administración",
            "usuario", authentication.getName(),
            "roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList())
        ));
    }

    @GetMapping("/despachador/pedidos")
    public ResponseEntity<Map<String, Object>> despachadorPedidos(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "mensaje", "Acceso concedido al panel de Despachador",
            "usuario", authentication.getName(),
            "roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList())
        ));
    }

    @GetMapping("/cliente/mis-envios")
    public ResponseEntity<Map<String, Object>> clienteEnvios(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "mensaje", "Acceso concedido al panel de Cliente",
            "usuario", authentication.getName(),
            "roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList())
        ));
    }
}
