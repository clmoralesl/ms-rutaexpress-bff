package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.config.JwtRoleConverter;
import cl.duoc.rutaexpress.bff.config.SecurityConfig;
import cl.duoc.rutaexpress.bff.service.BffProxyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeguimientoPublicoBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
class SeguimientoPublicoBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private BffProxyService bffProxyService;

    @Test
    @DisplayName("GET /api/publico/seguimiento/{codigo} es accesible publicamente sin autenticacion")
    void consultarSeguimientoPublicoPermitAll() throws Exception {
        String payloadJson = "{\"codigoSeguimiento\":\"RTX-2026-0001\",\"estadoActual\":\"EN_RUTA\"}";

        when(bffProxyService.reenviar(
                anyString(),
                eq("/api/envios/publico/seguimiento/RTX-2026-0001"),
                any(),
                isNull(),
                isNull(),
                eq("ms-rutaexpress-envios")
        )).thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(payloadJson));

        mockMvc.perform(get("/api/publico/seguimiento/RTX-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(content().json(payloadJson));
    }
}
