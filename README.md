# Microservicio BFF (Backend For Frontend) - RutaExpress

## Descripcion General
Microservicio desarrollado en Spring Boot 3.3.0 y Java 17 que opera como puerta de enlace segura (Resource Server OAuth2) entre la aplicacion web Angular y la red privada de microservicios. Centraliza la autenticacion, control de acceso basado en roles (RBAC con Microsoft Entra ID / Azure AD), propagacion automatica de identidad (Token Relay) y resiliencia con respuestas de fallback amigables en formato JSON ante contingencias operativas.

## Responsabilidades Principales
1. **Validacion de Tokens JWT**: Decodifica y valida criptograficamente tokens emitidos por Azure AD mediante el endpoint de descubrimiento de claves JWKS.
2. **Control de Acceso Declarativo (RBAC)**: Protege los recursos mediante `@PreAuthorize` evaluando roles de negocio (`ROLE_ADMIN`, `ROLE_OPERADOR`, `ROLE_DESPACHADOR`, `ROLE_AUDITOR`, `ROLE_CLIENTE`).
3. **Token Relay**: Reenvia de forma transparente la cabecera `Authorization: Bearer <token>` hacia los microservicios downstream en cada peticion.
4. **Manejo de Fallback Resiliente**: Captura fallos de conectividad o timeouts (`ResourceAccessException`) y genera respuestas estandarizadas HTTP 503 Service Unavailable en formato JSON (`ErrorRespuestaBff`), impidiendo que el frontend colapse.

---

## Matriz de Enrutamiento y Seguridad

### 1. Modulo de Envios (`ms-rutaexpress-envios` - Puerto 8081)
- `GET /api/bff/envios`: Listado de envios con filtros opcionales (`estado`, `fechaDesde`, `fechaHasta`).
- `GET /api/bff/envios/{id}`: Detalle de envio especifico.
- `POST /api/bff/envios`: Creacion de nuevos despachos.
- `PUT /api/bff/envios/{id}`: Actualizacion de datos de envio.
- `PUT /api/bff/envios/{id}/estado`: Transicion de estados del ciclo logistico.

### 2. Modulo de Catalogo (`ms-rutaexpress-catalogo` - Puerto 8082)
- `GET /api/bff/catalogo/servicios`: Catalogo de servicios y tarifas activas (`isAuthenticated()`).
- `GET /api/bff/catalogo/servicios/{id}`: Detalle de servicio logistico (`isAuthenticated()`).
- `POST /api/bff/catalogo/servicios`: Alta de servicios logiticos (`hasRole('ADMIN')`).
- `PUT /api/bff/catalogo/servicios/{id}`: Modificacion de tarifas y capacidad diaria (`hasRole('ADMIN')`).
- `PATCH /api/bff/catalogo/servicios/{id}/descontar-capacidad`: Reduccion atomica de cupo vehicular (`hasAnyRole('ADMIN', 'OPERADOR', 'DESPACHADOR')`).
- `PATCH /api/bff/catalogo/servicios/{id}/restablecer-capacidad`: Restablecimiento de cupo vehicular (`hasRole('ADMIN')`).

### 3. Modulo de Auditoria (`ms-rutaexpress-auditoria` - Puerto 8084)
- `GET /api/bff/auditoria/envios/{id}`: Timeline cronologico inmutable de un envio (`hasAnyRole('ADMIN', 'AUDITOR', 'OPERADOR', 'DESPACHADOR')`).
- `GET /api/bff/auditoria/eventos`: Consulta paginada y filtrada del historial global de auditoria (`hasAnyRole('ADMIN', 'AUDITOR')`).

### 4. Modulo de Reportes Analiticos (`ms-rutaexpress-reportes` - Puerto 8086)
- `GET /api/bff/reportes/kpis`: Resumen ejecutivo de KPIs, Lead Time y distribucion de estados (`hasAnyRole('ADMIN', 'AUDITOR', 'OPERADOR')`).
- `GET /api/bff/reportes/top-servicios`: Ranking de tipos de servicio mas demandados (`hasAnyRole('ADMIN', 'OPERADOR')`).
- `GET /api/bff/reportes/rendimiento-couriers`: Tasa de exito y cumplimiento por operador logistico (`hasRole('ADMIN')`).

### 5. Monitoreo y Salud (Publico)
- `GET /api/publico/salud`: Verificacion de estado operativo del BFF.
- `GET /api/publico/estado`: Estado activo del microservicio.
- `GET /actuator/health`: Healthcheck estandar de Spring Boot Actuator.

---

## Estructura de Respuesta de Fallback (HTTP 503)

```json
{
  "timestamp": "2026-10-18T18:03:13.231Z",
  "estado": 503,
  "error": "Service Unavailable",
  "mensaje": "El servicio 'ms-rutaexpress-reportes' no se encuentra disponible temporalmente. Reintente en unos instantes.",
  "servicio": "ms-rutaexpress-reportes",
  "ruta": "/api/bff/reportes/kpis"
}
```

---

## Variables de Entorno y Configuracion

| Variable | Descripcion | Valor por Defecto |
| :--- | :--- | :--- |
| `SERVER_PORT` | Puerto HTTP del servicio | `8080` |
| `JWK_SET_URI` | URL del endpoint de certificados JWKS de Azure AD | Microsoft common discovery |
| `RUTAEXPRESS_ENVIOS_URL` | URL base de ms-rutaexpress-envios | `http://localhost:8081` |
| `RUTAEXPRESS_CATALOGO_URL` | URL base de ms-rutaexpress-catalogo | `http://localhost:8082` |
| `RUTAEXPRESS_AUDITORIA_URL` | URL base de ms-rutaexpress-auditoria | `http://localhost:8084` |
| `RUTAEXPRESS_REPORTES_URL` | URL base de ms-rutaexpress-reportes | `http://localhost:8086` |
