import { Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { type EntityManager } from 'typeorm';
import { OutboxEventEntity } from '../entities/outbox-event.entity.js';
import { validateOutboxEvent } from './domain/event-catalog.js';
import {
  MENTORSHIP_AGGREGATE_TYPE,
  OutboxPublishState,
  type OutboxEventInput,
} from './domain/outbox.types.js';

export interface OutboxAppendInput {
  eventId?: string;
  eventType: string;
  aggregateType?: string;
  aggregateId: string;
  version?: number;
  correlationId?: string | null;
  payload: Record<string, unknown>;
  occurredAt?: Date;
}

/**
 * Puerta de aplicación para anexar eventos al outbox dentro de la transacción
 * de negocio. No conoce Kafka ni el publicador.
 */
@Injectable()
export class OutboxAppendService {
  async append(
    manager: EntityManager,
    input: OutboxAppendInput,
  ): Promise<OutboxEventEntity> {
    const event: OutboxEventInput = {
      eventId: input.eventId ?? randomUUID(),
      eventType: input.eventType,
      aggregateType: input.aggregateType ?? MENTORSHIP_AGGREGATE_TYPE,
      aggregateId: input.aggregateId,
      version: input.version ?? 1,
      correlationId: input.correlationId ?? null,
      payload: input.payload,
      occurredAt: input.occurredAt ?? new Date(),
    };

    validateOutboxEvent(event);

    const repository = manager.getRepository(OutboxEventEntity);
    const now = new Date();
    const entity = repository.create({
      id: randomUUID(),
      ...event,
      publishedAt: null,
      publishState: OutboxPublishState.PENDING,
      attempts: 0,
      nextAttemptAt: now,
      lastError: null,
      lockedUntil: null,
      lockedBy: null,
    });

    return repository.save(entity);
  }
}
