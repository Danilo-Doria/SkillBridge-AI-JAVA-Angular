# HU29 — Implementación y guía para la daily

## Entregado

1. `audit-service` expone `GET /api/audit/events/correlation/{correlationId}`.
2. MongoDB crea el índice compuesto `correlationId_occurredAt_idx`.
3. `mcp-server` usa Java 21, SDK MCP oficial y transporte `stdio`.
4. Catálogo cerrado: `audit_by_correlation`, `booking_status`, `queue_status`, `ai_providers_status`.
5. Las herramientas están marcadas como read-only, no destructivas e idempotentes.
6. `TriageAgent` rechaza herramientas no descubiertas y limita `MCP_MAX_STEPS` a 1–8.
7. Se redactan `email`, `token`, `authorization`, `zoomPasscode`, `password` y `cardNumber`.
8. Se detectan citas `eventId`/`correlationId` para impedir diagnósticos sin evidencia.
9. El token se propaga por `MCP_BEARER_TOKEN`; no hay credenciales propias del servidor.

## Cómo ejecutar

```bash
cd mcp-server
AUDIT_SERVICE_URL=http://localhost:8090 MCP_BEARER_TOKEN="$JWT" mvn spring-boot:run
```

## Explicación para la daily

“Primero añadimos trazabilidad por `correlationId` al audit-service. Luego encapsulamos esa consulta en un servidor MCP independiente con transporte stdio. El catálogo es cerrado y de solo lectura: el agente no puede inventar herramientas ni modificar estado. Finalmente añadimos redacción de datos sensibles y límites de pasos para controlar coste y riesgo.”

## Pendientes de integración

Los endpoints de booking, cola y proveedores de IA deben apuntar a los contratos reales cuando estén disponibles. El adaptador falla explícitamente ante HTTP 4xx/5xx; no fabrica evidencia. La prueba de utilidad con escenarios sembrados y la comparación contra el runbook fijo deben ejecutarse con los servicios levantados.
