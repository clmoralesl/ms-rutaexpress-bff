package cl.duoc.rutaexpress.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "rutaexpress.seguridad.jwt")
public record EntraJwtProperties(String tenantId, List<String> audiencias) {

    private static final Pattern GUID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    public EntraJwtProperties {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException(
                    "Falta AZURE_TENANT_ID (rutaexpress.seguridad.jwt.tenant-id): el BFF no puede validar el emisor del JWT");
        }
        tenantId = tenantId.trim().toLowerCase(Locale.ROOT);
        if (!GUID.matcher(tenantId).matches()) {
            throw new IllegalStateException("AZURE_TENANT_ID debe ser el GUID del tenant de Entra");
        }
        audiencias = audiencias == null ? List.of()
                : audiencias.stream().map(String::trim).filter(a -> !a.isEmpty()).toList();
        if (audiencias.isEmpty()) {
            throw new IllegalStateException(
                    "Falta AZURE_API_AUDIENCIAS (rutaexpress.seguridad.jwt.audiencias): el BFF no puede validar la audiencia del JWT");
        }
    }

    /** Emisor de los tokens v1.0 del tenant: con barra final y en minusculas. */
    public String emisor() {
        return "https://sts.windows.net/" + tenantId + "/";
    }

    public String jwkSetUri() {
        return "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";
    }
}
