import type { OutboxEventInput } from '../domain/outbox.types.js';

export interface EventPublisherPort {
  publish(event: OutboxEventInput): Promise<void>;
}

export const EVENT_PUBLISHER = Symbol('EVENT_PUBLISHER');
