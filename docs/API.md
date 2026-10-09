# API starter

Base path: `/api`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register CUSTOMER and return JWT |
| POST | `/auth/login` | Public | Login and return JWT |
| GET | `/offerings` | Public | Active catalog; Redis-backed |
| POST | `/bookings` | Bearer JWT | Persist booking and publish event |
| PATCH | `/bookings/{bookingId}/cancel` | Bearer JWT | Cancel own booking when allowed |
| POST | `/ai/recommendations` | Bearer JWT | Generate catalog-grounded recommendation |


OpenAPI UI locally:

```text
http://localhost:8080/swagger-ui.html
```

## Suggested next endpoints

```text
GET    /api/offerings                     público, catálogo activo
GET    /api/offerings/me                  PROVIDER
POST   /api/offerings                     PROVIDER, ADMIN
PUT    /api/offerings/{id}                PROVIDER (dueño), ADMIN
POST   /api/offerings/{id}/deactivate     PROVIDER (dueño), ADMIN
POST   /api/offerings/{id}/activate       PROVIDER (dueño), ADMIN
GET    /api/admin/offerings               ADMIN
```

The provider write endpoints should invalidate the public offerings cache through `OfferingCachePort`.

## Administración de usuarios (HU-20) — solo ADMIN

GET    /api/admin/users?page=0&size=20&sort=createdAt,desc   lista paginada
GET    /api/admin/users/{id}                                 detalle
PUT    /api/admin/users/{id}/status   {"status":"ACTIVE|SUSPENDED"}
PUT    /api/admin/users/{id}/role     {"role":"CUSTOMER|PROVIDER|ADMIN"}

Orden permitido (sort=campo,asc|desc): name, email, role, status, createdAt. Tamaño de página: 1 a 100.

Respuestas: 200 OK · 400 datos inválidos (estado/rol/orden/paginación) · 401 sin sesión ·
403 el rol no es ADMIN · 404 usuario inexistente · 422 regla de negocio.

Reglas:
- Un Admin no puede cambiar su propio estado ni su propio rol (422).
- No se puede asignar el estado o el rol que el usuario ya tiene (422).
- Una cuenta SUSPENDED no puede iniciar sesión (403) y su token deja de ser válido de inmediato (401).
- El cambio de rol es inmediato en el backend; el frontend lo refleja en el siguiente inicio de sesión.
- Las respuestas nunca incluyen la contraseña ni su hash.
- Los cambios de estado y de rol (éxitos y rechazos) quedan en el log de auditoría `AUDIT`
  con ids y valores, sin correos, contraseñas ni JWT.
