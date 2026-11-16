package cl.duoc.rutaexpress.bff.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Utilidad de pruebas: genera claves RSA propias y firma tokens sin ninguna red.
 * Los identificadores son ficticios; no se usan los del tenant real.
 */
final class TokensDePrueba {

    static final String TENANT = "11111111-1111-1111-1111-111111111111";
    static final String OTRO_TENANT = "22222222-2222-2222-2222-222222222222";
    static final String AUDIENCIA = "api://33333333-3333-3333-3333-333333333333";
    static final String AUDIENCIA_GRAPH = "00000003-0000-0000-c000-000000000000";

    static final KeyPair PAR = generarPar();
    static final KeyPair OTRO_PAR = generarPar();

    private TokensDePrueba() {
    }

    static String emisorV1(String tenant) {
        return "https://sts.windows.net/" + tenant + "/";
    }

    static RSAPublicKey publica() {
        return (RSAPublicKey) PAR.getPublic();
    }

    static EntraJwtProperties propiedades() {
        return new EntraJwtProperties(TENANT, List.of(AUDIENCIA));
    }

    static JWTClaimsSet.Builder claimsValidos() {
        Instant ahora = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(emisorV1(TENANT))
                .audience(AUDIENCIA)
                .subject("usuario-prueba")
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(3600)))
                .claim("roles", List.of("ADMIN"));
    }

    /** Firma RS256 con la clave de prueba principal. aud puede ser String, List o null (se omite). */
    static String firmar(String iss, Object aud, Instant exp, Instant nbf, List<String> roles, String sub) {
        return firmarConClave(PAR, iss, aud, exp, nbf, roles, sub);
    }

    static String firmarConClave(KeyPair par, String iss, Object aud, Instant exp, Instant nbf,
                                 List<String> roles, String sub) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims(iss, aud, exp, nbf, roles, sub));
            jwt.sign(new RSASSASigner((RSAPrivateKey) par.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token de prueba", e);
        }
    }

    /** Token HS256 usando como secreto la clave publica (ataque de confusion de algoritmo). */
    static String firmarHs256ConClavePublica(String iss, Object aud, Instant exp) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                    claims(iss, aud, exp, null, List.of("ADMIN"), "atacante"));
            jwt.sign(new MACSigner(publica().getEncoded()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token HS256 de prueba", e);
        }
    }

    /** Token valido para el tenant y la audiencia de prueba, con los roles indicados. */
    static String valido(List<String> roles) {
        return firmar(emisorV1(TENANT), AUDIENCIA, Instant.now().plusSeconds(3600), null, roles, "usuario-prueba");
    }

    private static JWTClaimsSet claims(String iss, Object aud, Instant exp, Instant nbf,
                                       List<String> roles, String sub) {
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer(iss)
                .subject(sub)
                // iat una hora antes de exp: un token vencido tambien debe tener iat anterior a exp.
                .issueTime(Date.from(exp.minusSeconds(3600)))
                .expirationTime(Date.from(exp))
                .claim("roles", roles);
        if (nbf != null) {
            b.notBeforeTime(Date.from(nbf));
        }
        if (aud instanceof String s) {
            b.audience(s);
        } else if (aud instanceof List<?> l) {
            b.audience(l.stream().map(String::valueOf).toList());
        }
        return b.build();
    }

    private static KeyPair generarPar() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el par RSA de prueba", e);
        }
    }
}
