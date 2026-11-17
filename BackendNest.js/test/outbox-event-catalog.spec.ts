import { randomUUID } from 'node:crypto';
import {
  OutboxValidationError,
  validateOutboxEvent,
} from '../src/mentorship/outbox/domain/event-catalog.js';
import type { OutboxEventInput } from '../src/mentorship/outbox/domain/outbox.types.js';

function validEvent(
  overrides: Partial<OutboxEventInput> = {},
): OutboxEventInput {
  const assignmentId = overrides.aggregateId ?? randomUUID();
  return {
    eventId: randomUUID(),
    eventType: 'MentorshipLiveRoomCreated',
    aggregateType: 'Mentorship',
    aggregateId: assignmentId,
    version: 1,
    correlationId: 'corr-1',
    occurredAt: new Date('2026-11-16T12:00:00Z'),
    payload: {
      assignmentId,
      provider: 'ZOOM',
      fallbackUsed: false,
    },
    ...overrides,
  };
}

describe('outbox event catalog', () => {
  it('acepta un evento del catálogo con payload válido', () => {
    expect(() => validateOutboxEvent(validEvent())).not.toThrow();
  });

  it('rechaza un eventType desconocido', () => {
    expect(() =>
      validateOutboxEvent(validEvent({ eventType: 'MentorshipInvented' })),
    ).toThrow(OutboxValidationError);
  });

  it('rechaza campos prohibidos incluso anidados', () => {
    expect(() =>
      validateOutboxEvent(
        validEvent({
          payload: {
            assignmentId: randomUUID(),
            provider: 'ZOOM',
            fallbackUsed: false,
            extra: { zoomPasscode: '1234' },
          },
        }),
      ),
    ).toThrow(/campo prohibido/);
  });

  it('rechaza un assignmentId que no coincide con aggregateId', () => {
    expect(() =>
      validateOutboxEvent(
        validEvent({
          payload: {
            assignmentId: randomUUID(),
            provider: 'ZOOM',
            fallbackUsed: false,
          },
        }),
      ),
    ).toThrow(/assignmentId debe coincidir/);
  });

  it('rechaza un campo obligatorio ausente', () => {
    expect(() =>
      validateOutboxEvent(
        validEvent({
          payload: { assignmentId: randomUUID(), provider: 'ZOOM' },
        }),
      ),
    ).toThrow(/fallbackUsed/);
  });

  it('rechaza campos fuera del catálogo', () => {
    const assignmentId = randomUUID();
    expect(() =>
      validateOutboxEvent(
        validEvent({
          aggregateId: assignmentId,
          payload: {
            assignmentId,
            provider: 'ZOOM',
            fallbackUsed: false,
            unexpected: true,
          },
        }),
      ),
    ).toThrow(/campo no permitido/);
  });

  it('rechaza un eventId que no es UUID', () => {
    expect(() =>
      validateOutboxEvent(validEvent({ eventId: 'not-a-uuid' })),
    ).toThrow(/UUID/);
  });

  it('rechaza una fecha inválida', () => {
    expect(() =>
      validateOutboxEvent(validEvent({ occurredAt: new Date('invalid') })),
    ).toThrow(/occurredAt/);
  });

  it('rechaza mode inválido en MentorshipProvisioned', () => {
    const assignmentId = randomUUID();
    expect(() =>
      validateOutboxEvent({
        eventId: randomUUID(),
        eventType: 'MentorshipProvisioned',
        aggregateType: 'Mentorship',
        aggregateId: assignmentId,
        version: 1,
        correlationId: null,
        occurredAt: new Date(),
        payload: {
          assignmentId,
          bookingId: randomUUID(),
          customerId: randomUUID(),
          offeringId: randomUUID(),
          providerId: randomUUID(),
          mode: 'HYBRID',
          accessStartsAt: new Date().toISOString(),
          accessEndsAt: new Date().toISOString(),
        },
      }),
    ).toThrow(/mode inválido/);
  });

  it('rechaza porcentajes fuera de rango', () => {
    const assignmentId = randomUUID();
    expect(() =>
      validateOutboxEvent({
        eventId: randomUUID(),
        eventType: 'MentorshipProgressUpdated',
        aggregateType: 'Mentorship',
        aggregateId: assignmentId,
        version: 1,
        correlationId: null,
        occurredAt: new Date(),
        payload: {
          assignmentId,
          previousPercent: 101,
          progressPercent: 50,
        },
      }),
    ).toThrow(/previousPercent/);
  });
});
