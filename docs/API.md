# API starter

Base path: `/api`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register CUSTOMER and return JWT |
| POST | `/auth/login` | Public | Login and return JWT |
| GET | `/offerings` | Public | Active catalog; Redis-backed |
| POST | `/bookings` | Bearer JWT + `Idempotency-Key` | Persist booking once and publish event |
| PATCH | `/bookings/{bookingId}/cancel` | Bearer JWT | Cancel own booking when allowed |
| POST | `/ai/recommendations` | Bearer JWT | Generate catalog-grounded recommendation |


OpenAPI UI locally:

```text
http://localhost:8080/swagger-ui.html
```

## Crear reserva idempotente

```http
POST /api/bookings
Authorization: Bearer <jwt>
Idempotency-Key: <clave-única-por-intento>
Content-Type: application/json
```

La primera petición devuelve `201 Created`. Repetir la misma clave con el mismo cuerpo devuelve la misma reserva sin duplicar eventos ni notificaciones. La clave ausente devuelve `400`; reutilizarla con un cuerpo distinto devuelve `409`.

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
