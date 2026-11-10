#!/usr/bin/env bash
set -euo pipefail

# Script para crear las Historias de Usuario de migración a NestJS como issues en GitHub
# y agregarlas al GitHub Project #1 de Danilo-Doria.

REPO="Danilo-Doria/SkillBridge-AI-JAVA-Angular"
PROJECT_NUMBER=1
OWNER="Danilo-Doria"

# Verificar autenticación de gh
if ! gh auth status &>/dev/null; then
  echo "Error: gh no está autenticado."
  echo "Ejecuta: 'gh auth login' o define 'export GH_TOKEN=tu_token' antes de ejecutar este script."
  echo "Asegúrate de que el token incluya los permisos 'repo' y 'project'."
  exit 1
fi

echo "Iniciando creación de issues en $REPO y vinculación al Proyecto #$PROJECT_NUMBER..."

create_and_link_issue() {
  local title="$1"
  local body="$2"

  echo "------------------------------------------------------------"
  echo "Creando issue: $title"
  issue_url=$(gh issue create --repo "$REPO" --title "$title" --body "$body")
  echo "Issue creada exitosamente: $issue_url"

  echo "Vinculando issue al GitHub Project #$PROJECT_NUMBER..."
  if gh project item-add "$PROJECT_NUMBER" --owner "$OWNER" --url "$issue_url"; then
    echo "Vinculada al proyecto exitosamente."
  else
    echo "Advertencia: No se pudo vincular al proyecto automáticamente. Verifica permisos de 'project' en tu token con 'gh auth refresh -s project'."
  fi
}

# -------------------------------------------------------------
# HU-MNT-01
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-01] Configuración base del microservicio y modelo relacional aislado (NestJS + Prisma)" \
"## Historia de usuario
**Como** desarrollador backend de NestJS,
**quiero** inicializar el proyecto \`mentorship-service\` con validación de configuración estricta y base de datos PostgreSQL propia con Prisma,
**para** garantizar aislamiento físico y persistencia relacional sin dependencias directas de esquemas ajenos.

## Contexto y Aislamiento (Database-per-Service)
- Microservicio independiente alojado en \`mentorship-service/\` dentro del Monorepo.
- Base de datos relacional propia (\`mentorship_db\`).
- Sin constraints \`FOREIGN KEY\` hacia bases de datos de otros servicios (\`users\`, \`bookings\`, \`offerings\`). Los identificadores se guardan como UUIDs indexados.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Validación estricta de variables de entorno al arrancar
  Dado que el servicio inicia
  Cuando falte alguna variable obligatoria (DATABASE_URL, JWT_PUBLIC_KEY, RABBITMQ_URL, KAFKA_BROKERS)
  Entonces el proceso debe abortar el arranque de inmediato arrojando un error descriptivo con Joi/class-validator.

Escenario: Creación de esquema y restricciones relacionales
  Dado el esquema de Prisma en mentorship_db
  Cuando se ejecutan las migraciones
  Entonces se crean las tablas mentorship_assignments, mentorship_resources y outbox_events
  Y mentorship_assignments tiene una restricción única sobre booking_id
  Y los campos customer_id, offering_id y booking_id se indexan sin claves foráneas a tablas externas.
\`\`\`

## Tareas técnicas
- [ ] Inicializar proyecto NestJS con TypeScript estricto.
- [ ] Configurar \`@nestjs/config\` con esquema Joi de variables obligatorias.
- [ ] Configurar Prisma con PostgreSQL y definir modelos (\`MentorshipAssignment\`, \`MentorshipResource\`, \`OutboxEvent\`).
- [ ] Configurar healthcheck (\`GET /health\`) validando PostgreSQL.
- [ ] Configurar Dockerfile multi-stage y entrada en \`docker-compose.yml\`.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

# -------------------------------------------------------------
# HU-MNT-02
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-02] Aprovisionamiento asíncrono e idempotente por Event-Carried State Transfer (RabbitMQ)" \
"## Historia de usuario
**Como** sistema SkillBridge,
**quiero** consumir el evento \`PaymentApproved\` desde RabbitMQ y aprovisionar la mentoría usando la información contenida en el mensaje,
**para** dar acceso inmediato al estudiante sin realizar peticiones síncronas a otros microservicios.

## Contexto y Desacoplamiento (ECST)
- El evento de pago llega a la cola \`mentorship.payment.approved.queue\` (Exchange: \`payment.events\`, key: \`payment.approved\`).
- El mensaje transporta el estado completo: \`bookingId\`, \`customerId\`, \`customerEmail\`, \`offeringId\`, \`offeringTitle\`, \`providerId\`, \`scheduledAt\`, \`durationHours\`, \`mode\`.
- Cero llamadas HTTP síncronas durante el aprovisionamiento.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Aprovisionamiento exitoso sin dependencias HTTP
  Dado un mensaje en la cola mentorship.payment.approved.queue con el payload enriquecido
  Cuando el consumidor procesa el evento
  Entonces crea la asignación en estado SCHEDULED, modo ASYNCHRONOUS o LIVE según corresponda, progreso 0%
  Y establece la ventana de acceso [scheduledAt, scheduledAt + durationHours]
  Y confirma el mensaje (ACK) a RabbitMQ.

Escenario: Idempotencia ante mensajes duplicados o reintentos
  Dado un evento recibido cuyo booking_id ya existe en mentorship_assignments
  Cuando el consumidor intente procesarlo
  Entonces detecta el registro existente, confirma el mensaje con ACK y no genera registros duplicados ni errores 500.

Escenario: Fallo no recuperable a Dead Letter Queue
  Dado un mensaje corrupto o con datos estructuralmente inválidos
  Cuando falla tras 3 reintentos exponenciales
  Entonces el mensaje se enruta a mentorship.payment.dlq para inspección y auditoría.
\`\`\`

## Tareas técnicas
- [ ] Implementar módulo RabbitMQ con \`amqplib\` o \`@golevelup/nestjs-rabbitmq\`.
- [ ] Configurar colas, bindings y políticas de reintentos exponenciales y DLQ.
- [ ] Implementar handler idempotente con transacción Prisma.
- [ ] Pruebas unitarias de mensajes duplicados y concurrencia.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

# -------------------------------------------------------------
# HU-MNT-03
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-03] Consulta y gestión del ciclo de vida y progreso (Regla 1 Pago = 1 Mentoría)" \
"## Historia de usuario
**Como** estudiante autenticado,
**quiero** consultar mis mentorías contratadas y reportar mi porcentaje de avance,
**para** registrar mi aprendizaje y culminar el servicio contratado.

## Regla de Oro
- **1 Pago = 1 Mentoría:** Cada pago aprobado habilita una única asignación.
- Al llegar al 100% (\`COMPLETED\`), el pago queda consumido y no puede reiniciarse.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Consulta de mentorías propias
  Dado un usuario con rol CUSTOMER autenticado mediante JWT
  Cuando realiza GET /api/v1/mentorships/me
  Entonces responde código 200 con la lista de sus asignaciones ordenadas por accessStartsAt descendente.

Escenario: Actualización de progreso intermedio
  Dado un estudiante con mentoría activa dentro de su ventana de horario
  Cuando envía PATCH /api/v1/mentorships/:id/progress con { \"progressPercent\": 60 }
  Entonces el estado pasa a IN_PROGRESS y actualiza el porcentaje.

Escenario: Finalización irreversible de la mentoría (pago consumido)
  Dado un estudiante dentro de su ventana de acceso
  Cuando envía PATCH /api/v1/mentorships/:id/progress con { \"progressPercent\": 100 }
  Entonces el estado pasa a COMPLETED, se registra completedAt = now()
  Y cualquier intento posterior de reiniciar el progreso es rechazado con 400 Bad Request.
\`\`\`

## Tareas técnicas
- [ ] Crear endpoints \`GET /api/v1/mentorships/me\` y \`PATCH /api/v1/mentorships/:id/progress\`.
- [ ] Implementar \`JwtAuthGuard\` extrayendo identidad de los claims JWT validados con \`JWT_PUBLIC_KEY\`.
- [ ] DTO con validación \`@Min(0)\` y \`@Max(100)\`.
- [ ] Reglas de bloqueo terminal para estados \`COMPLETED\`, \`CANCELLED\` y \`NO_SHOW\`.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

# -------------------------------------------------------------
# HU-MNT-04
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-04] Control de acceso estricto en servidor para aula virtual" \
"## Historia de usuario
**Como** estudiante autenticado,
**quiero** solicitar la autorización de acceso a mi sesión de mentoría,
**para** ingresar al aula o sala únicamente cuando mi pago, fecha y estado sean válidos.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Acceso permitido dentro de la ventana horaria
  Dado un estudiante con asignación propia activa
  Cuando hace GET /api/v1/mentorships/:id/access y se cumple accessStartsAt <= now < accessEndsAt
  Entonces responde 200 OK con assignmentId, mode, accessEndsAt y datos de conexión de la sala.

Escenario: Acceso rechazado por ventana aún no iniciada
  Dado un estudiante con asignación válida
  Cuando intenta acceder antes de accessStartsAt
  Entonces responde 403 Forbidden indicando \"La mentoría aún no está disponible\".

Escenario: Acceso bloqueado por mentoría vencida o completada
  Dado un estudiante intentando acceder cuando now >= accessEndsAt o estado es COMPLETED o CANCELLED
  Entonces responde 403 Forbidden indicando \"El acceso a esta mentoría ya no está habilitado\".
\`\`\`

## Tareas técnicas
- [ ] Endpoint \`GET /api/v1/mentorships/:id/access\`.
- [ ] Verificación estricta de límites temporales en servidor (\`accessStartsAt <= now < accessEndsAt\`).
- [ ] Aislamiento de errores: Retornar \`404\` ante mentorías de otros usuarios para no filtrar UUIDs.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

# -------------------------------------------------------------
# HU-MNT-05
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-05] Administración de recursos didácticos por enlace (URL)" \
"## Historia de usuario
**Como** Provider propietario del servicio (o Admin),
**quiero** crear, editar, listar y eliminar enlaces de estudio asociados a un offering,
**para** suministrar material complementario a los estudiantes.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Validación de pertenencia al crear recurso
  Dado un usuario autenticado con rol PROVIDER
  Cuando envía POST /api/v1/mentorship-resources/offerings/:offeringId
  Entonces valida que el providerId del offering coincida con su identidad (o sea ADMIN)
  Y verifica que title no supere 160 caracteres y url inicie con http:// o https://.

Escenario: Visualización de recursos para alumno autorizado
  Dado un estudiante con mentoría vigente para dicho offering
  Cuando realiza GET /api/v1/mentorships/:id/resources
  Entonces recibe los recursos ordenados por fecha de publicación.
\`\`\`

## Tareas técnicas
- [ ] CRUD completo en \`/api/v1/mentorship-resources\`.
- [ ] Validaciones de URL vía \`class-validator\` (\`@IsUrl()\`).
- [ ] Control de roles con \`@Roles('PROVIDER', 'ADMIN')\`.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

# -------------------------------------------------------------
# HU-MNT-06
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-06] Carga y descarga protegida de archivos (Storage Port: S3 / Local)" \
"## Historia de usuario
**Como** Provider y estudiante autorizado,
**quiero** subir guías de estudio (hasta 10 MB) y descargarlas de forma segura,
**para** compartir material descargable sin exponer enlaces públicos directos.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Subida de archivo por Provider
  Dado un Provider dueño del offering
  Cuando sube un archivo vía POST /api/v1/mentorship-resources/offerings/:offeringId/file (multipart/form-data)
  Entonces el backend valida tamaño <= 10 MB, sanitiza el nombre y delega al StorageAdapter (S3/Local)
  Y almacena el recurso con resourceType = DOCUMENT.

Escenario: Descarga autorizada para alumno
  Dado un estudiante con asignación activa dentro de su ventana temporal
  Cuando realiza GET /api/v1/mentorship-resources/:id/file
  Entonces el servidor retorna el stream del archivo con headers:
    - Content-Disposition: attachment; filename=\"...\"
    - X-Content-Type-Options: nosniff
    - Cache-Control: no-store.

Escenario: Descarga bloqueada para usuarios sin acceso
  Dado un usuario anónimo o un estudiante fuera de su ventana de mentoría
  Cuando intenta descargar el archivo por su URL directa
  Entonces responde 403 Forbidden.
\`\`\`

## Tareas técnicas
- [ ] Definir \`StoragePort\` con métodos \`upload\`, \`downloadStream\` y \`delete\`.
- [ ] Implementar \`LocalStorageAdapter\` y \`S3StorageAdapter\`.
- [ ] Endpoint de subida con \`FileInterceptor\` y límite de 10 MB.
- [ ] Endpoint de descarga segura con validación de ventana temporal de la mentoría.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

# -------------------------------------------------------------
# HU-MNT-07
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-07] Mentorías en vivo multi-proveedor (Zoom, Google Meet, Discord)" \
"## Historia de usuario
**Como** Provider de una mentoría sincrónica,
**quiero** generar la sala de reunión seleccionando entre Zoom, Google Meet o Discord,
**para** impartir la sesión en la plataforma elegida de manera segura.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Creación de sala Zoom
  Dado una mentoría LIVE
  Cuando el Provider solicita creación con liveProvider = ZOOM
  Entonces interactúa con la API de Zoom OAuth, crea la reunión y almacena meetingId, passcode y joinUrl.

Escenario: Creación de sala Google Meet
  Dado una mentoría LIVE
  Cuando el Provider solicita creación con liveProvider = GOOGLE_MEET
  Entonces interactúa con Google Calendar/Workspace API y genera el enlace seguro meet.google.com/xxx-yyyy-zzz.

Escenario: Creación de canal/invitación Discord
  Dado una mentoría LIVE
  Cuando el Provider solicita creación con liveProvider = DISCORD
  Entonces interactúa con la API del Bot de Discord y genera un canal de voz temporal o invitación de un solo uso.

Escenario: Idempotencia en creación de sesiones en vivo
  Dado que una mentoría ya tiene una sala configurada
  Cuando se vuelve a invocar la creación
  Entonces retorna la sesión existente sin duplicar reuniones en los proveedores externos.
\`\`\`

## Tareas técnicas
- [ ] Definir interfaz \`LiveMeetingPort\`.
- [ ] Implementar \`ZoomAdapter\` con generación de firma efímera HMAC-SHA256 para el Web SDK.
- [ ] Implementar \`GoogleMeetAdapter\` con Google Calendar API.
- [ ] Implementar \`DiscordAdapter\` con Discord Bot API.
- [ ] Endpoint \`POST /api/v1/mentorships/:id/live-session\` idempotente.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

# -------------------------------------------------------------
# HU-MNT-08
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-08] Publicación confiable de auditoría a Apache Kafka (Transactional Outbox)" \
"## Historia de usuario
**Como** oficial de auditoría de la plataforma,
**quiero** que todos los eventos de dominio de mentorías se publiquen en el topic \`booking-events\` de Kafka bajo el envelope \`BusinessEvent<T>\`,
**para** que el microservicio de auditoría (\`audit-service\`) persista el historial sin acoplamiento temporal.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Persistencia atómica en tabla outbox
  Dado un evento relevante (MentorshipProvisioned, MentorshipCompleted, MentorshipCancelled)
  Cuando se procesa la transacción de la mentoría
  Entonces el registro en outbox_events se guarda dentro de la misma transacción ACID de PostgreSQL.

Escenario: Publicación asíncrona hacia Kafka
  Dado el worker en segundo plano de NestJS
  Cuando detecta registros PENDING en outbox_events
  Entonces publica en Kafka en el topic booking-events con el envelope BusinessEvent<T>
  Y marca el registro como PUBLISHED.

Escenario: Tolerancia a caídas de Kafka
  Dado que el cluster de Kafka no responde
  Cuando el worker intente publicar
  Entonces el worker reintenta con retroceso exponencial sin arrojar errores al usuario final.
\`\`\`

## Tareas técnicas
- [ ] Crear tabla \`outbox_events\` en Prisma.
- [ ] Implementar \`KafkaProducerService\` con \`kafkajs\` en topic \`booking-events\`.
- [ ] Implementar cron / worker de despacho (\`@nestjs/schedule\` o poller dedicado).
- [ ] Serialización con envelope estándar \`BusinessEvent<T>\`.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

# -------------------------------------------------------------
# HU-MNT-09
# -------------------------------------------------------------
create_and_link_issue \
"[HU-MNT-09] Resiliencia ante fallos de proveedores en vivo (Circuit Breaker y Fallback)" \
"## Historia de usuario
**Como** Provider o estudiante,
**quiero** disponer de alternativas si la API de Zoom, Meet o Discord falla o tarda en responder,
**para** que la sesión de mentoría no quede bloqueada.

## Criterios de Aceptación (Gherkin)
\`\`\`gherkin
Escenario: Fallback ante timeout de Zoom/Meet/Discord
  Dado que una API externa tarda más de 4 segundos o responde con error 5xx
  Cuando el Circuit Breaker interrumpe la llamada
  Entonces el sistema notifica el fallo al mentor y le permite ingresar una URL alternativa o cambiar de proveedor.

Escenario: Autonomía de lectura de acceso
  Dado un estudiante que consulta GET /api/v1/mentorships/:id/access
  Cuando la sala ya fue creada previamente
  Entonces los datos de conexión se entregan directamente desde PostgreSQL local sin consultar a las APIs externas.
\`\`\`

## Tareas técnicas
- [ ] Configurar circuit breaker (bap/opossum o interceptor resiliente) en adaptadores de videollamada.
- [ ] Permitir ingreso de \`CUSTOM_URL\` como fallback inmediato.
- [ ] Garantizar lectura local 100% desacoplada en el endpoint de acceso.

Ref: Documento docs/HISTORIAS-USUARIO-NESTJS.md"

echo "============================================================"
echo "Todas las Historias de Usuario fueron creadas y vinculadas exitosamente."
