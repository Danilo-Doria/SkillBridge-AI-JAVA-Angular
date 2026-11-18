import { randomUUID } from 'node:crypto';
import { buildBusinessEventEnvelope } from '../src/mentorship/outbox/domain/event-catalog.js';
import type { OutboxEventInput } from '../src/mentorship/outbox/domain/outbox.types.js';

const assignmentId = randomUUID();

const event: OutboxEventInput = {
  eventId: randomUUID(),
  eventType: 'MentorshipCompleted',
  aggregateType: 'Mentorship',
  aggregateId: assignmentId,
  version: 1,
  correlationId: 'corr-42',
  occurredAt: new Date('2026-11-16T14:30:00.000Z'),
  payload: {
    assignmentId,
    completedAt: '2026-11-16T14:29:59.000Z',
  },
};

describe('BusinessEvent envelope', () => {
  it('serializa occurredAt como ISO UTC y conserva el payload', () => {
    const envelope = buildBusinessEventEnvelope(event);

    expect(envelope).toEqual({
      eventId: event.eventId,
      eventType: 'MentorshipCompleted',
      aggregateId: assignmentId,
      aggregateType: 'Mentorship',
      occurredAt: '2026-11-16T14:30:00.000Z',
      correlationId: 'corr-42',
      version: 1,
      payload: event.payload,
    });
    expect(JSON.parse(JSON.stringify(envelope))).toEqual(envelope);
  });

  it('omite correlationId cuando no existe', () => {
    const envelope = buildBusinessEventEnvelope({
      ...event,
      correlationId: null,
    });

    expect('correlationId' in envelope).toBe(false);
  });

  it('no duplica campos de actor dentro del payload', () => {
    const envelope = buildBusinessEventEnvelope(event);

    expect(envelope.payload).not.toHaveProperty('actorUserId');
    expect(envelope.payload).not.toHaveProperty('actorRole');
  });
});
