import {
  MENTORSHIP_AGGREGATE_TYPE,
  type BusinessEventEnvelope,
  type OutboxEventInput,
} from './outbox.types.js';

/**
 * Catálogo cerrado de eventos de mentoría (docs/contracts/mentorships/events.md §3).
 * Cualquier cambio de contrato debe reflejarse aquí y en la prueba de contrato.
 */
interface EventDefinition {
  required: readonly string[];
  dateFields?: readonly string[];
}

export const MENTORSHIP_EVENT_CATALOG: Readonly<Record<string, EventDefinition>> = {
  MentorshipProvisioned: {
    required: [
      'assignmentId',
      'bookingId',
      'customerId',
      'offeringId',
      'providerId',
      'mode',
      'accessStartsAt',
      'accessEndsAt',
    ],
    dateFields: ['accessStartsAt', 'accessEndsAt'],
  },
  MentorshipStarted: {
    required: ['assignmentId', 'startedAt'],
    dateFields: ['startedAt'],
  },
  MentorshipProgressUpdated: {
    required: ['assignmentId', 'previousPercent', 'progressPercent'],
  },
  MentorshipCompleted: {
    required: ['assignmentId', 'completedAt'],
    dateFields: ['completedAt'],
  },
  MentorshipCancelled: {
    required: ['assignmentId', 'reason'],
  },
  MentorshipExpired: {
    required: ['assignmentId'],
  },
  MentorshipNoShow: {
    required: ['assignmentId'],
  },
  MentorshipRescheduled: {
    required: [
      'assignmentId',
      'previousStartsAt',
      'previousEndsAt',
      'accessStartsAt',
      'accessEndsAt',
      'reason',
    ],
    dateFields: [
      'previousStartsAt',
      'previousEndsAt',
      'accessStartsAt',
      'accessEndsAt',
    ],
  },
  MentorshipLiveRoomCreated: {
    required: ['assignmentId', 'provider', 'fallbackUsed'],
  },
};

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const FORBIDDEN_KEY_PATTERNS: readonly RegExp[] = [
  /passcode/i,
  /password/i,
  /secret/i,
  /token/i,
  /signature/i,
  /^hostUrl$/i,
  /^startUrl$/i,
  /^inviteUrl$/i,
  /^invite$/i,
  /card/i,
  /cvv/i,
  /cvc/i,
  /^pan$/i,
  /authorization/i,
  /credential/i,
  /^apiKey$/i,
  /^privateKey$/i,
];

const MODES = new Set(['ASYNCHRONOUS', 'LIVE']);
const MAX_PAYLOAD_DEPTH = 10;

export class OutboxValidationError extends Error {
  readonly code = 'OUTBOX_INVALID_EVENT';

  constructor(message: string) {
    super(message);
    this.name = 'OutboxValidationError';
  }
}

/**
 * Valida un evento antes de persistirlo. El contrato es cerrado: rechaza tipos
 * desconocidos, campos fuera del catálogo y cualquier clave sensible.
 */
export function validateOutboxEvent(event: OutboxEventInput): void {
  if (!event || typeof event !== 'object') {
    throw new OutboxValidationError('El evento de outbox es obligatorio');
  }
  if (event.aggregateType !== MENTORSHIP_AGGREGATE_TYPE) {
    throw new OutboxValidationError(
      `aggregateType inválido: ${String(event.aggregateType)}`,
    );
  }
  if (!UUID_PATTERN.test(event.eventId)) {
    throw new OutboxValidationError(`eventId no es un UUID válido: ${event.eventId}`);
  }
  if (!event.aggregateId || typeof event.aggregateId !== 'string') {
    throw new OutboxValidationError('aggregateId es obligatorio');
  }
  if (!Number.isInteger(event.version) || event.version < 1) {
    throw new OutboxValidationError('version debe ser un entero positivo');
  }
  if (!isValidDate(event.occurredAt)) {
    throw new OutboxValidationError('occurredAt debe ser una fecha válida');
  }

  const definition = MENTORSHIP_EVENT_CATALOG[event.eventType];
  if (!definition) {
    throw new OutboxValidationError(
      `eventType desconocido para mentorías: ${event.eventType}`,
    );
  }

  const payload = event.payload;
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new OutboxValidationError('payload debe ser un objeto');
  }

  assertNoForbiddenKeys(payload, 'payload', 0);

  const allowed = new Set(definition.required);
  for (const key of Object.keys(payload)) {
    if (!allowed.has(key)) {
      throw new OutboxValidationError(
        `campo no permitido en ${event.eventType}: ${key}`,
      );
    }
  }

  for (const key of definition.required) {
    const value = payload[key];
    if (value === undefined || value === null || value === '') {
      throw new OutboxValidationError(
        `campo obligatorio ausente en ${event.eventType}: ${key}`,
      );
    }
  }

  if (payload.assignmentId !== event.aggregateId) {
    throw new OutboxValidationError(
      'payload.assignmentId debe coincidir con aggregateId',
    );
  }

  for (const field of definition.dateFields ?? []) {
    if (!isValidDate(payload[field])) {
      throw new OutboxValidationError(`${field} debe ser una fecha válida`);
    }
  }

  validateKnownFields(event.eventType, payload);
}

export function buildBusinessEventEnvelope(
  event: OutboxEventInput,
): BusinessEventEnvelope {
  const envelope: BusinessEventEnvelope = {
    eventId: event.eventId,
    eventType: event.eventType,
    aggregateId: event.aggregateId,
    aggregateType: event.aggregateType,
    occurredAt: toIsoString(event.occurredAt),
    version: event.version,
    payload: event.payload,
  };
  if (event.correlationId) {
    envelope.correlationId = event.correlationId;
  }
  return envelope;
}

function validateKnownFields(
  eventType: string,
  payload: Record<string, unknown>,
): void {
  if (eventType === 'MentorshipProvisioned') {
    if (typeof payload.mode !== 'string' || !MODES.has(payload.mode)) {
      throw new OutboxValidationError(`mode inválido: ${String(payload.mode)}`);
    }
  }

  if (
    eventType === 'MentorshipProgressUpdated' &&
    (!isPercent(payload.previousPercent) || !isPercent(payload.progressPercent))
  ) {
    throw new OutboxValidationError(
      'previousPercent y progressPercent deben ser enteros entre 0 y 100',
    );
  }

  if (eventType === 'MentorshipLiveRoomCreated') {
    if (
      typeof payload.provider !== 'string' ||
      payload.provider.trim() === ''
    ) {
      throw new OutboxValidationError('provider debe ser una cadena no vacía');
    }
    if (typeof payload.fallbackUsed !== 'boolean') {
      throw new OutboxValidationError('fallbackUsed debe ser booleano');
    }
  }
}

function assertNoForbiddenKeys(
  value: unknown,
  path: string,
  depth: number,
): void {
  if (depth > MAX_PAYLOAD_DEPTH) {
    throw new OutboxValidationError(`payload excede la profundidad máxima en ${path}`);
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) =>
      assertNoForbiddenKeys(item, `${path}[${index}]`, depth + 1),
    );
    return;
  }
  if (!value || typeof value !== 'object') {
    return;
  }
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    if (FORBIDDEN_KEY_PATTERNS.some((pattern) => pattern.test(key))) {
      throw new OutboxValidationError(`campo prohibido en ${path}: ${key}`);
    }
    assertNoForbiddenKeys(child, `${path}.${key}`, depth + 1);
  }
}

function isPercent(value: unknown): boolean {
  return Number.isInteger(value) && Number(value) >= 0 && Number(value) <= 100;
}

function isValidDate(value: unknown): boolean {
  if (value instanceof Date) {
    return !Number.isNaN(value.getTime());
  }
  if (typeof value === 'string') {
    return !Number.isNaN(Date.parse(value));
  }
  return false;
}

function toIsoString(value: Date | string): string {
  return value instanceof Date
    ? value.toISOString()
    : new Date(value).toISOString();
}
