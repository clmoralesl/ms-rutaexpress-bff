package cl.duoc.rutaexpress.bff.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Validacion de arranque de las propiedades obligatorias (issue #95). No usa red. */
class EntraJwtConfigTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String AUDIENCIA = "api://33333333-3333-3333-3333-333333333333";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(EntraJwtConfig.class);

    @Test
    @DisplayName("C14: sin tenant el contexto no arranca y el mensaje nombra AZURE_TENANT_ID")
    void sinTenantNoArranca() {
        runner.withPropertyValues("rutaexpress.seguridad.jwt.audiencias=" + AUDIENCIA)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("AZURE_TENANT_ID");
                });
    }

    @Test
    @DisplayName("C15: tenant que no es GUID (common) no arranca")
    void tenantNoGuidNoArranca() {
        for (String invalido : new String[]{"common", "organizations"}) {
            runner.withPropertyValues(
                            "rutaexpress.seguridad.jwt.tenant-id=" + invalido,
                            "rutaexpress.seguridad.jwt.audiencias=" + AUDIENCIA)
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("GUID");
                    });
        }
    }

    @Test
    @DisplayName("C16: audiencias vacias o solo espacios no arranca")
    void audienciasVaciasNoArranca() {
        for (String vacia : new String[]{"", " , "}) {
            runner.withPropertyValues(
                            "rutaexpress.seguridad.jwt.tenant-id=" + TENANT,
                            "rutaexpress.seguridad.jwt.audiencias=" + vacia)
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("AZURE_API_AUDIENCIAS");
                    });
        }
    }

    @Test
    @DisplayName("C17: configuracion completa arranca y las claves son las del tenant, nunca common")
    void configuracionCompletaUsaClavesDelTenant() {
        runner.withPropertyValues(
                        "rutaexpress.seguridad.jwt.tenant-id=" + TENANT.toUpperCase(),
                        "rutaexpress.seguridad.jwt.audiencias=" + AUDIENCIA)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(JwtDecoder.class);
                    EntraJwtProperties p = ctx.getBean(EntraJwtProperties.class);
                    assertThat(p.jwkSetUri()).contains(TENANT).doesNotContain("common");
                    assertThat(p.emisor()).isEqualTo("https://sts.windows.net/" + TENANT + "/");
                });
    }

    @Test
    @DisplayName("C26: el decodificador de produccion usa las claves del tenant y rechaza otro emisor")
    @SuppressWarnings("unchecked")
    void decodificadorDeProduccionAplicaValidadorYClavesDelTenant() {
        RestOperations rest = mock(RestOperations.class);
        String jwks = new JWKSet(new RSAKey.Builder(TokensDePrueba.publica()).build()).toString();
        when(rest.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jwks));

        NimbusJwtDecoder d = EntraJwtConfig.construirDecodificador(TokensDePrueba.propiedades(), rest);

        assertDoesNotThrow(() -> d.decode(TokensDePrueba.valido(List.of("ADMIN"))));
        String otroTenant = TokensDePrueba.firmar(TokensDePrueba.emisorV1(TokensDePrueba.OTRO_TENANT),
                TokensDePrueba.AUDIENCIA, Instant.now().plusSeconds(3600), null, List.of("ADMIN"), "usuario-prueba");
        assertThrows(JwtValidationException.class, () -> d.decode(otroTenant));

        ArgumentCaptor<RequestEntity<?>> pedido = ArgumentCaptor.forClass(RequestEntity.class);
        verify(rest, atLeastOnce()).exchange(pedido.capture(), eq(String.class));
        assertThat(pedido.getValue().getUrl().toString())
                .isEqualTo("https://login.microsoftonline.com/" + TokensDePrueba.TENANT + "/discovery/v2.0/keys");
    }
}
