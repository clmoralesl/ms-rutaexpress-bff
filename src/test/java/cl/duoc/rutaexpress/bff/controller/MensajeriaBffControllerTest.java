package cl.duoc.rutaexpress.bff.controller;

import cl.duoc.rutaexpress.bff.config.JwtRoleConverter;
import cl.duoc.rutaexpress.bff.config.SecurityConfig;
import cl.duoc.rutaexpress.bff.service.BffProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MensajeriaBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
class MensajeriaBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BffProxyService proxyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void adminListaColas() throws Exception {
        when(proxyService.reenviar(any(), any(), any(), any(), any(), eq("ms-rutaexpress-mensajeria"), any()))
                .thenReturn(ResponseEntity.ok("[]".getBytes()));
        mockMvc.perform(get("/api/bff/mensajeria/colas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void clienteRecibe403() throws Exception {
        mockMvc.perform(get("/api/bff/mensajeria/colas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(proxyService);
    }

    @Test
    void despachadorNoElimina() throws Exception {
        mockMvc.perform(delete("/api/bff/mensajeria/colas/demo").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DESPACHADOR"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(proxyService);
    }

    @Test
    void sinTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/bff/mensajeria/colas")).andExpect(status().isUnauthorized());
    }

    @Test
    void rutaNoMapeadaConClienteRetorna403() throws Exception {
        mockMvc.perform(get("/api/bff/mensajeria/inexistente").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void nombreInvalidoNoSeReenvia() throws Exception {
        mockMvc.perform(delete("/api/bff/mensajeria/colas/Demo").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(proxyService);
    }
}
