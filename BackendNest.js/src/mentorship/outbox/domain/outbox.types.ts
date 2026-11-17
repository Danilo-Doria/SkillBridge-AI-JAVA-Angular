/**
 * Tipos puros del Transactional Outbox (HU-MNT-08).
 * Este archivo no importa Nest, TypeORM ni Kafka.
 */

export enum OutboxPublishState {
  PENDING = 'PENDING',
  PUBLISHED = 'PUBLISHED',
  FAILED = 'FAILED',
}

export const MENTORSHIP_AGGREGATE_TYPE = 'Mentorship';

export interface OutboxEventRecord {
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

/** Entrada estructural compartida por el append y la escritura atómica de MNT-07. */
export interface OutboxEventInput {
  eventId: string;
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  version: number;
  correlationId: string | null;
  payload: Record<string, unknown>;
  occurredAt: Date;
}

/** Envelope `BusinessEvent` publicado en Kafka (contrato JSON de events.md). */
export interface BusinessEventEnvelope {
  eventId: string;
  eventType: string;
  aggregateId: string;
  aggregateType: string;
  occurredAt: string;
  correlationId?: string;
  version: number;
  payload: Record<string, unknown>;
}

export interface RetrySchedule {
  attempts: number;
  nextAttemptAt: Date;
  final: boolean;
}

export interface OutboxPublisherConfig {
  enabled: boolean;
  brokers: string[];
  topic: string;
  clientId: string;
  pollIntervalMs: number;
  batchSize: number;
  maxAttempts: number;
  backoffBaseMs: number;
  backoffMaxMs: number;
  lockMs: number;
  requestTimeoutMs: number;
  nodeEnv: string;
}

export const DEFAULT_OUTBOX_PUBLISHER_CONFIG: OutboxPublisherConfig = {
  enabled: false,
  brokers: ['localhost:9092'],
  topic: 'booking-events',
  clientId: 'skillbridge-mentorship',
  pollIntervalMs: 1000,
  batchSize: 50,
  maxAttempts: 10,
  backoffBaseMs: 500,
  backoffMaxMs: 60_000,
  lockMs: 30_000,
  requestTimeoutMs: 10_000,
  nodeEnv: 'development',
};
