# SECURITY-REMEDIATION-001 — Correcciones de seguridad SEC-003, SEC-004, SEC-005

| Campo | Valor |
|---|---|
| Responsable | Claude Code (sesión separada, permisos normales) |
| Base | `1d5dde4` (rama `develop`) |
| Hallazgos | SEC-003, SEC-004, SEC-005 (ver `docs/RISK-REGISTER.md`, `docs/SECURITY-MODEL.md`) |
| Estado inicial | PLANNED |

## Criterios de aceptación (validables por el coordinador)

1. **SEC-003**: `POST /api/payments/authorize` responde 403 cuando el actor autenticado no es el `customerId` de la reserva; 404 si la reserva no existe; 200 para el dueño. La comprobación ocurre **antes** de la búsqueda por `Idempotency-Key` y antes de llamar a `PaymentPort`.
2. **SEC-004**: en `audit-service/backend`, `GET /api/audit/events`, `/api/audit/events/{id}` y `/api/audit/stats` devuelven 401 sin credenciales, 403 con un rol distinto de ADMIN y 200 con un JWT ADMIN válido. `/actuator/health/**` sigue siendo público (healthcheck de Docker).
3. **SEC-005**: `PUT` y `DELETE /api/categories/{id}` devuelven 403 para PROVIDER y CUSTOMER, 2xx para ADMIN y 401 sin autenticación.
4. Existen pruebas nuevas o actualizadas para **acceso permitido y denegado** de cada hallazgo (ver "Pruebas").
5. `mvn test` en `backend/` y `mvn test` en `audit-service/backend/` se ejecutan y se informa el resultado real (aprobadas/falladas/omitidas). Si Docker no está disponible, se declara qué suites no pudieron correr.
6. Ningún cambio fuera del alcance de archivos; ningún commit, `git add`, stash ni cambio de rama.
7. No se marca ningún hallazgo como RESUELTO en documentos: el coordinador lo hace tras re-ejecutar las pruebas.
8. Se entrega el informe final con el formato indicado abajo.

## Descripción y evidencia inicial

| ID | Evidencia (verificada en `1d5dde4`) |
|---|---|
| SEC-003 | `PaymentController.authorizePayment()` no recibe `Authentication`; arma `PaymentCommand` con el `bookingId` del cuerpo y llama a `AuthorizePaymentUseCase.authorize`. `PaymentAuthorizationService.authorize` consulta primero por `idempotencyKey` y devuelve el resultado guardado sin importar quién llama. |
| SEC-004 | `AuditController` (`/api/audit/**`) sin Spring Security: `audit-service/backend/pom.xml` no incluye `spring-boot-starter-security`; `application.yml` solo expone `health,info` por actuator. El frontend de auditoría ya envía `Authorization: Bearer <token>` (`audit-service/frontend/src/app/auth.interceptor.ts`). |
| SEC-005 | `CategoryController`: `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` en POST, PUT y DELETE. El registro de riesgos prescribe `hasRole('ADMIN')` para DELETE; este encargo extiende la restricción a PUT (modificación). |

Datos útiles: `JwtService.generate(email, role)` firma HS256 con `app.jwt.secret` e incluye el claim `role` (`CUSTOMER|PROVIDER|ADMIN`). `CurrentActorResolver.from(Authentication)` devuelve `Actor(id, email, role)`. `Booking` es un record con `customerId()`. `GlobalExceptionHandler` mapea `DomainNotFoundException` y `ForbiddenOperationException`.

## Archivos y módulos a inspeccionar (leer solo esto)

- `docs/RISK-REGISTER.md` (filas SEC-003/004/005), `docs/SECURITY-MODEL.md` (secciones 2 y 4), `docs/TESTING.md`.
- Backend: `.../infrastructure/adapter/in/rest/PaymentController.java`, `CategoryController.java`, `GlobalExceptionHandler.java`; `.../application/service/PaymentAuthorizationService.java`; `.../application/port/in/AuthorizePaymentUseCase.java`; `.../application/port/out/BookingRepositoryPort.java`; `.../domain/model/PaymentCommand.java`, `Booking.java`; `.../infrastructure/security/CurrentActorResolver.java`, `JwtService.java`; `.../infrastructure/config/SecurityConfiguration.java`.
- Pruebas de referencia: `PaymentAuthorizationServiceTest`, `BookingControllerSecurityTest`, `OfferingSecurityTest`, `AdminUserControllerTest`.
- Audit: `audit-service/backend/pom.xml`, `src/main/resources/application.yml`, `controller/AuditController.java`, `config/CorsConfig.java`, `src/test/.../AuditEventRepositoryTest.java`, `docker-compose.yml` (solo el servicio `audit-backend`).

## Comportamiento esperado

**SEC-003.** La autorización se decide en la capa de aplicación (la verificación debe sobrevivir si otro adaptador invoca el caso de uso). Propuesta: el controlador resuelve el `Actor` con `CurrentActorResolver` y lo pasa al caso de uso; el servicio carga la reserva con `BookingRepositoryPort.findById`, lanza `DomainNotFoundException` si no existe y `ForbiddenOperationException` si `booking.customerId()` no coincide con `actor.id()`. Decisión a documentar en el informe: por defecto **solo el dueño** puede pagar (ADMIN y PROVIDER no pagan por terceros) salvo que el código existente indique lo contrario. El dominio permanece Java puro.

**SEC-004.** Añadir Spring Security a `audit-service/backend` con sesión stateless y validar el JWT emitido por el backend principal **sin importar clases de `backend/`** (AGENTS.md §1). El secreto se inyecta por variable de entorno (por ejemplo `JWT_SECRET`, mismo valor que `app.jwt.secret` del backend), sin valor por defecto inseguro hardcodeado en el audit-service: si falta, el arranque debe fallar de forma explícita. Reglas: `/api/audit/**` requiere ADMIN; `/actuator/health/**` público; CORS existente conservado (incluido el header `Authorization`); CSRF no aplica a peticiones Bearer sin cookie, justifíquese la decisión. Si se necesita añadir la variable a `docker-compose.yml` (servicio `audit-backend`), hacerlo referenciando una variable de entorno, **sin escribir secretos reales**.

**SEC-005.** Cambiar `@PreAuthorize` de `update` y `delete` a `hasRole('ADMIN')`. No tocar `create` ni `list`.

## Pruebas necesarias

| Hallazgo | Permitido | Denegado |
|---|---|---|
| SEC-003 | Dueño de la reserva → 200/resultado de pago | Otro CUSTOMER → 403 y `PaymentPort.process` **nunca invocado**; reserva inexistente → 404; replay de `Idempotency-Key` ajena por otro usuario → 403 sin devolver el resultado guardado; sin autenticación → 401 |
| SEC-004 | JWT ADMIN → 200 en los 3 endpoints; `/actuator/health` sin token → 200 | Sin token → 401; JWT CUSTOMER/PROVIDER → 403; token expirado o firma inválida → 401 |
| SEC-005 | ADMIN → PUT/DELETE exitosos | PROVIDER y CUSTOMER → 403 en PUT y DELETE; anónimo → 401 |

Seguir el estilo de las pruebas existentes (`@WebMvcTest`/Mockito, sin Docker, cuando sea posible). Las pruebas deben fallar contra el código original (indicar cómo se comprobó, p. ej. ejecutándolas antes del cambio) y pasar tras él.

## Alcance de archivos (único permitido)

- `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java`
- `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java`
- `backend/src/main/java/com/riwi/skillbridge/application/service/PaymentAuthorizationService.java`
- `backend/src/main/java/com/riwi/skillbridge/application/port/in/AuthorizePaymentUseCase.java`
- `backend/src/main/java/com/riwi/skillbridge/domain/model/PaymentCommand.java` (solo si el diseño lo exige)
- `backend/src/test/java/com/riwi/skillbridge/` (clases de prueba nuevas o existentes de pagos, categorías y seguridad REST)
- `audit-service/backend/pom.xml`, `audit-service/backend/src/main/`, `audit-service/backend/src/test/`
- `docker-compose.yml` (solo el bloque `audit-backend.environment`, y solo si es imprescindible)

## Restricciones

- Rama y worktree propios; sin commits, `git add`, stash, reset ni cambios de rama; sin `--dangerously-skip-permissions`.
- Prohibido tocar: `docs/audit/**`, `scripts/repo_audit.py`, `backend/src/main/resources/db/migration/*` existentes, `.env*`, `docs/PROJECT_CONTEXT.md`, `docs/tasks/WORKSTREAM-LOG.md`, `docs/RISK-REGISTER.md`, `docs/SECURITY-MODEL.md`.
- No generalizar a otras reglas de seguridad (SEC-001/002/006A/007-010 quedan fuera).
- No dejar secretos ni credenciales en código, configuración ni logs.
- Si una corrección exige salir del alcance, detenerse y reportarlo en vez de ampliar el cambio.

## Fuera de alcance

SEC-001, SEC-002, SEC-006A, SEC-007, SEC-008, SEC-009 (PAN/CVV en DTO), SEC-010 (rate limiting); validar que `amount` coincida con el precio de la reserva; cambios de frontend; rediseño del modelo de identidad entre servicios (OIDC/JWKS); `create` de categorías; refactor de `SecurityConfiguration`.

## Riesgos y pendientes conocidos

- **Regresión de UI SEC-005**: `frontend/src/app/features/provider-offerings.component.ts` llama a `categoryService.delete`. Tras el cambio, PROVIDER recibirá 403; informar el impacto, no corregir el frontend aquí.
- **SEC-004 y secreto compartido**: validar HS256 con un secreto común acopla a ambos servicios por configuración; documentarlo y proponer alternativa en el informe.
- **Maven offline**: añadir dependencias de seguridad al audit-service puede requerir red. Si falla la resolución, informar el comando exacto y el error; no instalar ni cambiar configuración global.
- Suites con Testcontainers requieren Docker (`AuditEventRepositoryTest`, `BookingPersistenceAdapterTest`); indicar si no se ejecutaron.
- El `amount` del pago lo sigue decidiendo el cliente (pendiente, fuera de alcance).

## Formato del informe final (`report.md`)

1. Resumen por hallazgo (SEC-003/004/005): causa, cambio, archivos tocados (`ruta:línea`).
2. Pruebas **ejecutadas** (comando, módulo, salida resumida con totales reales) y pruebas **no ejecutadas** (motivo).
3. Demostración de que las pruebas nuevas fallan sin el cambio y pasan con él.
4. Decisiones tomadas (política ADMIN en pagos, esquema JWT en audit, CSRF) y alternativas descartadas.
5. Limitaciones, regresiones esperadas y pendientes.
6. Recomendación: ready-for-review / needs-fixes, con `git diff --stat` real.
Marcar cada afirmación como `[VERIFIED]`, `[INFERRED]` o `[UNKNOWN]` (AGENTS.md §2).
