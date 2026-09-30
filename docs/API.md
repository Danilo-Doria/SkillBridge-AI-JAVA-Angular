# API starter

Base path: `/api`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register CUSTOMER and return JWT |
| POST | `/auth/login` | Public | Login and return JWT |
| GET | `/offerings` | Public | Active catalog; Redis-backed |
| POST | `/bookings` | Bearer JWT | Persist booking and publish event |
| POST | `/ai/recommendations` | Bearer JWT | Generate catalog-grounded recommendation |

OpenAPI UI locally:

```text
http://localhost:8080/swagger-ui.html
```

## Suggested next endpoints

```text
POST   /api/provider/offerings
PUT    /api/provider/offerings/{id}
PATCH  /api/provider/offerings/{id}/status
GET    /api/bookings/me
PATCH  /api/bookings/{id}/cancel
GET    /api/admin/metrics/business
```

The provider write endpoints should invalidate the public offerings cache through `OfferingCachePort`.
