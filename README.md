# ms-pedidos360-bff

Backend for Frontend de Pedidos360. Es la única aplicación que el backend de negocio consume después de AWS API Gateway.

## Responsabilidades

- Configura Spring Security como OAuth2 Resource Server.
- Valida firma, `iss`, `aud`, `exp` y `nbf` del JWT.
- Convierte el claim `roles` de Entra ID en autoridades Spring `ROLE_*`.
- Aplica autorización por rol en la gestión de catálogo y estados operacionales.
- Reenvía el Bearer y las peticiones a `orders` o `catalog`.
- No incluye JDBC, JPA, driver de base de datos ni datasource.

## Configuración cloud

| Variable | Ejemplo/uso |
|---|---|
| `JWT_MODE` | `entra` |
| `ENTRA_ISSUER` | `https://login.microsoftonline.com/1feca74f-8331-414a-bd8d-2d687b22a7b3/v2.0` |
| `ENTRA_API_AUDIENCE` | Application (client) ID GUID de la API |
| `ENTRA_JWK_SET_URI` | Opcional; por defecto se descubre desde el issuer |
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

En producción:

```bash
mvn clean package
java -jar target/ms-pedidos360-bff-1.0.0.jar
```
