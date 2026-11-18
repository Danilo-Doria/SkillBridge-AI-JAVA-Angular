import type { ConfigService } from '@nestjs/config';
import { randomUUID } from 'node:crypto';
import type { KafkaConfig } from '../src/config/configuration.js';
import {
  DEFAULT_OUTBOX_PUBLISHER_CONFIG,
  OutboxPublishState,
  type OutboxEventInput,
  type OutboxEventRecord,
} from '../src/mentorship/outbox/domain/outbox.types.js';
import { OutboxMetrics } from '../src/mentorship/outbox/outbox.metrics.js';
import { OutboxPublisherService } from '../src/mentorship/outbox/outbox-publisher.service.js';
import type { EventPublisherPort } from '../src/mentorship/outbox/ports/event-publisher.port.js';
import type {
  ClaimOptions,
  OutboxRepository,
  RetryUpdate,
} from '../src/mentorship/outbox/ports/outbox.repository.js';

function configService(
  overrides: Partial<KafkaConfig> = {},
): ConfigService {
  const config: KafkaConfig = {
    ...DEFAULT_OUTBOX_PUBLISHER_CONFIG,
    enabled: true,
    ...overrides,
  };
  return {
    get: <T>(key: string): T | undefined =>
      key === 'kafka' ? (config as T) : undefined,
  } as unknown as ConfigService;
}

class FakeOutboxRepository implements OutboxRepository {
  batches: OutboxEventRecord[][] = [];
  readonly published: Array<{ id: string; at: Date }> = [];
  readonly retries: Array<{ id: string; update: RetryUpdate }> = [];
  claimCalls = 0;
  failMarkRetry = false;
  pending = 0;

  async countPending(): Promise<number> {
    return this.pending;
  }

  async claimBatch(_options: ClaimOptions): Promise<OutboxEventRecord[]> {
    this.claimCalls += 1;
    return this.batches.shift() ?? [];
  }

  async markPublished(id: string, at: Date): Promise<void> {
    this.published.push({ id, at });
  }

  async markRetry(id: string, update: RetryUpdate): Promise<void> {
    if (this.failMarkRetry) {
      throw new Error('markRetry failed');
    }
    this.retries.push({ id, update });
  }
}

class FakeEventPublisher implements EventPublisherPort {
  readonly published: OutboxEventInput[] = [];
  readonly failing = new Set<string>();
  alwaysFail = false;

  async publish(event: OutboxEventInput): Promise<void> {
    if (this.alwaysFail || this.failing.has(event.eventId)) {
      throw new Error('Kafka no disponible');
    }
    this.published.push(event);
  }
}

function record(
  overrides: Partial<OutboxEventRecord> = {},
): OutboxEventRecord {
  const assignmentId = overrides.aggregateId ?? randomUUID();
  const now = new Date();
  return {
    id: randomUUID(),
    sequence: '1',
    eventId: randomUUID(),
    eventType: 'MentorshipLiveRoomCreated',
    aggregateType: 'Mentorship',
    aggregateId: assignmentId,
    version: 1,
    correlationId: 'corr-1',
    payload: { assignmentId, provider: 'ZOOM', fallbackUsed: false },
    occurredAt: now,
    publishedAt: null,
    publishState: OutboxPublishState.PENDING,
    attempts: 0,
    nextAttemptAt: now,
    lastError: null,
    lockedUntil: null,
    lockedBy: null,
    createdAt: now,
    ...overrides,
  };
}

function buildService(
  repository: FakeOutboxRepository,
  publisher: FakeEventPublisher,
  config: Partial<KafkaConfig> = {},
): { service: OutboxPublisherService; metrics: OutboxMetrics } {
  const metrics = new OutboxMetrics();
  const service = new OutboxPublisherService(
    repository,
    publisher,
    configService(config),
    metrics,
  );
  return { service, metrics };
}

describe('OutboxPublisherService', () => {
  it('publica el lote y marca cada evento como publicado', async () => {
    const repository = new FakeOutboxRepository();
    const publisher = new FakeEventPublisher();
    const { service, metrics } = buildService(repository, publisher);
    const event = record();
    repository.pending = 1;
    repository.batches.push([event]);

    await expect(service.runOnce()).resolves.toBe(1);

    expect(publisher.published).toEqual([event]);
    expect(repository.published).toHaveLength(1);
    expect(repository.published[0].id).toBe(event.id);
    expect(metrics.snapshot()).toMatchObject({
      pending: 1,
      claimed: 1,
      published: 1,
    });
    expect(metrics.snapshot().lastPublishLatencyMs).toBeGreaterThanOrEqual(0);
  });

  it('programa un reintento PENDING cuando Kafka falla', async () => {
    const repository = new FakeOutboxRepository();
    const publisher = new FakeEventPublisher();
    publisher.alwaysFail = true;
    const before = Date.now();
    const { service, metrics } = buildService(repository, publisher, {
      backoffBaseMs: 1000,
      backoffMaxMs: 10_000,
    });
    repository.batches.push([record()]);

    await expect(service.runOnce()).resolves.toBe(0);

    expect(repository.published).toHaveLength(0);
    expect(repository.retries).toHaveLength(1);
    const retry = repository.retries[0].update;
    expect(retry.attempts).toBe(1);
    expect(retry.state).toBe(OutboxPublishState.PENDING);
    expect(retry.lastError).toMatch(/Kafka no disponible/);
    expect(retry.nextAttemptAt.getTime()).toBeGreaterThanOrEqual(
      before + 700,
    );
    expect(retry.nextAttemptAt.getTime()).toBeLessThanOrEqual(
      before + 1500,
    );
    expect(metrics.snapshot()).toMatchObject({ retried: 1, failed: 0 });
  });

  it('marca FAILED al agotar el máximo de intentos', async () => {
    const repository = new FakeOutboxRepository();
    const publisher = new FakeEventPublisher();
    publisher.alwaysFail = true;
    const { service, metrics } = buildService(repository, publisher, {
      maxAttempts: 3,
    });
    repository.batches.push([record({ attempts: 2 })]);

    await service.runOnce();

    expect(repository.retries[0].update).toMatchObject({
      attempts: 3,
      state: OutboxPublishState.FAILED,
    });
    expect(metrics.snapshot()).toMatchObject({ retried: 1, failed: 1 });
  });

  it('continúa con el resto del lote si un evento falla', async () => {
    const repository = new FakeOutboxRepository();
    const publisher = new FakeEventPublisher();
    const first = record();
    const second = record();
    publisher.failing.add(first.eventId);
    const { service } = buildService(repository, publisher);
    repository.batches.push([first, second]);

    await expect(service.runOnce()).resolves.toBe(1);

    expect(publisher.published).toEqual([second]);
    expect(repository.published).toHaveLength(1);
    expect(repository.retries).toHaveLength(1);
    expect(repository.retries[0].id).toBe(first.id);
  });

  it('no publica un eventType fuera del catálogo y lo marca FAILED', async () => {
    const repository = new FakeOutboxRepository();
    const publisher = new FakeEventPublisher();
    const { service, metrics } = buildService(repository, publisher);
    repository.batches.push([record({ eventType: 'UnknownEvent' })]);

    await expect(service.runOnce()).resolves.toBe(0);

    expect(publisher.published).toHaveLength(0);
    expect(repository.retries[0].update).toMatchObject({
      state: OutboxPublishState.FAILED,
    });
    expect(repository.retries[0].update.lastError).toMatch(
      /eventType desconocido/,
    );
    expect(metrics.snapshot()).toMatchObject({ published: 0, failed: 1 });
  });

  it('no publica un payload inválido y lo marca FAILED', async () => {
    const repository = new FakeOutboxRepository();
    const publisher = new FakeEventPublisher();
    const { service } = buildService(repository, publisher);
    repository.batches.push([
      record({ payload: { assignmentId: randomUUID(), provider: 'ZOOM' } }),
    ]);

    await expect(service.runOnce()).resolves.toBe(0);

    expect(publisher.published).toHaveLength(0);
    expect(repository.retries[0].update.lastError).toMatch(
      /fallbackUsed|assignmentId/,
    );
    expect(repository.retries[0].update.state).toBe(OutboxPublishState.FAILED);
  });

  it('no reclama nada cuando el publicador está deshabilitado', async () => {
    const repository = new FakeOutboxRepository();
    const publisher = new FakeEventPublisher();
    const { service } = buildService(repository, publisher, {
      enabled: false,
    });

    await expect(service.runOnce()).resolves.toBe(0);

    expect(repository.claimCalls).toBe(0);
    expect(publisher.published).toHaveLength(0);
  });

  it('no propaga el error si falla el registro del reintento', async () => {
    const repository = new FakeOutboxRepository();
    const publisher = new FakeEventPublisher();
    publisher.alwaysFail = true;
    repository.failMarkRetry = true;
    const { service } = buildService(repository, publisher);
    repository.batches.push([record()]);

    await expect(service.runOnce()).resolves.toBe(0);
  });
});
