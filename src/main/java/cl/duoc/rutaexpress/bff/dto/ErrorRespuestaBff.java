package cl.duoc.rutaexpress.bff.dto;

import java.time.Instant;

public record ErrorRespuestaBff(
        Instant timestamp,
        int estado,
        String error,
        String mensaje,
        String servicio,
        String ruta
) {
    public static ErrorRespuestaBff solicitudInvalida(String servicio, String ruta, String mensaje) {
        return new ErrorRespuestaBff(Instant.now(), 400, "Bad Request", mensaje, servicio, ruta);
    }

    public static ErrorRespuestaBff servicioNoDisponible(String servicio, String ruta, String mensaje) {
        return new ErrorRespuestaBff(
                Instant.now(),
                503,
                "Service Unavailable",
                mensaje != null ? mensaje : "El servicio '" + servicio + "' no se encuentra disponible temporalmente.",
                servicio,
                ruta
        );
    }
}
