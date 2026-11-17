import { Injectable } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { type DataSource } from 'typeorm';
import {
  OutboxPublishState,
  type OutboxEventRecord,
} from '../domain/outbox.types.js';
import type {
  ClaimOptions,
  OutboxRepository,
  RetryUpdate,
} from '../ports/outbox.repository.js';

interface OutboxRow {
  id: string;
  sequence: string;
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
  createdAt: Date;
}

const CLAIM_SQL = `
WITH candidate AS (
  SELECT o."id"
  FROM "outbox_events" o
  WHERE o."publishedAt" IS NULL
    AND o."publishState" = $5
    AND o."nextAttemptAt" <= $1
    AND (o."lockedUntil" IS NULL OR o."lockedUntil" <= $1)
    AND NOT EXISTS (
      SELECT 1
      FROM "outbox_events" prev
      WHERE prev."aggregateId" = o."aggregateId"
        AND prev."publishedAt" IS NULL
        AND prev."publishState" = $5
        AND (
          prev."occurredAt" < o."occurredAt"
          OR (prev."occurredAt" = o."occurredAt" AND prev."sequence" < o."sequence")
        )
    )
  ORDER BY o."occurredAt" ASC, o."sequence" ASC
  LIMIT $2
  FOR UPDATE OF o SKIP LOCKED
)
UPDATE "outbox_events" o
SET "lockedUntil" = $3,
    "lockedBy" = $4
FROM candidate c
WHERE o."id" = c."id"
RETURNING
  o."id", o."sequence", o."eventId", o."eventType", o."aggregateType", o."aggregateId",
  o."version", o."correlationId", o."payload", o."occurredAt", o."publishedAt",
  o."publishState", o."attempts", o."nextAttemptAt", o."lastError",
  o."lockedUntil", o."lockedBy", o."createdAt"
`;

@Injectable()
export class TypeOrmOutboxRepository implements OutboxRepository {
  constructor(@InjectDataSource() private readonly dataSource: DataSource) {}

  async claimBatch(options: ClaimOptions): Promise<OutboxEventRecord[]> {
    const lockUntil = new Date(options.now.getTime() + options.lockMs);
    const [rows] = await this.dataSource.query<[OutboxRow[], number]>(
      CLAIM_SQL,
      [
        options.now,
        options.limit,
        lockUntil,
        options.instanceId,
        OutboxPublishState.PENDING,
      ],
    );

    return rows
      .map(mapRow)
      .sort(
        (a, b) =>
          a.occurredAt.getTime() - b.occurredAt.getTime() ||
          compareSequences(a.sequence, b.sequence),
      );
  }

  async countPending(): Promise<number> {
    const rows = await this.dataSource.query<Array<{ count: string }>>(
      `
      SELECT count(*) AS "count"
      FROM "outbox_events"
      WHERE "publishedAt" IS NULL
        AND "publishState" = $1
      `,
      [OutboxPublishState.PENDING],
    );
    return Number(rows[0]?.count ?? 0);
  }

  async markPublished(id: string, publishedAt: Date): Promise<void> {
    await this.dataSource.query(
      `
      UPDATE "outbox_events"
      SET "publishedAt" = $1,
          "publishState" = $2,
          "lockedUntil" = NULL,
          "lockedBy" = NULL,
          "lastError" = NULL
      WHERE "id" = $3
        AND "publishedAt" IS NULL
      `,
      [publishedAt, OutboxPublishState.PUBLISHED, id],
    );
  }

  async markRetry(id: string, update: RetryUpdate): Promise<void> {
    await this.dataSource.query(
      `
      UPDATE "outbox_events"
      SET "attempts" = $1,
          "nextAttemptAt" = $2,
          "lastError" = $3,
          "publishState" = $4,
          "lockedUntil" = NULL,
          "lockedBy" = NULL
      WHERE "id" = $5
        AND "publishedAt" IS NULL
      `,
      [
        update.attempts,
        update.nextAttemptAt,
        update.lastError,
        update.state,
        id,
      ],
    );
  }
}

function mapRow(row: OutboxRow): OutboxEventRecord {
  return {
    id: row.id,
    sequence: row.sequence,
    eventId: row.eventId,
    eventType: row.eventType,
    aggregateType: row.aggregateType,
    aggregateId: row.aggregateId,
    version: row.version,
    correlationId: row.correlationId,
    payload: row.payload,
    occurredAt: toDate(row.occurredAt),
    publishedAt: row.publishedAt ? toDate(row.publishedAt) : null,
    publishState: row.publishState,
    attempts: row.attempts,
    nextAttemptAt: toDate(row.nextAttemptAt),
    lastError: row.lastError,
    lockedUntil: row.lockedUntil ? toDate(row.lockedUntil) : null,
    lockedBy: row.lockedBy,
    createdAt: toDate(row.createdAt),
  };
}

function toDate(value: Date | string): Date {
  return value instanceof Date ? value : new Date(value);
}

function compareSequences(left: string, right: string): number {
  const a = BigInt(left);
  const b = BigInt(right);
  if (a === b) {
    return 0;
  }
  return a < b ? -1 : 1;
}
