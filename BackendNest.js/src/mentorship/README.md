# Módulo `mentorship`

Consulta de las mentorías contratadas por un estudiante y reporte de su porcentaje de avance
(HU-MNT-03). Contratos de referencia: `docs/contracts/mentorships/` en la raíz del repositorio
(`rest-api.md`, `state-machine.md`, `security.md`).

> **Regla de oro — 1 pago = 1 mentoría.** Cada pago aprobado habilita una única asignación
> (`paymentId` es único en la tabla). Al llegar al 100 % la mentoría pasa a `COMPLETED`, el pago
> queda consumido y ya no puede reiniciarse.

## Estructura

| Archivo | Responsabilidad |
|---|---|
| `mentorship.module.ts` | Registra el repositorio TypeORM, `JwtModule`, el controller y el guard. |
| `mentorship.controller.ts` | Rutas HTTP bajo `/api/v1/mentorships`, protegidas con `JwtAuthGuard`. |
| `mentorship.service.ts` | Reglas de negocio: propiedad, ventana de acceso, monotonía y bloqueo terminal. |
| `dto/update-progress.dto.ts` | Cuerpo del `PATCH`: `progressPercent` entero entre 0 y 100. |
| `entities/mentorship-assignment.entity.ts` | Entidad `mentorship_assignments` y enum `MentorshipStatus`. |
| `../auth/jwt-auth.guard.ts` | Valida el JWT del monolito y expone `req.user`. |

## Autenticación

Todas las rutas exigen `Authorization: Bearer <token>` con un JWT emitido por el backend Java:

- Firma **RS256**, verificada con la variable `JWT_PUBLIC_KEY` (PEM X.509; puede ir en una sola
  línea con `\n` literales).
- Emisor (`iss`) obligatorio: `skillbridge`.
- El id del usuario se toma del claim **`uid`**. `sub` contiene el correo y **no** se usa como id.
- Solo el rol `CUSTOMER` puede usar estas rutas; cualquier otro recibe `403`.

La cookie `access_token` todavía **no** se acepta (requeriría además protección CSRF).

## Endpoints

### `GET /api/v1/mentorships/me`

Devuelve las asignaciones del estudiante autenticado, ordenadas por `accessStartsAt` descendente.
Solo lee: las asignaciones las crea el evento de pago aprobado, nunca esta ruta.

```http
GET /api/v1/mentorships/me
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

`200 OK`

```json
[
  {
    "id": "22222222-2222-4222-8222-222222222222",
    "studentId": "11111111-1111-4111-8111-111111111111",
    "paymentId": "33333333-3333-4333-8333-333333333333",
    "status": "IN_PROGRESS",
    "progressPercent": 60,
    "accessStartsAt": "2026-11-13T14:00:00.000Z",
    "accessEndsAt": "2026-11-13T16:00:00.000Z",
    "completedAt": null,
    "createdAt": "2026-11-01T00:00:00.000Z"
  }
]
```

### `PATCH /api/v1/mentorships/:id/progress`

Actualiza el porcentaje de avance de una mentoría propia.

```http
PATCH /api/v1/mentorships/22222222-2222-4222-8222-222222222222/progress
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
Content-Type: application/json

{ "progressPercent": 60 }
```

`200 OK` con la asignación actualizada.

| `progressPercent` | Estado resultante |
|---|---|
| `0` | Se mantiene el estado actual (`SCHEDULED` no se inicia). |
| `1`–`99` | `IN_PROGRESS` |
| `100` | `COMPLETED`, con `completedAt = now()`. **Irreversible.** |

## Reglas y errores

| HTTP | Cuándo |
|---|---|
| `400` | Cuerpo inválido: `progressPercent` ausente, no entero o fuera de 0–100; `:id` no es UUID. |
| `400` | La mentoría está en estado terminal (`COMPLETED`, `CANCELLED`, `NO_SHOW`), también si otra petición la completó al mismo tiempo. |
| `401` | Sin token, token inválido o expirado, firma o emisor incorrectos, algoritmo distinto de RS256, o sin claims `uid`/`role`. |
| `403` | El rol no es `CUSTOMER`. |
| `403` | Fuera de la ventana de acceso `[accessStartsAt, accessEndsAt)`: el instante final ya está fuera. |
| `404` | La mentoría no existe **o pertenece a otro estudiante** (no se revela su existencia). |
| `422` | El nuevo porcentaje es menor que el actual (el progreso es monótono). Repetir el mismo valor está permitido. |
| `500` | `JWT_PUBLIC_KEY` no configurada (error de configuración, no de autenticación). |

Los errores usan el formato del `HttpExceptionFilter` global:
`{ statusCode, error, message, path, timestamp }`.

> **Diferencia con el contrato:** `docs/contracts/mentorships/state-machine.md` pide `409` al
> intentar modificar una mentoría terminal; la historia de usuario HU-MNT-03 pide `400`. Se
> implementó `400`, según la historia. Debe acordarse cuál prevalece.

### Concurrencia

El `UPDATE` se condiciona a que el estado no sea terminal. Si dos peticiones compiten y una
completa la mentoría primero, la otra afecta 0 filas y recibe `400`: un pago consumido no puede
reabrirse por una carrera.

## Configuración

| Variable | Uso |
|---|---|
| `JWT_PUBLIC_KEY` | Clave pública RS256 del monolito. Obligatoria. |
| `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DATABASE` | Conexión PostgreSQL (`src/database/database.module.ts`). |
| `PORT` | Puerto HTTP (por defecto `3000`). |

## Tests

Desde `BackendNest.js/`:

```bash
npm test                                         # toda la suite
npx vitest run src/mentorship src/auth           # solo este módulo y el guard
```

| Spec | Cubre |
|---|---|
| `mentorship.service.spec.ts` | Los tres escenarios de la HU, estados terminales, carrera, 404, ventana y regresión. |
| `dto/update-progress.dto.spec.ts` | Límites de `@Min(0)`/`@Max(100)`, decimales y campo ausente. |
| `../auth/jwt-auth.guard.spec.ts` | Firma con claves RSA reales: claim `uid`, emisor, expiración, HS256, roles y clave ausente. |

Son tests unitarios con el repositorio simulado; no necesitan base de datos.

## Pendiente

- **No hay migración** para la tabla `mentorship_assignments` y `synchronize` está en `false`: los
  endpoints fallarán contra una base real hasta que se cree.
- La representación devuelve la entidad tal cual (`studentId`, `paymentId`); el contrato
  `rest-api.md` describe un DTO con `bookingId`, `customerId`, `offeringId`, `mode` y `version`.
- Aceptar la cookie `access_token` junto con protección CSRF.
- Publicar los eventos `MentorshipStarted`, `MentorshipProgressUpdated` y `MentorshipCompleted`
  (outbox → Kafka, ver `docs/contracts/mentorships/events.md`).
- El estado `EXPIRED` del contrato aún no existe en el enum.
