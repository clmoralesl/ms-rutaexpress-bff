package cl.duoc.rutaexpress.bff.dto.publico;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CotizacionCatalogo(
        Long servicioId,
        String servicioCodigo,
        Long sucursalOrigenId,
        Integer idComunaOrigen,
        String zona,
        BigDecimal factorZona,
        BigDecimal tarifaBase,
        BigDecimal precioPorKg,
        BigDecimal pesoVolumetricoKg,
        BigDecimal pesoFacturableKg,
        Long totalClp,
        Integer plazoMinDias,
        Integer plazoMaxDias,
        LocalDateTime calculadoEn,
        DireccionSucursalCatalogo direccionSucursal
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DireccionSucursalCatalogo(
            String nombre,
            String calle,
            String numero,
            String complemento,
            Integer idComuna
    ) {}
}
