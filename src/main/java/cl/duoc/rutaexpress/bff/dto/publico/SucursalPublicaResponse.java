package cl.duoc.rutaexpress.bff.dto.publico;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SucursalPublicaResponse(
        Long id,
        String nombre,
        String calle,
        String numero,
        String complemento,
        Integer idComuna,
        String horario
) {}
