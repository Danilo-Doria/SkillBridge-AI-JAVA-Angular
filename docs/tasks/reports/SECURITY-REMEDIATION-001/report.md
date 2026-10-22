# SECURITY-REMEDIATION-001 — Informe final

Rama `agent/SECURITY-REMEDIATION-001-20261022T001327Z`, base `1d5dde4`. Cambios solo como ediciones del árbol de trabajo (sin commit, `git add`, stash, reset ni cambio de rama).
Marcas: `[VERIFIED]` confirmado en esta sesión · `[INFERRED]` deducido · `[UNKNOWN]` no confirmado.

## 1. Resumen por hallazgo

### SEC-003 — BOLA/IDOR en `POST /api/payments/authorize`
- **Causa** `[VERIFIED]`: el controlador no recibía `Authentication`; el servicio consultaba primero por `idempotencyKey` y devolvía el resultado guardado a cualquiera, y llamaba a `PaymentPort` sin comprobar la propiedad de la reserva.
- **Cambio** `[VERIFIED]`:
  - `AuthorizePaymentUseCase.authorize(PaymentCommand, Actor)` (`backend/.../application/port/in/AuthorizePaymentUseCase.java:15`).
  - `PaymentAuthorizationService.authorize` (`.../application/service/PaymentAuthorizationService.java:41-60`): `bookingId` nulo → `IllegalArgumentException` (400); `BookingRepositoryPort.findById` → `DomainNotFoundException` (404); `booking.customerId() != actor.id()` → `ForbiddenOperationException` (403). **Todo ocurre antes** de `findByIdempotencyKey` y de `PaymentPort`.
  - Refuerzo añadido (no pedido expresamente): la clave de idempotencia es global, así que si el resultado guardado pertenece a **otro** `bookingId` se responde 403 (`:55-57`). Sin esto, un usuario podía pagar su propia reserva con la clave de otro y recibir el `PaymentResult` ajeno (incluido `clientSecret`).
  - `PaymentController` resuelve el `Actor` con `CurrentActorResolver` y lo pasa al caso de uso (`.../adapter/in/rest/PaymentController.java:19-42`).
  - `PaymentCommand` y el dominio **no se modificaron** (siguen siendo Java puro).
- 401 sin autenticación: lo da la `SecurityConfiguration` existente (`anyRequest().authenticated()`), sin cambios.

### SEC-004 — Audit service sin autenticación
- **Causa** `[VERIFIED]`: `audit-service/backend` no tenía Spring Security; `/api/audit/**` era público.
- **Cambio** `[VERIFIED]`:
  - `pom.xml`: `spring-boot-starter-security` + jjwt 0.12.6 (api/impl/jackson, misma versión que `backend/pom.xml`).
  - `config/SecurityConfig.java:24-35`: stateless, CSRF deshabilitado, `cors(withDefaults())` (reutiliza `CorsConfig`, que ya permite `Authorization` con `allowedHeaders("*")`; **`CorsConfig` sin tocar**), 401 por `HttpStatusEntryPoint`, `/actuator/health/**` y `/error` públicos, `/api/audit/**` → `hasRole("ADMIN")`, `anyRequest().authenticated()`.
  - `security/JwtTokenVerifier.java`: valida HS256 con el secreto compartido; exige firma, `exp` y `sub`. Un token sin firma (`alg=none`), sin expiración, expirado o con otra firma → vacío → 401. Constructor falla con mensaje explícito si el secreto falta/está en blanco/<32 caracteres.
  - `security/BearerJwtAuthenticationFilter.java`: lee `Authorization: Bearer`, asigna `ROLE_<role>` solo para `CUSTOMER|PROVIDER|ADMIN`; el filtro se instancia dentro de `SecurityConfig` (no es `@Component`, para que no se registre dos veces como filtro de servlet). No registra el token.
  - `application.yml:39`: `app.jwt.secret: ${JWT_SECRET:}` (sin valor por defecto: vacío ⇒ el arranque falla). `application.yml:9`: se excluye `UserDetailsServiceAutoConfiguration` para que Boot no imprima una contraseña generada en el log.
  - `docker-compose.yml:220` (solo `audit-backend.environment`): `JWT_SECRET: ${JWT_SECRET:-<placeholder>}`; ver §5 sobre este default.
- Sin importar clases de `backend/` `[VERIFIED]` (el contrato es solo el token).

### SEC-005 — Permisos excesivos en categorías
- **Causa** `[VERIFIED]`: `update` y `delete` usaban `hasAnyRole('PROVIDER','ADMIN')`.
- **Cambio** `[VERIFIED]`: `hasRole('ADMIN')` en `update` y `delete` (`CategoryController.java:53` y `:61`). `create` (`:46`) y `list` sin tocar.

## 2. Pruebas

Entorno: Maven 3.8.7 con `JAVA_HOME=~/.jdks/azul-21.0.12.1` solo como variable del comando (el JDK del sistema es un JRE sin `javac`; no se cambió configuración global). Docker disponible.
Comandos con red (sin `-o`): `mvn -o` falla en `backend/` porque el plugin `jacoco-maven-plugin:0.8.13` y sus dependencias no están en `~/.m2` (`Cannot access central ... in offline mode`). Maven descargó esos artefactos a `~/.m2` en la primera ejecución con red.

### Ejecutadas `[VERIFIED]`
| Módulo | Comando | Resultado |
|---|---|---|
| `backend/` | `mvn test` | **233 ejecutadas, 0 fallos, 0 errores, 1 omitida** (`JpaOfferingRepositoryTest`, `@Disabled("Docker Bug Testcontainers")` preexistente). BUILD SUCCESS. Incluye `BookingPersistenceAdapterTest` (Testcontainers, 1/1 OK). |
| `audit-service/backend/` | `mvn test` | **21 ejecutadas, 0 fallos, 0 errores, 0 omitidas**: `AuditEventRepositoryTest` 2/2 (Testcontainers Mongo), `AuditControllerSecurityTest` 14/14, `JwtTokenVerifierTest` 5/5. BUILD SUCCESS. |
| `audit-service/backend/` | `java -jar target/audit-service-0.0.1-SNAPSHOT.jar` sin `JWT_SECRET` (timeout 90 s) | Falla al arrancar con `IllegalStateException: JWT_SECRET (app.jwt.secret) es obligatorio y debe tener al menos 32 caracteres; ...`. |

Pruebas nuevas/actualizadas:
- `backend/.../adapter/in/rest/PaymentControllerSecurityTest` (9, cadena real seguridad+controlador+servicio): dueño → 200; otro CUSTOMER, ADMIN y PROVIDER → 403 con `PaymentPort.process` nunca invocado (ni `save` ni eventos); reserva inexistente → 404; replay de clave ajena (misma reserva y reserva propia) → 403 sin el resultado guardado en el cuerpo; el dueño repite su propia clave → 200 sin reprocesar; sin autenticación → 401 (sin tocar `PaymentPort` ni `BookingRepositoryPort`).
- `backend/.../service/PaymentAuthorizationServiceTest` (8; 3 existentes adaptados a la nueva firma + 5 nuevos): forbidden/not-found/`bookingId` nulo/clave de otra reserva, verificando `verifyNoInteractions` sobre idempotencia, pasarela y eventos.
- `backend/.../adapter/in/rest/CategoryControllerSecurityTest` (8): ADMIN PUT 200 / DELETE 204; PROVIDER y CUSTOMER 403 en PUT y DELETE; anónimo 401; casos de uso nunca invocados en los denegados.
- `audit-service/.../controller/AuditControllerSecurityTest` (14): ADMIN 200 en `/events`, `/events/{id}`, `/stats`; health público (200 vía stub, ver limitación); preflight CORS con `authorization`; sin token 401; CUSTOMER/PROVIDER 403; rol desconocido/ausente 403; expirado, sin `exp`, firma inválida, `alg=none`, malformado y `Basic` → 401; ruta no listada → 401.
- `audit-service/.../security/JwtTokenVerifierTest` (5): arranque falla sin secreto / en blanco / <32 caracteres; arranca con secreto válido.

### No ejecutadas
- `mvn verify` (`*IT`) en ninguno de los dos módulos: fuera de lo pedido (`NotificationFlowIT` además está `@Disabled`).
- `mvn test` de `frontend/` y `audit-service/frontend/`: sin cambios de frontend.
- Arranque real del audit-service **con** secreto válido contra Kafka/Mongo, y `docker compose up`: no ejecutados `[UNKNOWN]` (solo se validó sintaxis con `docker compose config -q`, sin errores).

## 3. Las pruebas nuevas fallan sin el cambio y pasan con él `[VERIFIED]`

Se escribieron las pruebas antes del cambio y se ejecutaron contra el código original.

| Prueba | Contra el código original | Con el cambio |
|---|---|---|
| `backend` `PaymentControllerSecurityTest` + `CategoryControllerSecurityTest` (`mvn test -Dtest=...`) | **17 ejecutadas, 3 fallos + 5 errores** → 6/9 de pagos y 2/8 de categorías. Pagos: replays esperan 403 y reciben 200; otro CUSTOMER/ADMIN/PROVIDER y reserva inexistente llegan a la pasarela (NPE porque `PaymentPort` simulado devuelve null: el pago *se intentó*). Categorías: `provider_no_puede_eliminar` esperaba 403, recibió 204; `provider_no_puede_actualizar` llegó al caso de uso. Log: `scratchpad/baseline-backend.log`. | 17/17 OK |
| `audit-service` `AuditControllerSecurityTest` | **14 ejecutadas, 10 fallos**: sobre una copia limpia de `HEAD` (`git archive` en el scratchpad, solo lectura sobre git) con únicamente jjwt de prueba y sin las clases de seguridad (tokens expirados/falsos/sin token/CUSTOMER → 200 en lugar de 401/403; los 4 de ADMIN pasan porque sin seguridad todo pasa). Log: `scratchpad/baseline-audit.log`. | 14/14 OK |
| `PaymentAuthorizationServiceTest`, `JwtTokenVerifierTest` | No comparables: cambia la firma `authorize(...)` / la clase `JwtTokenVerifier` no existía ⇒ no compilan contra el original (el fallo es de compilación). La cobertura equivalente a nivel HTTP sí se demostró arriba. | OK |

`health_es_publico` también falló en la línea base (404 sin regla ni stub registrado en esa copia); con el cambio es 200.

## 4. Decisiones y alternativas descartadas

- **Pagos: solo el dueño** (ADMIN y PROVIDER → 403) `[VERIFIED]`: el código existente no indicaba lo contrario (`Booking.customerId()` es el único vínculo y el frontend de pago es de cliente). Alternativa descartada: permitir ADMIN (ampliaría el permiso de cobrar por terceros sin requisito).
- **Autorización en la capa de aplicación** con `Actor` como parámetro, no con `@PreAuthorize`/SpEL en el controlador: sobrevive a otro adaptador que invoque el caso de uso.
- **404 vs 403 distinguibles**: la reserva inexistente da 404 y la ajena 403, lo que permite enumerar ids existentes. Se siguió el criterio de aceptación; `BookingController` usa 404 para ajena/inexistente. Decisión pendiente para el coordinador `[INFERRED]`.
- **Esquema JWT en audit**: HS256 con secreto compartido `JWT_SECRET` vía jjwt (ya en `~/.m2`, misma librería que el backend). Alternativa descartada: `spring-boot-starter-oauth2-resource-server` (Nimbus no está en `~/.m2`, exigiría red y cambia más superficie). Alternativa recomendada a futuro: asimétrico (RS256/JWKS u OIDC) para que audit solo tenga la clave pública; fuera de alcance.
- **CSRF deshabilitado en audit** `[VERIFIED en código]`: el servicio solo autentica por `Authorization: Bearer`, no lee cookies ni crea sesión, y los 3 endpoints son GET; no existe credencial ambiental que un sitio externo pueda reutilizar. Mismo criterio que `bearerRequests` en el backend.
- **CORS**: `CorsConfig` intacto; Security delega en él (`cors(withDefaults())`) y el preflight se resuelve antes de autenticar (probado).

## 5. Limitaciones, regresiones esperadas y pendientes

- **Regresión UI SEC-005** `[VERIFIED]`: `frontend/src/app/features/provider-offerings.component.ts:394` llama a `categoryService.delete`; PROVIDER recibirá 403. Por la misma restricción se ve afectada cualquier pantalla de edición de categorías para PROVIDER `[UNKNOWN]` (no inspeccionada). No se tocó el frontend.
- **Frontend de pagos**: usa `crypto.randomUUID()` por intento (`payment-modal.component.ts:272,413`), compatible con el nuevo chequeo de clave↔reserva `[VERIFIED]`.
- **Secreto compartido** `[VERIFIED]`: audit y backend quedan acoplados por configuración (mismo `JWT_SECRET`); rotarlo exige reiniciar ambos. Alternativa: RS256/JWKS (ver §4).
- **Default en `docker-compose.yml`** `[VERIFIED]`: `audit-backend` usa `${JWT_SECRET:-change-this-to-a-long-random-secret-...}`, idéntico al placeholder que ya usa el servicio `backend` (`docker-compose.yml:70`), para que ambos coincidan por defecto en local. No es un secreto real, pero es débil: en despliegues debe definirse `JWT_SECRET`. El audit-service en sí **no** tiene default. Alternativa si se prefiere fallo duro en compose: `${JWT_SECRET:?...}` en ambos servicios (cambiaría el comportamiento de `backend`, fuera de alcance).
- **Healthcheck de Docker** `[INFERRED]`: `/actuator/health/liveness` queda cubierto por `/actuator/health/**`, pero el test de salud usa un controlador stub porque `@WebMvcTest` no autoconfigura actuator; la ruta real de actuator no se probó con el servicio levantado `[UNKNOWN]`.
- **Pendiente fuera de alcance**: `amount` sigue decidido por el cliente; SEC-001/002/006A/007–010; `audit-frontend` ya envía Bearer pero no verifiqué que el token almacenado (`skillbridge_token`) pertenezca a un ADMIN `[UNKNOWN]`.
- **Artefactos de build rastreados** `[VERIFIED]`: `audit-service/backend/target/` tiene 10 archivos versionados en git. `mvn test` los modificó (`classes/application.yml`, `maven-status/*`, `surefire-reports/*`) y creó 4 informes surefire nuevos. Los restauré a su contenido de `HEAD` (`git show HEAD:<ruta> > <ruta>`) y borré los 4 nuevos; no usé checkout/stash/reset. Quedan sin rastrear/ignorados otros archivos de `target/` (p. ej. el jar que generé con `mvn package`). Recomiendo sacar `target/` del control de versiones en otra tarea.
- No se marcó ningún hallazgo como RESUELTO en documentos ni se editó `docs/**`; el coordinador debe re-ejecutar las pruebas.

## 6. Recomendación: **ready-for-review**

`git diff --stat` (archivos rastreados modificados; los nuevos están sin rastrear):

```
 audit-service/backend/pom.xml                      | 23 +++++++
 .../backend/src/main/resources/application.yml     |  6 ++
 .../port/in/AuthorizePaymentUseCase.java           | 12 +++-
 .../service/PaymentAuthorizationService.java       | 24 ++++++-
 .../adapter/in/rest/CategoryController.java        |  4 +-
 .../adapter/in/rest/PaymentController.java         | 17 +++--
 .../service/PaymentAuthorizationServiceTest.java   | 75 +++++++++++++++++++++-
 docker-compose.yml                                 |  2 +
 8 files changed, 150 insertions(+), 13 deletions(-)
```

Archivos nuevos sin rastrear (`git status --short`):
- `audit-service/backend/src/main/java/com/riwi/audit/config/SecurityConfig.java`
- `audit-service/backend/src/main/java/com/riwi/audit/security/{JwtTokenVerifier,BearerJwtAuthenticationFilter}.java`
- `audit-service/backend/src/test/java/com/riwi/audit/controller/AuditControllerSecurityTest.java`
- `audit-service/backend/src/test/java/com/riwi/audit/security/JwtTokenVerifierTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/{CategoryControllerSecurityTest,PaymentControllerSecurityTest}.java`

Todos dentro del alcance autorizado. Puntos de revisión sugeridos: (1) refuerzo clave↔reserva en el servicio; (2) default de `JWT_SECRET` en compose; (3) impacto UI de SEC-005.
