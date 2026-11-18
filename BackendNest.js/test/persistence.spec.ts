import {
  PostgreSqlContainer,
  type StartedPostgreSqlContainer,
} from '@testcontainers/postgresql';
import { randomUUID } from 'node:crypto';
import { DataSource } from 'typeorm';
import { TypeOrmLiveRoomRepository } from '../src/mentorship/live-room/adapters/typeorm-live-room.repository.js';
import { TypeOrmOutboxRepository } from '../src/mentorship/outbox/adapters/typeorm-outbox.repository.js';
import {
  LiveProvider,
  LiveRoomSyncState,
  MentorshipMode,
  MentorshipStatus,
} from '../src/mentorship/live-room/domain/live-room.types.js';
import { LiveRoomEntity } from '../src/mentorship/entities/live-room.entity.js';
import { MentorshipAssignmentEntity } from '../src/mentorship/entities/mentorship-assignment.entity.js';
import { OutboxEventEntity } from '../src/mentorship/entities/outbox-event.entity.js';
import { OutboxPublishState } from '../src/mentorship/outbox/domain/outbox.types.js';
import { OutboxAppendService } from '../src/mentorship/outbox/outbox-append.service.js';
import {
  buildDataSourceOptions,
  databaseEntities,
  databaseMigrations,
} from '../src/database/data-source.js';

const runDbTests = process.env.RUN_DB_TESTS === '1';
const describeDb = runDbTests ? describe : describe.skip;

describeDb('Persistencia PostgreSQL (Testcontainers)', () => {
  let container: StartedPostgreSqlContainer;
  let dataSource: DataSource;
  let repository: TypeOrmLiveRoomRepository;
  let outboxRepository: TypeOrmOutboxRepository;
  let appendService: OutboxAppendService;
  let assignmentId: string;

  beforeAll(async () => {
    container = await new PostgreSqlContainer('postgres:17-alpine').start();
    dataSource = new DataSource(
      buildDataSourceOptions({
        host: container.getHost(),
        port: container.getPort(),
        username: container.getUsername(),
        password: container.getPassword(),
        database: container.getDatabase(),
        entities: databaseEntities,
        migrations: databaseMigrations,
      }),
    );
    await dataSource.initialize();
    await dataSource.runMigrations();
    repository = new TypeOrmLiveRoomRepository(dataSource);
    outboxRepository = new TypeOrmOutboxRepository(dataSource);
    appendService = new OutboxAppendService();
  }, 120_000);

  afterAll(async () => {
    await dataSource?.destroy();
    await container?.stop();
  });

  beforeEach(async () => {
    await dataSource.query(
      'TRUNCATE TABLE "outbox_events", "live_rooms", "mentorship_assignments" CASCADE',
    );

    assignmentId = randomUUID();
    await dataSource.getRepository(MentorshipAssignmentEntity).insert({
      id: assignmentId,
      bookingId: randomUUID(),
      customerId: randomUUID(),
      providerId: randomUUID(),
      offeringId: randomUUID(),
      mode: MentorshipMode.LIVE,
      status: MentorshipStatus.SCHEDULED,
      accessStartsAt: new Date(Date.now() - 60_000),
      accessEndsAt: new Date(Date.now() + 3_600_000),
      progressPercent: 0,
      completedAt: null,
      version: 0,
    });
  });

  it('aplica la migración y crea las tablas', async () => {
    const tables = await dataSource.query<{ table_name: string }[]>(
      `SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'`,
    );
    const names = tables.map((t) => t.table_name);
    expect(names).toEqual(
      expect.arrayContaining([
        'mentorship_assignments',
        'live_rooms',
        'outbox_events',
      ]),
    );
  });

  it('serializa 10 solicitudes concurrentes en una sola sala y un solo evento', async () => {
    const results = await Promise.all(
      Array.from({ length: 10 }, () =>
        repository.runLocked(assignmentId, async (tx) => {
          const existing = await tx.findRoom(assignmentId);
          if (existing) {
            return false;
          }
          await tx.saveRoom({
            id: randomUUID(),
            assignmentId,
            provider: LiveProvider.ZOOM,
            externalRoomId: '999',
            joinUrl: 'https://zoom.example/j/999',
            hostUrl: 'https://zoom.example/s/999',
            passcodeEnc: null,
            hostSignatureEnc: null,
            inviteUrlEnc: null,
            syncState: LiveRoomSyncState.SYNCED,
          });
          await tx.saveOutbox({
            eventId: randomUUID(),
            eventType: 'MentorshipLiveRoomCreated',
            aggregateType: 'Mentorship',
            aggregateId: assignmentId,
            version: 1,
            correlationId: null,
            occurredAt: new Date(),
            payload: { assignmentId, provider: 'ZOOM', fallbackUsed: false },
          });
          return true;
        }),
      ),
    );

    expect(results.filter(Boolean)).toHaveLength(1);
    expect(await dataSource.getRepository(LiveRoomEntity).count()).toBe(1);
    expect(await dataSource.getRepository(OutboxEventEntity).count()).toBe(1);
  });

  it('revierte la sala si el outbox falla en la misma transacción', async () => {
    await expect(
      repository.runLocked(assignmentId, async (tx) => {
        await tx.saveRoom({
          id: randomUUID(),
          assignmentId,
          provider: LiveProvider.ZOOM,
          externalRoomId: '999',
          joinUrl: 'https://zoom.example/j/999',
          hostUrl: null,
          passcodeEnc: null,
          hostSignatureEnc: null,
          inviteUrlEnc: null,
          syncState: LiveRoomSyncState.SYNCED,
        });
        throw new Error('outbox caído');
      }),
    ).rejects.toThrow('outbox caído');

    expect(await dataSource.getRepository(LiveRoomEntity).count()).toBe(0);
  });

  async function insertOutbox(
    aggregateId: string,
    occurredAt: Date,
  ): Promise<string> {
    const eventId = randomUUID();
    await dataSource.getRepository(OutboxEventEntity).insert({
      id: randomUUID(),
      eventId,
      eventType: 'MentorshipLiveRoomCreated',
      aggregateType: 'Mentorship',
      aggregateId,
      version: 1,
      correlationId: null,
      payload: { assignmentId: aggregateId, provider: 'ZOOM', fallbackUsed: false },
      occurredAt,
      publishedAt: null,
      publishState: OutboxPublishState.PENDING,
      attempts: 0,
      nextAttemptAt: new Date(),
      lastError: null,
      lockedUntil: null,
      lockedBy: null,
    });
    return eventId;
  }

  it('anexa el evento en la misma transacción y revierte ambos ante error', async () => {
    const eventAssignmentId = randomUUID();

    await expect(
      dataSource.transaction(async (manager) => {
        await appendService.append(manager, {
          eventType: 'MentorshipCompleted',
          aggregateId: eventAssignmentId,
          payload: {
            assignmentId: eventAssignmentId,
            completedAt: new Date().toISOString(),
          },
        });
        throw new Error('rollback intencional');
      }),
    ).rejects.toThrow('rollback intencional');

    expect(await dataSource.getRepository(OutboxEventEntity).count()).toBe(0);
  });

  it('confirma el evento cuando la transacción de negocio termina', async () => {
    const eventAssignmentId = randomUUID();

    const entity = await dataSource.transaction((manager) =>
      appendService.append(manager, {
        eventType: 'MentorshipCompleted',
        aggregateId: eventAssignmentId,
        correlationId: 'corr-atomic',
        payload: {
          assignmentId: eventAssignmentId,
          completedAt: new Date().toISOString(),
        },
      }),
    );

    const stored = await dataSource
      .getRepository(OutboxEventEntity)
      .findOneByOrFail({ id: entity.id });
    expect(stored.publishState).toBe(OutboxPublishState.PENDING);
    expect(stored.attempts).toBe(0);
    expect(stored.publishedAt).toBeNull();
  });

  it('reclama filas concurrentes sin solapamiento (SKIP LOCKED)', async () => {
    const aggregates = Array.from({ length: 10 }, () => randomUUID());
    for (const aggregate of aggregates) {
      await insertOutbox(aggregate, new Date());
    }

    const [first, second] = await Promise.all([
      outboxRepository.claimBatch({
        limit: 5,
        now: new Date(),
        lockMs: 30_000,
        instanceId: 'instance-a',
      }),
      outboxRepository.claimBatch({
        limit: 5,
        now: new Date(),
        lockMs: 30_000,
        instanceId: 'instance-b',
      }),
    ]);

    const ids = [...first, ...second].map((event) => event.id);
    expect(ids).toHaveLength(10);
    expect(new Set(ids).size).toBe(10);
    expect(first.every((event) => event.lockedBy === 'instance-a')).toBe(true);
    expect(second.every((event) => event.lockedBy === 'instance-b')).toBe(true);
  });

  it('preserva el orden por agregado y libera la siguiente fila al publicar', async () => {
    const now = Date.now();
    const aggregate = randomUUID();
    await insertOutbox(aggregate, new Date(now - 3000));
    await insertOutbox(aggregate, new Date(now - 2000));
    await insertOutbox(aggregate, new Date(now - 1000));

    const claimed: string[] = [];
    for (let i = 0; i < 3; i += 1) {
      const batch = await outboxRepository.claimBatch({
        limit: 10,
        now: new Date(),
        lockMs: 30_000,
        instanceId: 'instance-order',
      });
      expect(batch).toHaveLength(1);
      const [event] = batch;
      expect(event.aggregateId).toBe(aggregate);
      claimed.push(event.eventId);
      await outboxRepository.markPublished(event.id, new Date());
    }

    expect(claimed).toHaveLength(3);
    expect(new Set(claimed).size).toBe(3);
  });

  it('desempata por orden de inserción cuando occurredAt coincide', async () => {
    const aggregate = randomUUID();
    const sameInstant = new Date('2026-11-16T12:00:00.000Z');
    const firstEventId = await insertOutbox(aggregate, sameInstant);
    const secondEventId = await insertOutbox(aggregate, sameInstant);

    const [first] = await outboxRepository.claimBatch({
      limit: 10,
      now: new Date(),
      lockMs: 30_000,
      instanceId: 'instance-order',
    });
    expect(first.eventId).toBe(firstEventId);
    await outboxRepository.markPublished(first.id, new Date());

    const [second] = await outboxRepository.claimBatch({
      limit: 10,
      now: new Date(),
      lockMs: 30_000,
      instanceId: 'instance-order',
    });
    expect(second.eventId).toBe(secondEventId);
  });

  it('no reclama una fila mientras su lease está vigente', async () => {
    const aggregate = randomUUID();
    await insertOutbox(aggregate, new Date());

    const first = await outboxRepository.claimBatch({
      limit: 1,
      now: new Date(),
      lockMs: 60_000,
      instanceId: 'instance-a',
    });
    const second = await outboxRepository.claimBatch({
      limit: 1,
      now: new Date(),
      lockMs: 60_000,
      instanceId: 'instance-b',
    });

    expect(first).toHaveLength(1);
    expect(second).toHaveLength(0);
  });
});
