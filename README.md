# Microservicio BFF (Backend For Frontend) - RutaExpress

## Descripcion General
Microservicio desarrollado en Spring Boot 3.3.0 y Java 17 que opera como puerta de enlace segura (Resource Server OAuth2) entre la aplicacion web Angular y la red privada de microservicios. Centraliza la autenticacion, control de acceso basado en roles (RBAC con Microsoft Entra ID / Azure AD), propagacion automatica de identidad (Token Relay), exposicion de rutas publicas con limite de tasa (Rate Limiting) y resiliencia con respuestas de fallback amigables en formato JSON ante contingencias operativas.

## Responsabilidades Principales
1. **Validacion de Tokens JWT**: Decodifica y valida criptograficamente tokens emitidos por Azure AD mediante el endpoint de descubrimiento de claves JWKS (issue #95).
2. **Control de Acceso Declarativo (RBAC)**: Protege los recursos mediante Spring Security y `@PreAuthorize` evaluando roles de negocio (`ROLE_ADMIN`, `ROLE_OPERADOR`, `ROLE_DESPACHADOR`, `ROLE_AUDITOR`, `ROLE_CLIENTE`).
3. **Token Relay y Propagacion de Identidad**: Reenvia la cabecera `Authorization: Bearer <token>` e inyecta cabeceras internas (`X-Usuario-Oid`, `X-Usuario-Roles`, `X-Servicio-Interno`). La cabecera `X-Usuario-Email` se extrae estrictamente del claim `upn` validado del tenant institucional, descartando cuentas invitadas (`#EXT#`) para prevenir suplantaciones de identidad (D10, cierra riesgo R1 del issue #88).
4. **Rutas Publicas y Limite de Tasa**: Expone rutas publicas de consulta (cotizacion, sucursales, servicios, geografia) restringidas exclusivamente al metodo `GET`. Aplica limite de tasa por IP mediante algoritmo token bucket (Bucket4j y cache Caffeine) en rutas sensibles.
5. **Manejo de Fallback Resiliente**: Captura fallos de conectividad o timeouts (`ResourceAccessException`) y genera respuestas estandarizadas HTTP 503 Service Unavailable en formato JSON (`ErrorRespuestaBff`), impidiendo que el frontend colapse.

---

## Matriz de Enrutamiento y Seguridad

### 1. Modulo de Envios (`ms-rutaexpress-envios` - Puerto 8081)
- `GET /api/bff/envios`: Listado de envios con filtros opcionales (`estado`, `fechaDesde`, `fechaHasta`) (`hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE', 'OPERADOR')`).
- `GET /api/bff/envios/{id}`: Detalle de envio especifico (`hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE', 'OPERADOR')`).
- `POST /api/bff/envios`: Creacion de nuevos despachos / preregistro (`hasAnyRole('ADMIN', 'DESPACHADOR', 'CLIENTE')`).
- `PUT /api/bff/envios/{id}/estado`: Transicion de estados del ciclo logistico (`hasAnyRole('ADMIN', 'DESPACHADOR')`).
- `POST /api/bff/envios/{id}/etiqueta`: Generacion de etiqueta de despacho (`hasAnyRole('ADMIN', 'DESPACHADOR', 'OPERADOR')`).
- `POST /api/bff/envios/{id}/aceptacion`: Aceptacion de envio en sucursal con pesaje y medidas verificadas (`hasAnyRole('ADMIN', 'DESPACHADOR')`, issue #88 y #89).

### 2. Modulo de Catalogo (`ms-rutaexpress-catalogo` - Puerto 8082)
- `GET /api/bff/catalogo/servicios`: Catalogo de servicios y tarifas activas (`isAuthenticated()`).
- `GET /api/bff/catalogo/servicios/{id}`: Detalle de servicio logistico (`isAuthenticated()`).
- `POST /api/bff/catalogo/servicios`: Alta de servicios logisticos (`hasRole('ADMIN')`).
- `PUT /api/bff/catalogo/servicios/{id}`: Modificacion de tarifas y capacidad diaria (`hasRole('ADMIN')`).
- `PATCH /api/bff/catalogo/servicios/{id}/descontar-capacidad`: Reduccion atomica de cupo vehicular (`hasAnyRole('ADMIN', 'OPERADOR', 'DESPACHADOR')`).
- `PATCH /api/bff/catalogo/servicios/{id}/restablecer-capacidad`: Restablecimiento de cupo vehicular (`hasRole('ADMIN')`).
- `GET /api/bff/catalogo/sucursales`: Listado de sucursales con filtro opcional (`hasRole('ADMIN')`, issue #89).
- `POST /api/bff/catalogo/sucursales`: Alta de sucursal logistica (`hasRole('ADMIN')`, issue #89).
- `GET /api/bff/catalogo/sucursales/{id}`: Detalle de sucursal (`hasRole('ADMIN')`, issue #89).
- `PUT /api/bff/catalogo/sucursales/{id}`: Modificacion de sucursal (`hasRole('ADMIN')`, issue #89).
- `GET /api/bff/catalogo/tarifas-zonas`: Listado de factores de recargo por zona (`hasRole('ADMIN')`, issue #89).
- `GET /api/bff/catalogo/tarifas-zonas/{zona}`: Factor de recargo de una zona especifica (`hasRole('ADMIN')`, issue #89).
- `PUT /api/bff/catalogo/tarifas-zonas/{zona}`: Modificacion de factor de recargo por zona (`hasRole('ADMIN')`, issue #89).

### 3. Modulo de Auditoria (`ms-rutaexpress-auditoria` - Puerto 8084)
- `GET /api/bff/auditoria/envios/{id}`: Timeline cronologico inmutable de un envio (`hasAnyRole('ADMIN', 'AUDITOR', 'OPERADOR', 'DESPACHADOR')`).
- `GET /api/bff/auditoria/eventos`: Consulta paginada y filtrada del historial global de auditoria (`hasAnyRole('ADMIN', 'AUDITOR')`).

### 4. Modulo de Reportes Analiticos (`ms-rutaexpress-reportes` - Puerto 8086)
- `GET /api/bff/reportes/kpis`: Resumen ejecutivo de KPIs, Lead Time y distribucion de estados (`hasAnyRole('ADMIN', 'AUDITOR', 'OPERADOR')`).
- `GET /api/bff/reportes/top-servicios`: Ranking de tipos de servicio mas demandados (`hasAnyRole('ADMIN', 'OPERADOR')`).
- `GET /api/bff/reportes/rendimiento-couriers`: Tasa de exito y cumplimiento por operador logistico (`hasRole('ADMIN')`).

### 5. Rutas Publicas de Consulta (Issue #89)
- `GET /api/publico/cotizacion`: Cotizacion publica referencial con validacion de parametros y limite de tasa por IP.
- `GET /api/publico/sucursales`: Listado publico de sucursales activas (sin exponer datos de auditoria interna).
- `GET /api/publico/servicios`: Listado publico de servicios logisticos activos (sin exponer capacidades operativas ni flags internos).
- `GET /api/publico/geografia/regiones`: Listado de division politica regional para selector de destinos (con limite de tasa).
- `GET /api/publico/seguimiento/{codigo}`: Consulta publica de tracking de envios.
- `GET /api/publico/salud`: Verificacion de estado operativo del BFF.
- `GET /api/publico/estado`: Estado activo del microservicio.
- `GET /actuator/health`: Healthcheck estandar de Spring Boot Actuator.

*Regla de Seguridad Publica*: En `/api/publico/**` solo se permite el metodo HTTP `GET`. Cualquier otro metodo (`POST`, `PUT`, `DELETE`, `PATCH`, etc.) es denegado inmediatamente por Spring Security (respondiendo 401 si no hay token, o 403 si el usuario esta autenticado).

---

## Limite de Tasa (Rate Limiting) y Consideraciones de Arquitectura

El filtro `LimiteTasaPublicaFilter` implementa un algoritmo de cubo de fichas (Token Bucket) mediante Bucket4j y cache Caffeine en memoria:
- **Rutas protegidas por limite**: `/api/publico/cotizacion` y `/api/publico/geografia/*`.
- **Parametros**: Capacidad de 25 solicitudes por IP, recarga de 20 fichas por minuto por intervalo (`refillIntervally`).
- **Respuesta al exceder**: HTTP 429 Too Many Requests con cabecera `Retry-After` (segundos restantes) y cuerpo JSON `{"codigo":"LIMITE_TASA_EXCEDIDO","mensaje":"Demasiadas solicitudes. Reintente en N segundos."}`.
- **Clave del limite**: Determinada por `request.getRemoteAddr()`. Con `server.forward-headers-strategy: native`, Tomcat procesa `X-Forwarded-For` solo cuando proviene de proxies internos autorizados (rangos privados).
- **Limite por replica (R3)**: El almacenamiento de cubos se mantiene en memoria local de la JVM. Este diseno es exacto para una replica del BFF; en arquitecturas multi-replica se requeriria un almacen de estado distribuido (ej. Redis / Bucket4j-Redis).
- **Riesgo R2 declarado (AWS API Gateway)**: En el despliegue AWS EC2 actual, API Gateway invoca la IP publica de la instancia. Tomcat no clasifica esa direccion como proxy interno de confianza, por lo que el BFF percibe la IP de salida del gateway como cliente remoto. En consecuencia, el cubo de tasa queda compartido entre los usuarios que atraviesen el mismo nodo de API Gateway. Mitigacion recomendada antes del lanzamiento publico: configurar politicas de `throttling` por ruta en API Gateway o establecer un enlace VPC (VPC Link) con `server.tomcat.remoteip.internal-proxies` configurado con el bloque CIDR correspondiente.

---

## Orden de Despliegue de la Cadena (Issue #83)

Para garantizar la interoperabilidad y evitar inconsistencias de esquema o contratos entre microservicios, el despliegue debe seguir este orden estricto:
1. **Base de Datos Oracle**: Ejecucion del script `infra/oracle/12_envios_preregistro_issue88.sql` (PR #117).
2. **Microservicio de Envios**: Despliegue de `ms-rutaexpress-envios` con soporte de preregistro y aceptacion (PR envios#14).
3. **BFF**: Despliegue de `ms-rutaexpress-bff` conteniendo la validacion estricta de JWT de Entra ID (#95) y las rutas de cotizacion publica, aceptacion y administracion de sucursales (#89).
4. **Frontend**: Despliegue de `rutaexpress-frontend` (sub-issue D, #90).
5. **API Gateway**: Configuracion de limites de tasa (`throttling`) en la etapa publica de API Gateway antes de habilitar el acceso masivo (mitigacion R2).

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
| `AZURE_TENANT_ID` | GUID del tenant de Microsoft Entra ID. Obligatoria: el BFF no arranca sin ella. Define el emisor (`https://sts.windows.net/<tenant>/`) y la URL de claves del tenant | Sin valor |
| `AZURE_API_AUDIENCIAS` | Audiencias (claim `aud`) aceptadas, separadas por comas. Obligatoria: el BFF no arranca sin ella. Debe coincidir con `api_audiencias_jwt` del API Gateway | Sin valor |
| `RUTAEXPRESS_SECRETO_INTERNO` | Secreto compartido para autenticar llamadas entre microservicios internos (`X-Servicio-Interno`). Minimo 32 caracteres | Sin valor |
| `RUTAEXPRESS_LIMITE_TASA_CAPACIDAD` | Capacidad del cubo de fichas por IP para rutas publicas | `25` |
| `RUTAEXPRESS_LIMITE_TASA_RECARGA_POR_MINUTO` | Fichas agregadas por minuto al cubo de tasa | `20` |
| `RUTAEXPRESS_LIMITE_TASA_MAX_CLIENTES` | Maximo de entradas IP en la cache Caffeine | `10000` |
| `RUTAEXPRESS_LIMITE_TASA_EXPIRACION_MINUTOS` | Minutos de inactividad tras los cuales se desaloja una IP de la cache | `10` |
| `RUTAEXPRESS_ENVIOS_URL` | URL base de ms-rutaexpress-envios | `http://localhost:8081` |
| `RUTAEXPRESS_CATALOGO_URL` | URL base de ms-rutaexpress-catalogo | `http://localhost:8082` |
| `RUTAEXPRESS_AUDITORIA_URL` | URL base de ms-rutaexpress-auditoria | `http://localhost:8084` |
| `RUTAEXPRESS_REPORTES_URL` | URL base de ms-rutaexpress-reportes | `http://localhost:8086` |

### Validacion del JWT y TLS (issue #95)

El BFF valida firma, vigencia, emisor y audiencia del token. Descarga las claves de firma desde `https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys` verificando el certificado TLS con el almacen de confianza de la JVM; no existe ninguna opcion para desactivar esa verificacion.

Si un antivirus o proxy intercepta HTTPS en el equipo local, importe su certificado raiz en un almacen de confianza propio (fuera del repositorio) y entreguelo a la JVM con:

```
JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=<ruta> -Djavax.net.ssl.trustStorePassword=<clave>
```

No se versiona ningun almacen de claves ni certificado en el repositorio.
