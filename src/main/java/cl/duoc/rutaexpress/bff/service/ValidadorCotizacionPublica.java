package cl.duoc.rutaexpress.bff.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class ValidadorCotizacionPublica {

    private static final BigDecimal PESO_MAXIMO = new BigDecimal("500");
    private static final int MEDIDA_MINIMA = 1;
    private static final int MEDIDA_MAXIMA = 300;

    public List<String> validar(
            Long servicioId,
            Long sucursalOrigenId,
            Integer idComunaDestino,
            BigDecimal pesoKg,
            Integer largoCm,
            Integer anchoCm,
            Integer altoCm) {

        List<String> errores = new ArrayList<>();

        if (servicioId == null || servicioId <= 0) {
            errores.add("El parametro servicioId es obligatorio y debe ser positivo.");
        }
        if (sucursalOrigenId == null || sucursalOrigenId <= 0) {
            errores.add("El parametro sucursalOrigenId es obligatorio y debe ser positivo.");
        }
        if (idComunaDestino == null || idComunaDestino <= 0) {
            errores.add("El parametro idComunaDestino es obligatorio y debe ser positivo.");
        }

        if (pesoKg == null) {
            errores.add("El parametro pesoKg es obligatorio.");
        } else if (pesoKg.compareTo(BigDecimal.ZERO) <= 0) {
            errores.add("El parametro pesoKg debe ser mayor a cero.");
        } else if (pesoKg.compareTo(PESO_MAXIMO) > 0) {
            errores.add("El parametro pesoKg no puede superar los 500 kg.");
        }

        boolean tieneLargo = largoCm != null;
        boolean tieneAncho = anchoCm != null;
        boolean tieneAlto = altoCm != null;

        if (tieneLargo || tieneAncho || tieneAlto) {
            if (!(tieneLargo && tieneAncho && tieneAlto)) {
                errores.add("Las medidas (largoCm, anchoCm, altoCm) deben proporcionarse las tres o ninguna.");
            } else {
                if (largoCm < MEDIDA_MINIMA || largoCm > MEDIDA_MAXIMA) {
                    errores.add("El parametro largoCm debe estar entre " + MEDIDA_MINIMA + " y " + MEDIDA_MAXIMA + ".");
                }
                if (anchoCm < MEDIDA_MINIMA || anchoCm > MEDIDA_MAXIMA) {
                    errores.add("El parametro anchoCm debe estar entre " + MEDIDA_MINIMA + " y " + MEDIDA_MAXIMA + ".");
                }
                if (altoCm < MEDIDA_MINIMA || altoCm > MEDIDA_MAXIMA) {
                    errores.add("El parametro altoCm debe estar entre " + MEDIDA_MINIMA + " y " + MEDIDA_MAXIMA + ".");
                }
            }
        }

        return errores;
    }
}
