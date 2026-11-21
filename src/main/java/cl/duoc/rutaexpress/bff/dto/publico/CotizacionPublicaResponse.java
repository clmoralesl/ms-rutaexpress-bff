package cl.duoc.rutaexpress.bff.dto.publico;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CotizacionPublicaResponse(
        Long servicioId,
        String servicioCodigo,
        Long sucursalOrigenId,
        String zona,
        BigDecimal factorZona,
        BigDecimal tarifaBase,
        BigDecimal precioPorKg,
        BigDecimal pesoVolumetricoKg,
        BigDecimal pesoFacturableKg,
        Long totalClp,
        Integer plazoMinDias,
        Integer plazoMaxDias,
        SucursalPublicaResponse direccionSucursal,
        String aviso,
        Instant calculadaEn,
        Instant validaHasta
) {}
