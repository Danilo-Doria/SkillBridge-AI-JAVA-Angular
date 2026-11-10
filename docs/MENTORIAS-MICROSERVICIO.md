# Documentación de mentorías para migración a microservicio

## 1. Propósito

Esta documentación describe el comportamiento implementado en la rama
`feature/Plataforma-mentorias`, comparada con `develop`, para reconstruirlo en
otra tecnología como un microservicio consumible por API.

La rama contiene tres entregas:

1. Aprovisionamiento automático de una mentoría cuando una reserva queda pagada.
2. Creación, edición, eliminación y descarga protegida de recursos de mentoría.
3. Ajustes del frontend para edición de recursos, carga de archivos y navegación
   condicionada por autenticación.

Commits incluidos:

- `0694888 feat: edit and upload mentorship resources`
- `9395546 feat: provision paid mentorships automatically`
- `b5993ca Ajustar navegación y edición de recursos`

## 2. Alcance funcional

Una mentoría pertenece funcionalmente a una reserva (`booking`) pagada y a un
servicio (`offering`). La relación principal es:

```text
booking confirmado/pagado -> mentorship_assignment -> offering -> resources
             |                         |
          customer                  ventana de acceso
```

El cliente puede consultar sus mentorías, acceder durante la ventana permitida,
ver sus recursos y actualizar el progreso. Un `PROVIDER` dueño del servicio o
un `ADMIN` puede administrar la asignación y sus recursos.

## 3. Dependencias con otros módulos

El microservicio debe integrarse con:

- **Identidad/autenticación:** usuario autenticado, `userId`, correo y rol
  (`CUSTOMER`, `PROVIDER`, `ADMIN`). En la implementación actual Spring obtiene
  el usuario a partir del correo del token.
- **Bookings:** `bookingId`, `customerId`, `offeringId`, fecha programada y
  estado. Solo una reserva `CONFIRMED` puede generar una mentoría.
- **Offerings:** propietario del servicio (`providerId`) y título. Se usa para
  autorizar proveedores y para nombrar reuniones Zoom.
- **Pagos/eventos:** evento `PaymentApproved` con `bookingId`.
- **Zoom:** creación de reuniones y generación de firma de participante para
  mentorías en vivo.
- **Almacenamiento de archivos:** actualmente los archivos se guardan como
  `BYTEA` en PostgreSQL; para el nuevo servicio se recomienda objeto externo
  (S3, GCS o equivalente) y guardar solo metadatos y la clave del objeto.

## 4. Modelo de datos

### 4.1 `mentorship_assignments`

Campos requeridos:

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador de la mentoría |
| `booking_id` | UUID, único | Reserva que originó la mentoría |
| `customer_id` | UUID | Estudiante |
| `offering_id` | UUID | Servicio contratado |
| `mode` | enum | `ASYNCHRONOUS` o `LIVE` |
| `status` | enum | `PENDING_PAYMENT`, `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `EXPIRED`, `NO_SHOW` |
| `access_starts_at` | timestamp | Inicio de acceso |
| `access_ends_at` | timestamp | Fin de acceso |
| `progress_percent` | integer | Progreso de 0 a 100 |
| `live_room_url` | string nullable | URL de sala o reunión |
| `zoom_meeting_id` | string nullable | ID de Zoom |
| `zoom_passcode` | string nullable | Código de Zoom |
| `completed_at` | timestamp nullable | Fecha de finalización |
| `version` | integer | Control de concurrencia optimista |

Debe existir una restricción única sobre `booking_id`, que hace idempotente la
creación de una mentoría por reserva.

### 4.2 `mentorship_resources`

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador del recurso |
| `offering_id` | UUID | Servicio al que pertenece |
| `title` | string, máximo 160 | Nombre visible |
| `resource_type` | enum | `VIDEO`, `DOCUMENT` o `LINK` |
| `url` | string, máximo 1500 | URL externa o URL interna de descarga |
| `created_at` | timestamp | Orden de publicación |
| `file_data` | bytes nullable | Archivo en la implementación actual |
| `file_name` | string nullable, máximo 255 | Nombre original sanitizado |

La migración agregada por esta rama es `V11__resource_files.sql`, con
`file_data BYTEA` y `file_name VARCHAR(255)`.

## 5. Contrato HTTP de mentorías

Todos los endpoints requieren autenticación salvo que el gateway defina otra
política. Las respuestas de error deben conservar, como mínimo, los códigos
`400`, `403` y `404` según el caso.

### 5.1 Consultar mentorías propias

`GET /api/mentorships/me`

- Rol: usuario autenticado.
- Antes de responder, el sistema busca reservas confirmadas del cliente y crea
  las mentorías faltantes.
- Orden: `accessStartsAt` descendente.
- Respuesta: lista de asignaciones.

### 5.2 Crear/asignar una mentoría manualmente

`POST /api/mentorships`

Roles: `PROVIDER` o `ADMIN`.

```json
{
  "bookingId": "uuid",
  "mode": "ASYNCHRONOUS",
  "accessStartsAt": "2026-11-05T15:00:00Z",
  "accessEndsAt": "2026-11-05T17:00:00Z",
  "liveRoomUrl": ""
}
```

Reglas: la fecha final debe ser posterior a la inicial; la reserva debe existir,
estar `CONFIRMED` y no tener otra asignación; un proveedor solo puede operar
sobre sus propios servicios. La asignación inicia en `SCHEDULED` y con progreso
`0`.

### 5.3 Consultar acceso vigente

`GET /api/mentorships/{id}/access`

- Solo el cliente dueño de la asignación.
- Rechaza mentorías completadas, canceladas, `NO_SHOW`, vencidas o aún no
  iniciadas.
- Respuesta mínima:

```json
{
  "assignmentId": "uuid",
  "mode": "LIVE",
  "accessEndsAt": "2026-11-05T17:00:00Z",
  "liveRoomUrl": "https://...",
  "zoomMeetingId": "...",
  "zoomPasscode": "...",
  "zoomSdkKey": "...",
  "zoomSignature": "..."
}
```

Los campos Zoom solo se devuelven para una mentoría `LIVE` con reunión creada.

### 5.4 Recursos visibles para una mentoría

`GET /api/mentorships/{id}/resources`

- Solo el cliente dueño.
- Requiere que la ventana de acceso esté activa.
- No permite acceso si el estado es `COMPLETED`, `CANCELLED` o `NO_SHOW`.
- Devuelve los recursos del `offering`, ordenados por `createdAt` ascendente.

### 5.5 Actualizar progreso

`PATCH /api/mentorships/{id}/progress`

```json
{ "progressPercent": 75 }
```

- Solo el cliente dueño y dentro de la ventana de acceso.
- Rango válido: `0..100`.
- Un valor menor que `100` deja el estado en `IN_PROGRESS`.
- `100` cambia el estado a `COMPLETED` y registra `completedAt`.
- Una mentoría completada no puede reiniciarse con el mismo pago.

### 5.6 Crear reunión Zoom

`POST /api/mentorships/{id}/zoom-meeting`

- Roles: `ADMIN` o `PROVIDER` dueño del offering.
- Solo para modo `LIVE`.
- Es idempotente: si ya existe `zoomMeetingId`, devuelve la asignación sin
  crear otra reunión.
- Guarda `zoomMeetingId`, `zoomPasscode` y `liveRoomUrl`.

## 6. Contrato HTTP de recursos

Base: `/api/mentorship-resources`.

### Administración por proveedor/admin

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/offerings/{offeringId}` | Lista recursos del servicio |
| `POST` | `/offerings/{offeringId}` | Crea recurso por URL |
| `PUT` | `/{id}` | Edita título, tipo y URL |
| `DELETE` | `/{id}` | Elimina recurso |
| `POST` | `/offerings/{offeringId}/file` | Carga documento nuevo |
| `PUT` | `/{id}/file` | Reemplaza documento |

El administrador puede operar sobre cualquier offering. El proveedor solo sobre
uno cuyo `providerId` coincida con su usuario.

Payload JSON para URL:

```json
{
  "title": "Material de preparación",
  "resourceType": "LINK",
  "url": "https://ejemplo.com/material"
}
```

Reglas de URL: debe comenzar con `http://` o `https://` y tener host válido.
El título es obligatorio y admite máximo 160 caracteres. El tipo es obligatorio
y solo acepta `VIDEO`, `DOCUMENT` o `LINK`.

Carga multipart:

- `Content-Type: multipart/form-data`.
- Campo `title` obligatorio.
- Parte `file` obligatoria.
- Tamaño máximo: 10 MB.
- Archivo vacío: rechazado.
- Se sanitizan barras y saltos de línea del nombre original.
- El recurso queda como `DOCUMENT` y su URL pasa a ser
  `/api/mentorship-resources/{id}/file`.

### Descarga protegida

`GET /api/mentorship-resources/{id}/file`

- `ADMIN` y proveedor dueño: permitido.
- Cliente: permitido únicamente si tiene una asignación del mismo offering,
  está dentro de `accessStartsAt <= now < accessEndsAt` y el estado no es
  `COMPLETED`, `CANCELLED` ni `NO_SHOW`.
- Sin archivo: `404`.
- Respuesta binaria con `Content-Disposition: attachment`, `nosniff` y
  `Cache-Control: no-store`.

## 7. Aprovisionamiento automático por pago

El consumidor de `PaymentApproved` ejecuta dos pasos:

1. Procesa la aprobación del pago existente.
2. Llama a `provisionForPaidBooking(bookingId)`.

El aprovisionamiento verifica que la reserva exista, esté `CONFIRMED` y que no
exista una asignación para su `bookingId`. Si todo es válido crea:

- `mode = ASYNCHRONOUS`
- `status = SCHEDULED`
- `accessStartsAt = booking.scheduledAt`
- `accessEndsAt = booking.scheduledAt + 2 horas`
- `progressPercent = 0`
- `liveRoomUrl = null`

El evento debe procesarse de forma idempotente. Si el evento se reintenta, la
restricción única y la comprobación previa evitan duplicados.

Además, `GET /api/mentorships/me` ejecuta una reconciliación para reservas
confirmadas anteriores a esta funcionalidad. Esto permite migrar datos
históricos sin depender exclusivamente de reprocesar eventos antiguos.

Recomendación para el microservicio: separar la recepción del evento mediante
un endpoint/event consumer, registrar el `eventId` o `bookingId` procesado y
publicar un evento `MentorshipProvisioned` después de crear la asignación.

## 8. Cambios del frontend incluidos

- Los enlaces de navegación de reservar, IA, reservas, mentorías y gestión de
  servicios ahora solo se muestran cuando el usuario está autenticado.
- El servicio Angular de recursos soporta crear/editar por URL y crear/reemplazar
  por archivo multipart.
- Los documentos usan la URL interna de descarga cuando tienen `fileName`.
- La vista de mentorías carga los recursos mediante la URL protegida del archivo.
- La pantalla de offerings incorpora edición de recursos y carga de documentos
  dentro del flujo de creación/edición del servicio.
- Se corrigió la navegación/estado de edición para que el formulario mantenga
  recursos existentes, nuevos y archivos seleccionados antes de guardarlos.

## 9. Autorización y estados

La autorización no debe confiar únicamente en el frontend. El backend debe
validar siempre identidad, rol, propiedad del offering, propiedad de la
asignación y ventana temporal.

Estados terminales de acceso: `COMPLETED`, `CANCELLED`, `NO_SHOW`.
Una ventana de acceso válida usa límites inclusivo al inicio y exclusivo al
final: `start <= now < end`.

## 10. Pruebas existentes y pruebas recomendadas

La rama agrega `MentorshipResourceControllerTest`, que cubre principalmente:

- carga de archivos;
- validaciones de título/tamaño;
- actualización de recursos;
- descarga y autorización;
- eliminación de recursos.

Antes de liberar el microservicio se recomienda agregar pruebas de contrato para:

- evento de pago repetido;
- dos eventos concurrentes para la misma reserva;
- reserva no confirmada;
- reconciliación de reservas históricas;
- límites exactos de la ventana temporal;
- cambio de progreso a `100` y bloqueo posterior;
- proveedor intentando acceder a otro offering;
- cliente con asignación de otro offering;
- reemplazo de un archivo por otro;
- almacenamiento externo y URLs firmadas.

## 11. Consideraciones de migración tecnológica

1. Mantener UUIDs y nombres de campos para simplificar la migración de datos.
2. Migrar primero `mentorship_assignments` y `mentorship_resources`; después
   cambiar el consumidor de pagos al nuevo servicio.
3. Durante la transición, evitar que ambos servicios creen asignaciones sin una
   estrategia de ownership. Usar la restricción única o un lock distribuido.
4. No exponer `file_data` en respuestas JSON. Para archivos grandes, usar
   almacenamiento de objetos y URLs de descarga autorizadas o firmadas.
5. Centralizar la validación de JWT y propagar `userId`, `role` y, si aplica,
   `providerId` mediante claims confiables.
6. Documentar el contrato con OpenAPI y versionarlo, por ejemplo bajo
   `/api/v1/mentorships`.
7. Instrumentar métricas para aprovisionamientos exitosos, duplicados,
   rechazos por ventana y descargas de archivos.
8. Registrar auditoría para creación, edición, eliminación, descarga y cambio
   de progreso, sin escribir tokens ni firmas Zoom en logs.

## 12. Diferencias y decisiones a revisar

- La mentoría automática siempre se crea como `ASYNCHRONOUS`; la asignación
  manual permite `LIVE`.
- La duración automática está fija en dos horas. Conviene convertirla en una
  configuración o derivarla de la reserva/oferta.
- Los recursos se asocian al `offering`, no directamente a la asignación; por
  eso todas las mentorías del mismo servicio ven el mismo catálogo.
- Actualmente el archivo se almacena en PostgreSQL. Es una solución funcional
  para archivos pequeños, pero no ideal para escalar.
- `GET /api/mentorships/me` hace escritura (reconciliación). En un microservicio
  conviene mover esa reconciliación a un job o endpoint administrativo, dejando
  el GET como operación de lectura.
- La respuesta actual expone entidades de persistencia directamente. Para la
  nueva API se deben usar DTOs/versionado para evitar acoplar el contrato a la
  base de datos.

## 13. Diseño propuesto para Node.js + NestJS

### 13.1 Responsabilidad del microservicio

El servicio debe ser dueño de:

- asignaciones de mentoría;
- reglas de acceso y progreso;
- recursos asociados a un offering;
- procesamiento idempotente de pagos aprobados;
- integración opcional con Zoom;
- generación de URLs o permisos de descarga de archivos.

No debe ser dueño de usuarios, pagos, bookings ni offerings. Esos datos deben
consultarse mediante APIs internas, eventos o claims confiables. Para evitar una
dependencia síncrona en cada request, la asignación puede guardar los IDs
externos (`bookingId`, `customerId`, `offeringId`) y validar los datos necesarios
al crearla.

### 13.2 Estructura recomendada

```text
src/
├── main.ts
├── app.module.ts
├── config/
│   ├── configuration.ts
│   └── validation.ts
├── auth/
│   ├── auth.module.ts
│   ├── guards/jwt-auth.guard.ts
│   ├── guards/roles.guard.ts
│   └── decorators/current-user.decorator.ts
├── mentorships/
│   ├── mentorships.module.ts
│   ├── controllers/mentorships.controller.ts
│   ├── controllers/mentorship-admin.controller.ts
│   ├── dto/
│   ├── entities/mentorship-assignment.entity.ts
│   ├── repositories/mentorship-assignment.repository.ts
│   ├── services/mentorship-access.service.ts
│   ├── services/mentorship-provisioning.service.ts
│   └── services/mentorship-progress.service.ts
├── resources/
│   ├── resources.module.ts
│   ├── resources.controller.ts
│   ├── dto/
│   ├── entities/mentorship-resource.entity.ts
│   ├── services/resource-access.service.ts
│   └── storage/
│       ├── storage.port.ts
│       ├── s3-storage.adapter.ts
│       └── local-storage.adapter.ts
├── integrations/
│   ├── bookings/
│   ├── offerings/
│   ├── payments/
│   └── zoom/
├── messaging/
│   ├── messaging.module.ts
│   └── payment-approved.handler.ts
├── database/
│   ├── database.module.ts
│   └── migrations/
└── common/
    ├── filters/http-exception.filter.ts
    ├── interceptors/correlation-id.interceptor.ts
    └── pipes/validation.pipe.ts
```

La organización puede hacerse con TypeORM o Prisma. Para esta migración se
recomienda **Prisma + PostgreSQL** por su tipado, migraciones explícitas y
facilidad para generar repositorios. Si el equipo ya estandarizó TypeORM, el
modelo y los casos de uso son igualmente aplicables; lo importante es no
exponer el ORM desde los controllers.

### 13.3 Módulos NestJS

`MentorshipsModule` debe contener los casos de uso de asignación, consulta,
acceso, progreso y Zoom. `ResourcesModule` debe contener el catálogo de
recursos y el acceso a archivos. Ambos pueden compartir un módulo pequeño de
`OfferingOwnership`, que valida que un provider sea dueño del offering.

`MessagingModule` debe recibir `PaymentApproved` y llamar al caso de uso de
aprovisionamiento. La lógica no debe vivir dentro del handler de RabbitMQ; el
handler solo transforma el mensaje, propaga el `correlationId` y delega al
servicio de aplicación.

Ejemplo de composición:

```ts
@Module({
  imports: [DatabaseModule, AuthModule, ResourcesModule, IntegrationsModule],
  controllers: [MentorshipsController, MentorshipAdminController],
  providers: [
    MentorshipAccessService,
    MentorshipProvisioningService,
    MentorshipProgressService,
  ],
})
export class MentorshipsModule {}
```

### 13.4 Capas y flujo de una petición

Cada controller debe hacer solamente cuatro cosas: recibir la petición,
validar DTOs mediante `ValidationPipe`, obtener el usuario autenticado y
llamar un caso de uso. La secuencia recomendada es:

```text
HTTP Controller
  -> DTO + Guard
  -> Application Service / Use Case
  -> Repository o Integration Port
  -> DTO de respuesta
```

Los casos de uso deben contener las reglas de negocio: propiedad, estados,
ventana de acceso, idempotencia y transacciones. Los repositorios solo deben
persistir y consultar. Las integraciones externas deben estar detrás de
interfaces para poder probarlas con mocks.

## 14. API NestJS propuesta

Se recomienda versionar desde el inicio:

```ts
app.setGlobalPrefix('api');
app.enableVersioning({ type: VersioningType.URI });
```

El contrato quedaría bajo `/api/v1/mentorships` y
`/api/v1/mentorship-resources`. Los endpoints descritos en las secciones 5 y 6
se conservan, pero sus respuestas deben ser DTOs explícitos, no entidades de
Prisma/TypeORM.

DTOs mínimos:

```ts
export class CreateMentorshipDto {
  @IsUUID()
  bookingId: string;

  @IsEnum(MentorshipMode)
  mode: MentorshipMode;

  @IsDateString()
  accessStartsAt: string;

  @IsDateString()
  accessEndsAt: string;

  @IsOptional()
  @IsUrl()
  liveRoomUrl?: string;
}

export class UpdateProgressDto {
  @IsInt()
  @Min(0)
  @Max(100)
  progressPercent: number;
}
```

Configurar validación estricta para ignorar campos inesperados:

```ts
app.useGlobalPipes(new ValidationPipe({
  whitelist: true,
  forbidNonWhitelisted: true,
  transform: true,
}));
```

No usar `@Res()` en todos los controllers: dejar que Nest maneje respuestas
JSON y usar un interceptor/DTO para mantener un formato uniforme. Un formato
recomendado de error es:

```json
{
  "statusCode": 403,
  "code": "MENTORSHIP_ACCESS_DENIED",
  "message": "La mentoría no está disponible",
  "correlationId": "..."
}
```

## 15. Autenticación y autorización en NestJS

El gateway o el propio microservicio debe validar JWT. La información mínima
del token es `sub`, `role` y, si aplica, `email`. El `sub` debe convertirse en
`currentUser.id`; no se debe buscar el usuario por email como mecanismo
principal si el token ya contiene un identificador estable.

Implementar:

- `JwtAuthGuard` para validar firma, issuer, audience y expiración.
- `RolesGuard` para restricciones generales de `ADMIN` y `PROVIDER`.
- `CurrentUser` decorator para inyectar el actor.
- servicios de autorización para reglas que requieren consultar el offering o
  la asignación.

Un `RolesGuard` no reemplaza la autorización de ownership. Por ejemplo, un
provider con rol válido todavía debe comprobar que el `offering.providerId`
coincida con `currentUser.id`.

## 16. Persistencia con Prisma

El esquema debe conservar los nombres funcionales y la restricción única:

```prisma
model MentorshipAssignment {
  id              String   @id @db.Uuid
  bookingId       String   @unique @db.Uuid
  customerId      String   @db.Uuid
  offeringId      String   @db.Uuid
  mode            MentorshipMode
  status          MentorshipStatus
  accessStartsAt  DateTime
  accessEndsAt    DateTime
  progressPercent Int      @default(0)
  liveRoomUrl     String?
  zoomMeetingId   String?
  zoomPasscode    String?
  completedAt     DateTime?
  version         Int      @default(0)

  @@index([customerId, accessStartsAt(sort: Desc)])
}

model MentorshipResource {
  id           String   @id @db.Uuid
  offeringId   String   @db.Uuid
  title        String   @db.VarChar(160)
  resourceType ResourceType
  url          String   @db.VarChar(1500)
  createdAt    DateTime @default(now())
  fileKey      String?
  fileName     String?  @db.VarChar(255)
  mimeType     String?
  fileSize     Int?

  @@index([offeringId, createdAt])
}
```

Para archivos nuevos, `fileKey`, `mimeType` y `fileSize` son preferibles a
`fileData Bytes`. Si durante la transición se conserva la columna `BYTEA`, el
servicio debe limitar tamaño, usar streaming cuando sea posible y evitar
cargar archivos innecesariamente en memoria.

Las operaciones que crean asignaciones deben usar una transacción. El patrón
es: verificar reserva confirmada, intentar insertar con `bookingId` único y
tratar una violación de unicidad como resultado idempotente, no como error de
negocio fatal.

## 16. Mensajería y aprovisionamiento de pagos

El microservicio debe consumir `PaymentApproved` desde el mismo broker usado por
la plataforma. Si se usa RabbitMQ con NestJS, configurar un consumer dedicado,
acknowledgement manual y una cola durable. El mensaje debe incluir al menos:

```json
{
  "eventId": "uuid",
  "bookingId": "uuid",
  "occurredAt": "2026-11-05T15:00:00Z",
  "correlationId": "uuid"
}
```

Flujo de consumo:

1. Validar el envelope del evento.
2. Registrar `eventId` en una tabla de inbox o revisar `bookingId` procesado.
3. Consultar los datos de la reserva o recibirlos en el evento.
4. Crear la mentoría automática dentro de una transacción.
5. Confirmar el mensaje solo después del commit.
6. Enviar a dead-letter queue tras reintentos agotados.

No se debe hacer `ack` antes de persistir. Si el broker entrega dos veces el
evento, la tabla de inbox y la restricción única deben producir el mismo
resultado sin duplicar la mentoría.

La reconciliación de datos históricos debe ser un `@nestjs/schedule` job o un
comando administrativo protegido, no una escritura escondida dentro de
`GET /me`. Para despliegues con varias réplicas, usar lock distribuido o
particionar el trabajo.

## 17. Archivos y almacenamiento

Definir un puerto de almacenamiento:

```ts
export interface FileStoragePort {
  put(input: {
    key: string;
    stream: NodeJS.ReadableStream;
    contentType: string;
    contentLength: number;
  }): Promise<void>;
  delete(key: string): Promise<void>;
  createDownloadUrl(key: string, expiresInSeconds: number): Promise<string>;
}
```

La carga debe usar `FileInterceptor`, límite de 10 MB, validación MIME y nombre
sanitizado. No confiar solo en la extensión del archivo. La clave debe ser
generada por el servidor, por ejemplo:
`mentorship-resources/{offeringId}/{resourceId}/{randomId}`.

Para descargar, el servicio debe autorizar primero y solo después devolver un
stream o una URL firmada de corta duración. No devolver la clave interna del
bucket ni hacer el bucket público. Configurar `Content-Disposition`,
`X-Content-Type-Options: nosniff` y `Cache-Control: no-store` cuando el archivo
sea material privado.

## 18. Integración con bookings, offerings y Zoom

Crear interfaces internas independientes del cliente HTTP:

```ts
export interface BookingPort {
  findById(id: string): Promise<{ id: string; customerId: string; offeringId: string; status: string; scheduledAt: Date } | null>;
}

export interface OfferingPort {
  findById(id: string): Promise<{ id: string; providerId: string; title: string } | null>;
}

export interface ZoomPort {
  createMeeting(input: { topic: string; startsAt: Date; endsAt: Date }): Promise<ZoomMeeting>;
  createParticipantSignature(meetingId: string): string;
}
```

El adaptador concreto puede ser REST, gRPC o un cliente generado desde
OpenAPI. Los timeouts, reintentos y circuit breakers deben configurarse; una
caída de Zoom no debe borrar ni duplicar una asignación. Guardar el ID de Zoom
y volver a consultar antes de crear una reunión hace la operación idempotente.

## 19. Configuración y despliegue

Variables mínimas:

```text
PORT=3000
DATABASE_URL=postgresql://...
JWT_ISSUER=...
JWT_AUDIENCE=...
JWT_PUBLIC_KEY=...
RABBITMQ_URL=amqp://...
PAYMENT_APPROVED_QUEUE=mentorship.payment-approved
STORAGE_PROVIDER=s3
S3_BUCKET=...
S3_REGION=...
ZOOM_ACCOUNT_ID=...
ZOOM_CLIENT_ID=...
ZOOM_CLIENT_SECRET=...
MENTORSHIP_DEFAULT_ACCESS_HOURS=2
```

Usar `@nestjs/config` con `Joi` o `class-validator` para fallar al arrancar si
falta una variable obligatoria. Nunca guardar secretos en el repositorio ni
imprimirlos en logs.

El contenedor debe ejecutar migraciones como paso controlado del despliegue,
tener health checks separados para proceso y dependencias, y soportar apagado
graceful para dejar de consumir mensajes antes de cerrar conexiones.

## 20. Pruebas específicas para NestJS

- **Unitarias:** servicios de acceso, progreso, ownership y aprovisionamiento;
  mock de repositorios, broker, Zoom y storage.
- **Integración:** Prisma contra PostgreSQL de prueba; verificar unicidad por
  `bookingId`, transacciones y migraciones.
- **E2E:** `@nestjs/testing` + `supertest` para cada endpoint con JWT de
  customer, provider y admin.
- **Contrato:** validar OpenAPI y payloads del evento `PaymentApproved`.
- **Mensajería:** reentrega, evento duplicado, error transitorio, dead-letter y
  ack posterior al commit.
- **Archivos:** límite de 10 MB, MIME inválido, nombre malicioso, reemplazo y
  descarga sin autorización.

Casos críticos que deben quedar automatizados antes de producción:

1. Dos eventos simultáneos para el mismo `bookingId` crean una sola mentoría.
2. Un cliente no puede leer recursos de otro offering aunque conozca el UUID.
3. El acceso falla exactamente en `accessEndsAt`.
4. Un progreso `100` es terminal.
5. Un provider no puede administrar recursos ajenos.
6. Una carga que falla en storage no deja un recurso publicado incompleto.

## 21. Orden recomendado de implementación

1. Crear PostgreSQL, Prisma y migraciones de `assignments` y `resources`.
2. Implementar `AuthModule`, DTOs, filtros de error y contratos OpenAPI.
3. Implementar consulta, acceso y progreso de mentorías.
4. Implementar administración de recursos por URL.
5. Implementar storage de archivos y descarga autorizada.
6. Implementar consumo idempotente de `PaymentApproved`.
7. Implementar Zoom y mentorías `LIVE`.
8. Migrar datos existentes y ejecutar reconciliación histórica.
9. Cambiar el frontend para apuntar al nuevo `/api/v1`.
10. Ejecutar pruebas de contrato, carga y seguridad antes de retirar los
    endpoints del backend actual.

## 22. Decisiones que deben cerrarse antes de desarrollar

- ¿El microservicio tendrá su propia base de datos o compartirá PostgreSQL
  durante la transición? La opción final recomendada es base propia.
- ¿El evento de pago llegará por RabbitMQ, Kafka o HTTP webhook?
- ¿El nuevo servicio validará JWT directamente o confiará en un API gateway?
- ¿Los archivos se moverán a S3/GCS desde el inicio o se hará una transición
  temporal desde `BYTEA`?
- ¿La duración de dos horas será fija, configurable o definida por cada offering?
- ¿Los recursos serán compartidos por offering como ahora o versionados por cada
  asignación/compra?
- ¿Se requiere compatibilidad temporal con las rutas actuales sin `/v1`?
- ¿Qué servicio será responsable de actualizar/cancelar una mentoría cuando la
  reserva cambie de estado?

---

Para ver el desglose ágil en formato INVEST, los criterios de aceptación en Gherkin y la arquitectura de aislamiento e integración multi-proveedor (Zoom, Google Meet, Discord), consulta el documento:
- [Historias de Usuario y Arquitectura de Migración a NestJS](HISTORIAS-USUARIO-NESTJS.md)

