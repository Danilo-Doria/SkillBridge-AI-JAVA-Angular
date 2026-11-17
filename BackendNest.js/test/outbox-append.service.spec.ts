import type { EntityManager } from 'typeorm';
import { randomUUID } from 'node:crypto';
import { OutboxAppendService } from '../src/mentorship/outbox/outbox-append.service.js';
import { OutboxPublishState } from '../src/mentorship/outbox/domain/outbox.types.js';

interface SavedRow {
  id: string;
  eventId: string;
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  version: number;
  correlationId: string | null;
  payload: Record<string, unknown>;
  occurredAt: Date;
  publishedAt: Date | null;
  publishState: OutboxPublishState;
  attempts: number;
  nextAttemptAt: Date;
  lastError: string | null;
  lockedUntil: Date | null;
  lockedBy: string | null;
}

function fakeManager(): {
  manager: EntityManager;
  saved: SavedRow[];
} {
  const saved: SavedRow[] = [];
  const manager = {
    getRepository: () => ({
      create: (value: SavedRow) => value,
      save: async (value: SavedRow) => {
        saved.push(value);
        return value;
      },
    }),
  } as unknown as EntityManager;
  return { manager, saved };
}

describe('OutboxAppendService', () => {
  it('persiste un evento válido con metadata de publicación', async () => {
    const { manager, saved } = fakeManager();
    const service = new OutboxAppendService();
    const assignmentId = randomUUID();

    const entity = await service.append(manager, {
      eventType: 'MentorshipExpired',
      aggregateId: assignmentId,
      correlationId: 'corr-1',
      payload: { assignmentId },
    });

    expect(saved).toHaveLength(1);
    expect(entity.eventId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
    expect(entity.aggregateType).toBe('Mentorship');
    expect(entity.version).toBe(1);
    expect(entity.publishedAt).toBeNull();
    expect(entity.publishState).toBe(OutboxPublishState.PENDING);
    expect(entity.attempts).toBe(0);
    expect(entity.lockedUntil).toBeNull();
  });

  it('respeta un eventId explícito', async () => {
    const { manager } = fakeManager();
    const service = new OutboxAppendService();
    const assignmentId = randomUUID();
    const eventId = randomUUID();

    const entity = await service.append(manager, {
      eventId,
      eventType: 'MentorshipNoShow',
      aggregateId: assignmentId,
      payload: { assignmentId },
    });

    expect(entity.eventId).toBe(eventId);
  });

  it('rechaza un payload inválido sin guardar', async () => {
    const { manager, saved } = fakeManager();
    const service = new OutboxAppendService();
    const assignmentId = randomUUID();

    await expect(
      service.append(manager, {
        eventType: 'MentorshipLiveRoomCreated',
        aggregateId: assignmentId,
        payload: {
          assignmentId,
          provider: 'ZOOM',
          fallbackUsed: false,
          zoomPasscode: '1234',
        },
      }),
    ).rejects.toThrow(/campo prohibido/);

    expect(saved).toHaveLength(0);
  });
});
