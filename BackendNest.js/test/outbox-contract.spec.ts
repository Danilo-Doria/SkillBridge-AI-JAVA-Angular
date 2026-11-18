import { randomUUID } from 'node:crypto';
import {
  buildBusinessEventEnvelope,
  validateOutboxEvent,
} from '../src/mentorship/outbox/domain/event-catalog.js';
import type { OutboxEventInput } from '../src/mentorship/outbox/domain/outbox.types.js';

const occurredAt = new Date('2026-11-16T14:00:00.000Z');

interface Fixture {
  eventType: string;
  payload: (assignmentId: string) => Record<string, unknown>;
}

const fixtures: Fixture[] = [
  {
    eventType: 'MentorshipProvisioned',
    payload: (assignmentId) => ({
      assignmentId,
      bookingId: randomUUID(),
      customerId: randomUUID(),
      offeringId: randomUUID(),
      providerId: randomUUID(),
      mode: 'LIVE',
      accessStartsAt: '2026-11-17T14:00:00.000Z',
      accessEndsAt: '2026-11-17T16:00:00.000Z',
    }),
  },
  {
    eventType: 'MentorshipStarted',
    payload: (assignmentId) => ({
      assignmentId,
      startedAt: '2026-11-17T14:01:00.000Z',
    }),
  },
  {
    eventType: 'MentorshipProgressUpdated',
    payload: (assignmentId) => ({
      assignmentId,
      previousPercent: 40,
      progressPercent: 75,
    }),
  },
  {
    eventType: 'MentorshipCompleted',
    payload: (assignmentId) => ({
      assignmentId,
      completedAt: '2026-11-17T15:55:00.000Z',
    }),
  },
  {
    eventType: 'MentorshipCancelled',
    payload: (assignmentId) => ({
      assignmentId,
      reason: 'CUSTOMER_REQUEST',
    }),
  },
  {
    eventType: 'MentorshipExpired',
    payload: (assignmentId) => ({ assignmentId }),
  },
  {
    eventType: 'MentorshipNoShow',
    payload: (assignmentId) => ({ assignmentId }),
  },
  {
    eventType: 'MentorshipRescheduled',
    payload: (assignmentId) => ({
      assignmentId,
      previousStartsAt: '2026-11-17T14:00:00.000Z',
      previousEndsAt: '2026-11-17T16:00:00.000Z',
      accessStartsAt: '2026-11-18T14:00:00.000Z',
      accessEndsAt: '2026-11-18T16:00:00.000Z',
      reason: 'PROVIDER_AVAILABILITY',
    }),
  },
  {
    eventType: 'MentorshipLiveRoomCreated',
    payload: (assignmentId) => ({
      assignmentId,
      provider: 'ZOOM',
      fallbackUsed: false,
    }),
  },
];

describe('Mentorship BusinessEvent contract', () => {
  it.each(fixtures)(
    'valida y serializa $eventType conforme a events.md',
    ({ eventType, payload }) => {
      const assignmentId = randomUUID();
      const event: OutboxEventInput = {
        eventId: randomUUID(),
        eventType,
        aggregateType: 'Mentorship',
        aggregateId: assignmentId,
        version: 1,
        correlationId: 'corr-contract',
        occurredAt,
        payload: payload(assignmentId),
      };

      expect(() => validateOutboxEvent(event)).not.toThrow();

      const envelope = buildBusinessEventEnvelope(event);
      expect(Object.keys(envelope).sort()).toEqual(
        [
          'aggregateId',
          'aggregateType',
          'correlationId',
          'eventId',
          'eventType',
          'occurredAt',
          'payload',
          'version',
        ].sort(),
      );
      expect(envelope).toMatchObject({
        eventId: event.eventId,
        eventType,
        aggregateId: assignmentId,
        aggregateType: 'Mentorship',
        occurredAt: occurredAt.toISOString(),
        correlationId: 'corr-contract',
        version: 1,
        payload: event.payload,
      });
      expect(JSON.parse(JSON.stringify(envelope))).toEqual(envelope);
    },
  );
});
