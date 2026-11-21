package cl.duoc.rutaexpress.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LimiteTasaConfig {

    @Bean
    public FilterRegistrationBean<LimiteTasaPublicaFilter> limiteTasaFilterRegistration(
            @Value("${rutaexpress.limite-tasa.capacidad:25}") int capacidad,
            @Value("${rutaexpress.limite-tasa.recarga-por-minuto:20}") int recargaPorMinuto,
            @Value("${rutaexpress.limite-tasa.max-clientes:10000}") long maxClientes,
            @Value("${rutaexpress.limite-tasa.expiracion-minutos:10}") long expiracionMinutos) {

        LimiteTasaPublicaFilter filtro = new LimiteTasaPublicaFilter(
                capacidad,
                recargaPorMinuto,
                maxClientes,
                Duration.ofMinutes(expiracionMinutos)
        );

        FilterRegistrationBean<LimiteTasaPublicaFilter> registration = new FilterRegistrationBean<>(filtro);
        registration.setName("limiteTasaPublica");
        registration.addUrlPatterns("/api/publico/cotizacion", "/api/publico/geografia/*");
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        return registration;
    }
}
