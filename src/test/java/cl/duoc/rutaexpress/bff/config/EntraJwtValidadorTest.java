package cl.duoc.rutaexpress.bff.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.List;

import static cl.duoc.rutaexpress.bff.config.TokensDePrueba.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pruebas unitarias del validador de emisor, audiencia, vigencia y firma (issue #95). Sin red. */
@ExtendWith(OutputCaptureExtension.class)
class EntraJwtValidadorTest {

    private static NimbusJwtDecoder decodificador(EntraJwtProperties propiedades) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey(publica()).build();
        d.setJwtValidator(EntraJwtConfig.validador(propiedades));
        return d;
    }

    private final NimbusJwtDecoder decodificador = decodificador(propiedades());

    private static String token(String iss, Object aud, Instant exp, Instant nbf) {
        return firmar(iss, aud, exp, nbf, List.of("ADMIN"), "usuario-prueba");
    }

    private static Instant enUnaHora() {
        return Instant.now().plusSeconds(3600);
    }

    @Test
    @DisplayName("C3: emisor v2.0 del tenant propio se rechaza")
    void emisorV2DelTenantPropioSeRechaza() {
        String t = token("https://login.microsoftonline.com/" + TENANT + "/v2.0", AUDIENCIA, enUnaHora(), null);
        assertThrows(JwtValidationException.class, () -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C4: emisor propio sin barra final se rechaza")
    void emisorSinBarraFinalSeRechaza() {
        String t = token("https://sts.windows.net/" + TENANT, AUDIENCIA, enUnaHora(), null);
        assertThrows(JwtValidationException.class, () -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C2 (unitario): emisor de otro tenant con misma clave y audiencia se rechaza")
    void emisorOtroTenantSeRechaza() {
        String t = token(emisorV1(OTRO_TENANT), AUDIENCIA, enUnaHora(), null);
        assertThrows(JwtValidationException.class, () -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C6: token sin claim aud se rechaza")
    void sinAudienciaSeRechaza() {
        String t = token(emisorV1(TENANT), null, enUnaHora(), null);
        assertThrows(JwtValidationException.class, () -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C7: aud como lista que incluye la propia se acepta")
    void audienciaListaQueIncluyeLaPropiaSeAcepta() {
        String t = token(emisorV1(TENANT), List.of(AUDIENCIA_GRAPH, AUDIENCIA), enUnaHora(), null);
        assertDoesNotThrow(() -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C9: vencido hace 30 s, dentro de la tolerancia de 60 s, se acepta")
    void vencidoDentroDeToleranciaSeAcepta() {
        String t = token(emisorV1(TENANT), AUDIENCIA, Instant.now().minusSeconds(30), null);
        assertDoesNotThrow(() -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C10: nbf 5 minutos en el futuro se rechaza")
    void nbfFuturoSeRechaza() {
        String t = token(emisorV1(TENANT), AUDIENCIA, enUnaHora(), Instant.now().plusSeconds(300));
        assertThrows(JwtValidationException.class, () -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C11: firmado con otra clave RSA se rechaza")
    void firmaDeOtraClaveSeRechaza() {
        String t = firmarConClave(OTRO_PAR, emisorV1(TENANT), AUDIENCIA, enUnaHora(), null,
                List.of("ADMIN"), "usuario-prueba");
        assertThrows(BadJwtException.class, () -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C12: HS256 con la clave publica como secreto se rechaza")
    void algoritmoHs256SeRechaza() {
        String t = firmarHs256ConClavePublica(emisorV1(TENANT), AUDIENCIA, enUnaHora());
        assertThrows(BadJwtException.class, () -> decodificador.decode(t));
    }

    @Test
    @DisplayName("C13: tenant configurado en mayusculas se normaliza y el token en minusculas se acepta")
    void tenantEnMayusculasSeNormaliza() {
        NimbusJwtDecoder d = decodificador(new EntraJwtProperties(TENANT.toUpperCase(), List.of(AUDIENCIA)));
        String t = token(emisorV1(TENANT), AUDIENCIA, enUnaHora(), null);
        assertDoesNotThrow(() -> d.decode(t));
    }

    @Test
    @DisplayName("C19: emisor con salto de linea inyectado se sanea en el registro")
    void emisorConSaltoDeLineaSeSanea(CapturedOutput salida) {
        Jwt jwt = Jwt.withTokenValue("valor-opaco")
                .header("alg", "RS256")
                .claim("iss", "https://evil/\r\nINFO falso")
                .claim("aud", List.of(AUDIENCIA))
                .subject("usuario-prueba")
                .issuedAt(Instant.now())
                .expiresAt(enUnaHora())
                .build();

        assertThat(EntraJwtConfig.validador(propiedades()).validate(jwt).hasErrors()).isTrue();

        assertThat(salida.getOut()).contains("JWT rechazado");
        assertThat(salida.getOut()).doesNotContain("\r\nINFO falso");
        assertThat(salida.getOut()).doesNotContain("\nINFO falso");
    }

    // Los separadores se construyen desde su codigo numerico para que el archivo quede solo en ASCII.
    private static final String LS = String.valueOf((char) 0x2028);
    private static final String PS = String.valueOf((char) 0x2029);
    private static final String NEL = String.valueOf((char) 0x0085);
    private static final String ESC = String.valueOf((char) 0x001B);

    @Test
    @DisplayName("C27: sanear reemplaza caracteres de control y separadores de linea Unicode")
    void sanearReemplazaControlesYSeparadoresUnicode() {
        String saneado = EntraJwtConfig.sanear("a" + NEL + "b" + LS + "c" + PS + "d" + ESC + "[31me\r\nf");
        assertThat(saneado).doesNotContain(NEL, LS, PS, ESC, "\r", "\n");
        assertThat(saneado).startsWith("a_b_c_d_");
    }
}
