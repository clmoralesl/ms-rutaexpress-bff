package cl.duoc.rutaexpress.bff.dto.publico;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServicioPublicoResponse(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        BigDecimal tarifaBase,
        BigDecimal precioPorKg
) {}
