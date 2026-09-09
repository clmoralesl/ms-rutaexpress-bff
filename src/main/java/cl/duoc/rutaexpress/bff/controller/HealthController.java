package cl.duoc.rutaexpress.bff.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/publico")
public class HealthController {

    @GetMapping("/estado")
    public ResponseEntity<Map<String, String>> estado() {
        return ResponseEntity.ok(Map.of("estado", "ACTIVO", "mensaje", "ms-rutaexpress-bff en ejecucion"));
    }
}
