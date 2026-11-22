package cl.duoc.rutaexpress.bff.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class LimiteTasaPublicaFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> cubos;
    private final int capacidad;
    private final int recargaPorMinuto;

    public LimiteTasaPublicaFilter(int capacidad, int recargaPorMinuto, long maxClientes, Duration expiracion) {
        this.capacidad = capacidad;
        this.recargaPorMinuto = recargaPorMinuto;
        this.cubos = Caffeine.newBuilder().maximumSize(maxClientes).expireAfterAccess(expiracion).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Solo getRemoteAddr(): X-Forwarded-For lo resuelve Tomcat para proxies internos (D8)
        String clave = request.getRemoteAddr() != null ? request.getRemoteAddr() : "desconocida";
        ConsumptionProbe sonda = cubos.get(clave, k -> nuevoCubo()).tryConsumeAndReturnRemaining(1);
        if (sonda.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long segundos = Math.max(1, (sonda.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundos));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"codigo\":\"LIMITE_TASA_EXCEDIDO\",\"mensaje\":\"Demasiadas solicitudes. Reintente en "
                + segundos + " segundos.\"}");
    }

    private Bucket nuevoCubo() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacidad)
                        .refillIntervally(recargaPorMinuto, Duration.ofMinutes(1)).build())
                .build();
    }
}
