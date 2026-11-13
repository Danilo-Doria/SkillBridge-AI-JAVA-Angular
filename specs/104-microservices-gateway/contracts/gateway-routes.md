# Contrato: rutas del gateway (Traefik)

Estado: **Draft**. Los prefijos salen de los `@RequestMapping` reales del backend (verificado 2026-11-13).
"Destino fase 1" es el estado al terminar la Fase 1 (todo al monolito); "destino final" al terminar la iteración.

| Prefijo (mayor prioridad primero) | Público | Perfil rate limit | Destino fase 1 | Destino final | Breaker |
|---|---|---|---|---|---|
| `/api/webhooks/**` | Sí (firma Stripe) | `webhook` (holgado) | monolito | `payment-service` | sí |
| `/api/payments/**` | Auth | `default` | monolito | `payment-service` | sí |
| `/api/ai/**`, `/api/admin/ai/providers/**` | Auth | `ai` (estricto) | monolito | `ai-service` | sí |
| `/api/auth/**` | Sí | `auth` (muy estricto) | monolito | monolito (identity) | sí |
| `/api/mentorships/**`, `/api/mentorship-resources/**` | Auth | `default` | monolito | monolito, luego `mentorship-service` (NestJS, ADR-0006 §3) | sí |
| `/api/bookings/**` | Auth | `default` | monolito | monolito (booking) | sí |
| `/api/offerings/**`, `/api/categories/**`, `/api/admin/offerings/**` | Mixto | `default` | monolito | monolito (catalog) | sí |
| `/api/admin/users/**`, `/api/provider-requests/**`, `/api/admin/provider-requests/**` | Auth | `default` | monolito | monolito (identity) | sí |
| `/docs/**` | `basicAuth` | `default` | swagger-ui | swagger-ui | no |
| `/audit/**` o subdominio | Admin | `default` | audit-frontend/backend | igual | sí |
| `/` | Sí | `default` | frontend (nginx estático) | igual | no |

Notas:
- `/api/ai/knowledge` cae bajo `/api/ai/**`. **No** se mueve a `ai-service` hasta resolver Q4.
- `notification-service` **no expone HTTP** (solo consume Kafka); solo `actuator/health` en red interna.
- Puertos de BD, Kafka, Prometheus y Grafana **no** se publican; Grafana/Prometheus solo por túnel SSH o `basicAuth`.

## Perfiles de middleware (valores iniciales, configurables)

| Perfil | average | period | burst | Clave |
|---|---|---|---|---|
| `auth` | 10 | 1m | 5 | IP real |
| `ai` | 20 | 1m | 5 | IP real |
| `default` | 120 | 1m | 60 | IP real |
| `webhook` | 600 | 1m | 200 | IP real (Stripe) |

Circuit breaker inicial: `NetworkErrorRatio() > 0.30 || ResponseCodeRatio(500, 600, 0, 600) > 0.30`,
`checkPeriod=10s`, `fallbackDuration=30s`, `recoveryDuration=30s`. `retry` solo en métodos GET
(2 intentos, backoff). Estos valores son **[INFERRED]** y deben calibrarse con el test de carga (T020).

## Cabeceras

| Cabecera | Regla en el borde |
|---|---|
| `X-Correlation-Id` | Conservar si es UUID válido; generar si falta o es inválido; devolver en la respuesta |
| `X-User-Id`, `X-User-Roles` | Eliminar siempre en la entrada |
| `X-Forwarded-*` | Confiar solo en el proxy/CDN configurado (`trustedIPs`) |
| `Set-Cookie` / `Cookie` | Pasar sin modificar |
| `Strict-Transport-Security` | Añadir en respuestas |

## Errores del borde
Formato `application/problem+json` con `type`, `title`, `status`, `correlationId`. 429 incluye `Retry-After`; 503 por circuito incluye `Retry-After`.
