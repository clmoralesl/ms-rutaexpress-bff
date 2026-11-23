package cl.duoc.rutaexpress.bff.service;

import cl.duoc.rutaexpress.bff.dto.ErrorRespuestaBff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BffProxyServiceReenvioQueryTest {

    private static final String BASE = "http://localhost:8085";
    private static final String RUTA = "/api/auditoria/eventos";
    private static final String SERVICIO = "ms-rutaexpress-auditoria";

    private MockRestServiceServer server;
    private BffProxyService proxyService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        proxyService = new BffProxyService(restTemplate);
    }

    private MockHttpServletRequest peticion(String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bff/auditoria/eventos");
        request.setQueryString(query);
        return request;
    }

    private ResponseEntity<Object> reenviar(String ruta, MockHttpServletRequest request) {
        return proxyService.reenviar(BASE, ruta, request, null, null, SERVICIO);
    }

    private void esperarOk(String uriExacta) {
        server.expect(requestTo(uriExacta))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    }

    private void afirmar400DelBff(ResponseEntity<Object> respuesta) {
        server.verify();
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(respuesta.getBody()).isInstanceOf(ErrorRespuestaBff.class);
        ErrorRespuestaBff error = (ErrorRespuestaBff) respuesta.getBody();
        assertThat(error.estado()).isEqualTo(400);
        assertThat(error.servicio()).isEqualTo(SERVICIO);
        assertThat(error.ruta()).isEqualTo("/api/bff/auditoria/eventos");
        assertThat(error.mensaje()).doesNotContain("RTX");
    }

    @Test
    @DisplayName("Un '=' sin codificar se reenvia intacto y se propaga el 400 del microservicio")
    void igualSinCodificarSeReenviaYSePropagaRespuestaDelServicio() {
        String query = "codigoSeguimiento=RTX%27%20OR%20%271%27=%271";
        byte[] cuerpoServicio = "{\"error\":\"codigo invalido\"}".getBytes();
        server.expect(requestTo(BASE + RUTA + "?" + query))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cuerpoServicio));

        ResponseEntity<Object> respuesta = reenviar(RUTA, peticion(query));

        server.verify();
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isEqualTo(cuerpoServicio);
    }

    @Test
    @DisplayName("Un valor con '=' llega al microservicio sin cambios")
    void valorConIgualSeReenviaIntacto() {
        esperarOk(BASE + RUTA + "?codigoSeguimiento=a=b");

        ResponseEntity<Object> respuesta = reenviar(RUTA, peticion("codigoSeguimiento=a=b"));

        server.verify();
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Un '&' codificado se conserva dentro del valor y no parte el parametro")
    void ampersandCodificadoNoParteElParametro() {
        String query = "codigoSeguimiento=RTX%26tipo%3DX&size=1";
        server.expect(requestTo(BASE + RUTA + "?" + query))
                .andExpect(queryParam("codigoSeguimiento", "RTX%26tipo%3DX"))
                .andExpect(queryParam("size", "1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        reenviar(RUTA, peticion(query));

        server.verify();
    }

    @Test
    @DisplayName("Los caracteres ya codificados no se codifican dos veces")
    void caracteresCodificadosNoSeCodificanDosVeces() {
        esperarOk(BASE + RUTA + "?codigoSeguimiento=RTX%3Cscript%3E%27");

        reenviar(RUTA, peticion("codigoSeguimiento=RTX%3Cscript%3E%27"));

        server.verify();
    }

    @Test
    @DisplayName("Sin query o con query vacia, la URI destino no termina en '?'")
    void sinQueryNoAgregaSignoDeInterrogacion() {
        esperarOk(BASE + RUTA);
        esperarOk(BASE + RUTA);

        reenviar(RUTA, peticion(null));
        reenviar(RUTA, peticion(""));

        server.verify();
    }

    @Test
    @DisplayName("Un escape mal formado responde 400 sin llamar al microservicio")
    void escapeMalFormadoResponde400SinReenviar() {
        afirmar400DelBff(reenviar(RUTA, peticion("codigoSeguimiento=RTX%zz")));
    }

    @Test
    @DisplayName("Un espacio sin codificar responde 400 sin llamar al microservicio")
    void espacioSinCodificarResponde400SinReenviar() {
        afirmar400DelBff(reenviar(RUTA, peticion("codigoSeguimiento=RTX 1")));
    }

    @Test
    @DisplayName("Una query con fragmento responde 400 en vez de recortarse en silencio")
    void fragmentoEnQueryResponde400SinReenviar() {
        afirmar400DelBff(reenviar(RUTA, peticion("codigoSeguimiento=RTX#x")));
    }

    @Test
    @DisplayName("Una variable de ruta decodificada con caracter invalido responde 400")
    void rutaConCaracterInvalidoResponde400SinReenviar() {
        afirmar400DelBff(reenviar("/api/notificaciones/etiquetas/RTX<1/estado", peticion(null)));
    }
}
