import type {
  OutboxEventRecord,
  OutboxPublishState,
} from '../domain/outbox.types.js';

export interface ClaimOptions {
  limit: number;
  now: Date;
  lockMs: number;
  instanceId: string;
}

export interface RetryUpdate {
  attempts: number;
  nextAttemptAt: Date;
  lastError: string;
  state: OutboxPublishState;
}

export interface OutboxRepository {
  claimBatch(options: ClaimOptions): Promise<OutboxEventRecord[]>;
  countPending(): Promise<number>;
  markPublished(id: string, publishedAt: Date): Promise<void>;
  markRetry(id: string, update: RetryUpdate): Promise<void>;
}

export const OUTBOX_REPOSITORY = Symbol('OUTBOX_REPOSITORY');
