# WP-F — Native, CI/CD ARM y medición (tareas T070-T075) — se lanza en W5
**Léelo junto con**: `CLAUDE.md`, `AGENTS.md`, `specs/104-microservices-gateway/{spec.md,plan.md,tasks.md}` y `contracts/`. Reglas: no commit/stage, no cambiar de rama, no secretos/`.env`, no editar `docker-compose*.yml` (devuelve el fragmento de servicio en tu informe), no acciones externas. Reporta solo lo que ejecutaste; si no, "No ejecutado en esta sesión". Informe final ≤ 30 líneas: archivos tocados, comandos y resultado real, pendientes.

## Scope
`.github/workflows/**` y, **por módulo**, el `pom.xml` + hints de reflexión (`src/main/resources/META-INF/native-image/**` o `RuntimeHintsRegistrar`) del módulo que se te asigne (uno por agente: `backend`, `audit-service/backend`, `ai-service`, `notification-service`, `payment-service`).
**Forbidden**: lógica de negocio, `docker-compose*.yml`.

## Entregables
1. Perfil `native` por módulo (Spring Boot 3.5 AOT + `native-maven-plugin`), build para `linux/arm64`.
2. `.github/workflows/build-native-arm64.yml` (runner ARM de GitHub; repo público verificado) publica a GHCR con tags `sha` y `latest`; `deploy-oracle.yml` por SSH con `pull && up -d`, comprobación de salud y rollback. Secretos solo por `secrets.*`.
3. Spike por módulo: intenta `-Pnative` localmente si hay recursos (≥ 8 GB libres y tiempo razonable); si no, deja el perfil + hints y marca "No ejecutado en esta sesión".
4. Criterio de salida: módulo que no funcione en native queda en JVM afinada con razón en `research.md` (tabla del spike), **no es fallo**.
5. Medición de RAM en reposo si se pudo levantar la imagen.

## Acceptance criteria
- Workflows válidos (`actionlint` si está disponible) y sin secretos en claro.
- Cada módulo: perfil native presente **o** excepción documentada.
- Resultados del spike en `research.md` con comando y salida reales (o "No ejecutado").
- `mvn test` del módulo sigue verde (ejecutado y reportado).
