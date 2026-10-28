# ms-pedidos360-bff

[![CI](https://github.com/emilio-araya/pedidos360-bff/actions/workflows/ci.yml/badge.svg)](https://github.com/emilio-araya/pedidos360-bff/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MSAL](https://img.shields.io/badge/MSAL-5.7.1-0078D4?logo=microsoftazure&logoColor=white)](https://github.com/AzureAD/microsoft-authentication-library-for-js)

Backend for Frontend de Pedidos360. Es la única aplicación que el backend de negocio consume después de AWS API Gateway.

## Responsabilidades

- Configura dos cadenas Spring Security independientes: Entra para `/api/**` y Cognito para `/aws/api/**`.
- Valida firma, `iss`, audience/client ID, `exp`, `nbf` y `token_use=access` de Cognito.
- Convierte `roles` de Entra y `cognito:groups` en autoridades Spring `ROLE_*`.
- Aplica autorización por rol en la gestión de catálogo y estados operacionales.
- Reenvía el Bearer y las peticiones a `orders` o `catalog`.
- Propaga `X-Request-Id`: lo respeta si viene del cliente, genera un UUID si falta o es inseguro, y usa el mismo id para todas las llamadas a upstream de esa request.
- No incluye JDBC, JPA, driver de base de datos ni datasource.

## Configuración cloud

| Variable | Ejemplo/uso |
|---|---|
| `JWT_MODE` | `entra` |
| `ENTRA_ISSUER` | `https://login.microsoftonline.com/1feca74f-8331-414a-bd8d-2d687b22a7b3/v2.0` |
| `ENTRA_API_AUDIENCE` | Application (client) ID GUID de la API |
| `ENTRA_JWK_SET_URI` | Opcional; por defecto se descubre desde el issuer |
| `COGNITO_ISSUER` | Issuer exacto de Cognito |
| `COGNITO_API_AUDIENCE` | App Client ID público de Cognito |
| `COGNITO_JWK_SET_URI` | JWKS de Cognito |
| `CORS_ALLOWED_ORIGINS` | URL del frontend React separada por comas |
| `ORDERS_SERVICE_URL` | URL privada de orders |
| `CATALOG_SERVICE_URL` | URL privada de catalog |

El validador local acepta exclusivamente el issuer v2 configurado y su equivalente v1 exacto para el mismo tenant (`sts.windows.net`), además del GUID o `api://{client-id}` de la API. No se aceptan comodines.

## Perfil local

El perfil `local` usa HMAC exclusivamente para desarrollo. No se debe usar en AWS.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Token de desarrollo para probar el BFF directamente
TOKEN="$(scripts/generate-local-token.py --role Operador --oid emilio-local)"
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/catalog/products
```

Las rutas `/api/**` solo aceptan el decoder de Entra y las rutas `/aws/api/**` solo aceptan el decoder de Cognito. El prefijo del proveedor se conserva al reenviar a Orders y Catalog. El token que se propaga es el JwtAuthenticationToken ya validado, no un header arbitrario.

## Rutas públicas

Estas rutas no exigen token y existen solo en el namespace de Entra (`/api/**`); en `/aws/api/**` siguen protegidas.

| Método | Ruta | Destino | Cuerpo |
|---|---|---|---|
| `GET` | `/api/catalog/products` | catalog | No |
| `GET` | `/api/catalog/products/{id}` | catalog | No |
| `POST` | `/api/guest-orders` | orders | JSON |
| `GET` | `/api/guest-orders/{token}` | orders | No |
| `POST` | `/api/guest-orders/{token}/cancel` | orders | No |
| `POST` | `/api/guest-orders/link-requests` | orders | JSON |

Las rutas de invitado se reenvían a `orders` en la misma ruta y nunca llevan `Authorization`, aunque el cliente envíe uno: el token de seguimiento de la ruta es la única prueba y un bearer vencido haría que `orders` respondiera `401`. El BFF tampoco lee ese header en esas rutas. El estado, el ProblemDetail y `X-Request-Id` se devuelven tal como los entrega `orders`. `{token}` pasa por la misma validación de segmento que los demás identificadores. `/api/orders` sigue exigiendo token.

En producción:

```bash
mvn clean package
java -jar target/ms-pedidos360-bff-1.0.0.jar
```

## Pruebas y cobertura

```bash
mvn verify
```

Las pruebas cubren el enrutado por prefijo de proveedor, la propagación del `JwtAuthenticationToken`, los validadores de issuer y audience, el manejo de tokens de Cognito sin claim `aud` y el aislamiento de las dos cadenas de Spring Security.

| Métrica | Valor |
|---|---|
| Pruebas | 30 |
| Cobertura de líneas | 77.4% |
| Cobertura de instrucciones | 75.7% |

La CI ejecuta `mvn verify` en cada push y pull request, muestra el resumen en la página del workflow y adjunta el informe HTML de JaCoCo como artefacto. La cobertura de instrucciones es un umbral: el build falla si baja del 70%.
