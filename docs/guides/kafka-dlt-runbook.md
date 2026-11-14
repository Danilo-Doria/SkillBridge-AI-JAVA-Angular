# Runbook: Dead Letter Topics de Kafka (pagos y notificaciones)

Ámbito: HU-104 US4. Contrato: `specs/104-microservices-gateway/contracts/kafka-topics.md`.
Código: `backend/.../infrastructure/config/KafkaMessagingConfiguration.java`,
`adapter/in/messaging/KafkaPaymentResultConsumer.java`, `adapter/in/messaging/KafkaNotificationConsumer.java`.

## 1. Qué llega a una DLT y por qué

| Topic origen | DLT | Grupo consumidor (defecto) | Efecto del consumidor |
|---|---|---|---|
| `payment-events` | `payment-events.DLT` | `skillbridge-booking-payments` | `PaymentApproved` → reserva `CREATED` → `CONFIRMED` |
| `notification-requests` | `notification-requests.DLT` | `skillbridge-notifications` | envía la notificación (`NotificationSenderPort`) |

Un registro acaba en `<topic>.DLT` cuando:

1. **Falla transitoria agotada**: el listener lanzó una excepción y fallaron también los 3 reintentos
   (`ExponentialBackOff`: 1 s, 2 s, 4 s; propiedades `app.messaging.kafka.retry.*`).
2. **Falla determinista** (sin reintentos): `BusinessRuleException` (p. ej. la reserva ya no está en `CREATED`),
   `DomainNotFoundException` (reserva inexistente), `PermanentNotificationException`, `IllegalArgumentException`
   (payload incompleto o ilegible).
3. **Mensaje no deserializable** (JSON roto, otro formato): `ErrorHandlingDeserializer` lo desvía sin bloquear la
   partición; en la DLT se guardan los **bytes originales**.

Los duplicados (mismo `eventId`) **no** van a la DLT: se descartan con un `WARN Duplicate event skipped`
gracias a la tabla `processed_events` (PK `consumer_group, event_id`).

## 2. Qué mirar

Cabeceras de cada registro en la DLT (las añade `DeadLetterPublishingRecoverer`; se conservan las originales):

| Cabecera | Uso |
|---|---|
| `X-Correlation-Id` | buscar la traza completa en logs (`[<correlationId>]` en el patrón de log) |
| `eventId`, `eventType`, `schemaVersion` | identificar el evento y comprobar si ya se procesó |
| `kafka_dlt-exception-fqcn`, `kafka_dlt-exception-message`, `kafka_dlt-exception-stacktrace` | causa |
| `kafka_dlt-original-topic`, `kafka_dlt-original-partition`, `kafka_dlt-original-offset` | posición de origen |

```bash
# Contar mensajes en la DLT (contenedor kafka del docker-compose)
docker compose exec kafka kafka-run-class kafka.tools.GetOffsetShell \
  --bootstrap-server localhost:9092 --topic payment-events.DLT

# Leer con cabeceras
docker compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payment-events.DLT --from-beginning --property print.headers=true \
  --property print.key=true --timeout-ms 10000
```

En logs del backend: `ERROR ... KafkaMessagingConfiguration - Sending to DLT topic=... key=... cause=...`.
Métrica de fallos de **publicación** (equivalente al antiguo "mensaje no enrutable" de RabbitMQ):
`skillbridge_messaging_publish_failures_total{topic,eventType}` en `/actuator/prometheus`.

¿Ya se aplicó el efecto?

```sql
SELECT * FROM processed_events WHERE event_id = '<eventId>';
SELECT id, status FROM bookings WHERE id = '<key del registro = bookingId>';
```

## 3. Decidir

| Causa | Acción |
|---|---|
| Transitoria (BD caída, SMTP caído, timeout) ya resuelta | **Reinyectar** (§4) |
| `BusinessRuleException` "no esta en estado CREATED" en `PaymentApproved` | Normalmente la reserva ya está `CONFIRMED`/`CANCELLED` por otra vía: **descartar** tras verificar el estado; si estaba `CANCELLED`, abrir incidencia de reembolso |
| `DomainNotFoundException` | Reserva inexistente: investigar el productor; no reinyectar |
| `PermanentNotificationException` | Destinatario inválido: corregir el dato y reinyectar, o descartar |
| Deserialización / `IllegalArgumentException` | Productor con contrato roto: corregir el productor; no reinyectar los bytes tal cual |

## 4. Reinyectar

Reinyectar = volver a publicar el **mismo valor y la misma clave** en el topic origen. Es seguro repetirlo:
el consumidor es idempotente por `eventId` y un intento fallido no deja marca en `processed_events`
(la marca y el efecto comparten transacción).

```bash
# 1) Exportar clave|valor de la DLT (revisar el fichero antes de reinyectar)
docker compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payment-events.DLT --from-beginning --timeout-ms 10000 \
  --property print.key=true --property key.separator='|' > dlt-payment-events.txt

# 2) Filtrar solo las líneas a reinyectar y publicarlas en el topic origen (misma clave = misma partición)
docker compose exec -T kafka kafka-console-producer --bootstrap-server localhost:9092 \
  --topic payment-events --property parse.key=true --property key.separator='|' < dlt-payment-events.txt
```

Notas:
- `kafka-console-producer` no copia cabeceras: el consumidor recupera la correlación del campo `correlationId`
  del envelope (fallback previsto). Si se necesita la cabecera, usar `kcat -H "X-Correlation-Id=..."`.
- La DLT no se vacía al reinyectar (es un log). Anotar el último offset tratado en la incidencia para no repetir.
- Retención recomendada de las DLT: 7 días (`retention.ms=604800000`); ver fragmento de Compose en T039.

## 5. Volver a RabbitMQ (rollback de la transición)

Mientras no se ejecute T038, cada flujo puede volver a RabbitMQ sin cambiar código:
`MESSAGING_PAYMENTS=rabbit` y/o `MESSAGING_NOTIFICATIONS=rabbit` (con RabbitMQ levantado y `RABBITMQ_URL`).
Los mensajes que queden en los topics Kafka no se migran: drenarlos antes (lag 0 en los grupos) con
`kafka-consumer-groups --describe --group skillbridge-booking-payments`.
