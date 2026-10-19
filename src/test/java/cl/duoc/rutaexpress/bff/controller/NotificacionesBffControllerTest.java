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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificacionesBffController.class)
@Import({SecurityConfig.class, JwtRoleConverter.class})
class NotificacionesBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BffProxyService proxyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/bff/notificaciones/etiquetas/{codigo}/pdf autenticado retorna 200 OK y PDF")
    void obtenerPdfAutenticadoRetornaOk() throws Exception {
        byte[] pdfMock = "%PDF-1.4 mock".getBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"etiqueta-RT-TEST-01.pdf\"");

        when(proxyService.reenviar(any(), eq("/api/notificaciones/etiquetas/RT-TEST-01/pdf"), any(), any(), any(), eq("ms-rutaexpress-notificaciones")))
                .thenReturn(ResponseEntity.ok().headers(headers).body(pdfMock));

        mockMvc.perform(get("/api/bff/notificaciones/etiquetas/RT-TEST-01/pdf")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"etiqueta-RT-TEST-01.pdf\""));
    }

    @Test
    @DisplayName("GET /api/bff/notificaciones/etiquetas/{codigo}/pdf sin autenticacion retorna 401 Unauthorized")
    void obtenerPdfSinTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/bff/notificaciones/etiquetas/RT-TEST-01/pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/bff/notificaciones/etiquetas/generar con ROLE_OPERADOR retorna 200 OK")
    void generarEtiquetaOperadorRetornaOk() throws Exception {
        byte[] cuerpo = "{\"codigoSeguimiento\":\"RT-TEST-02\"}".getBytes();
        when(proxyService.reenviar(any(), eq("/api/notificaciones/etiquetas/generar"), any(), any(), any(), eq("ms-rutaexpress-notificaciones")))
                .thenReturn(ResponseEntity.ok().body("OK".getBytes()));

        mockMvc.perform(post("/api/bff/notificaciones/etiquetas/generar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/bff/notificaciones/etiquetas/generar con ROLE_CLIENTE retorna 403 Forbidden")
    void generarEtiquetaClienteRetorna403() throws Exception {
        byte[] cuerpo = "{\"codigoSeguimiento\":\"RT-TEST-02\"}".getBytes();

        mockMvc.perform(post("/api/bff/notificaciones/etiquetas/generar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }
}
