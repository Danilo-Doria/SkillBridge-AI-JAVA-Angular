import {
  Inject,
  Injectable,
  Logger,
  type OnModuleDestroy,
  type OnModuleInit,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { randomUUID } from 'node:crypto';
import type { KafkaConfig } from '../../config/configuration.js';
import { validateOutboxEvent } from './domain/event-catalog.js';
import {
  DEFAULT_OUTBOX_PUBLISHER_CONFIG,
  OutboxPublishState,
  type OutboxEventRecord,
} from './domain/outbox.types.js';
import { OutboxMetrics } from './outbox.metrics.js';
import {
  EVENT_PUBLISHER,
  type EventPublisherPort,
} from './ports/event-publisher.port.js';
import {
  OUTBOX_REPOSITORY,
  type OutboxRepository,
} from './ports/outbox.repository.js';

@Injectable()
export class OutboxPublisherService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(OutboxPublisherService.name);
  private readonly config: KafkaConfig;
  private readonly instanceId = `mentorship-${process.pid}-${randomUUID()}`;
  private timer?: NodeJS.Timeout;
  private running = false;
  private stopped = false;

  constructor(
    @Inject(OUTBOX_REPOSITORY)
    private readonly repository: OutboxRepository,
    @Inject(EVENT_PUBLISHER)
    private readonly publisher: EventPublisherPort,
    configService: ConfigService,
    private readonly metrics: OutboxMetrics,
  ) {
    this.config =
      configService.get<KafkaConfig>('kafka') ??
      DEFAULT_OUTBOX_PUBLISHER_CONFIG;
  }

  onModuleInit(): void {
    if (!this.config.enabled) {
      this.logger.warn(
        'Publicador de outbox deshabilitado (KAFKA_ENABLED=false). Los eventos quedan pendientes en PostgreSQL.',
      );
      return;
    }
    if (this.config.brokers.length === 0) {
      throw new Error(
        'KAFKA_BOOTSTRAP_SERVERS es obligatorio cuando el publicador de outbox está habilitado',
      );
    }

    this.timer = setInterval(() => {
      void this.runOnce();
    }, this.config.pollIntervalMs);
    this.timer.unref();
    void this.runOnce();
  }

  onModuleDestroy(): void {
    this.stopped = true;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  /** Ejecuta un ciclo de publicación. Público para pruebas y operación manual. */
  async runOnce(): Promise<number> {
    if (this.running || this.stopped || !this.config.enabled) {
      return 0;
    }

    this.running = true;
    try {
      this.metrics.recordPending(await this.repository.countPending());
      const events = await this.repository.claimBatch({
        limit: this.config.batchSize,
        now: new Date(),
        lockMs: this.config.lockMs,
        instanceId: this.instanceId,
      });
      if (events.length === 0) {
        return 0;
      }

      this.metrics.recordClaimed(events.length);
      let published = 0;

      for (const event of events) {
        if (this.stopped) {
          break;
        }
        try {
          validateOutboxEvent(event);
        } catch (error) {
          await this.handlePermanentFailure(event, error);
          continue;
        }
        try {
          const startedAt = Date.now();
          await this.publisher.publish(event);
          await this.repository.markPublished(event.id, new Date());
          this.metrics.recordPublished(Date.now() - startedAt);
          published += 1;
        } catch (error) {
          await this.handleFailure(event, error);
        }
      }

      const snapshot = this.metrics.snapshot();
      this.logger.debug(
        `Ciclo de outbox: pending=${snapshot.pending} claimed=${events.length} published=${published} retriedTotal=${snapshot.retried} failedTotal=${snapshot.failed} lastPublishLatencyMs=${snapshot.lastPublishLatencyMs ?? '-'}`,
      );
      return published;
    } catch (error) {
      this.logger.error(
        `No se pudo reclamar el lote de outbox: ${errorMessage(error)}`,
      );
      return 0;
    } finally {
      this.running = false;
    }
  }

  private async handlePermanentFailure(
    event: OutboxEventRecord,
    error: unknown,
  ): Promise<void> {
    const message = truncate(errorMessage(error));
    try {
      await this.repository.markRetry(event.id, {
        attempts: event.attempts + 1,
        nextAttemptAt: new Date(),
        lastError: message,
        state: OutboxPublishState.FAILED,
      });
    } catch (markError) {
      this.logger.error(
        `No se pudo registrar el evento inválido ${event.eventId}: ${errorMessage(markError)}`,
      );
    }

    this.metrics.recordRetry(true);
    this.logger.error(
      `Evento inválido marcado FAILED sin publicar: eventId=${event.eventId} eventType=${event.eventType} aggregateId=${event.aggregateId} correlationId=${event.correlationId ?? '-'} error=${message}`,
    );
  }

  private async handleFailure(
    event: OutboxEventRecord,
    error: unknown,
  ): Promise<void> {
    const attempts = event.attempts + 1;
    const final = attempts >= this.config.maxAttempts;
    const nextAttemptAt = final
      ? new Date(Date.now() + this.config.backoffMaxMs)
      : new Date(Date.now() + this.backoffFor(attempts));
    const message = truncate(errorMessage(error));

    try {
      await this.repository.markRetry(event.id, {
        attempts,
        nextAttemptAt,
        lastError: message,
        state: final ? OutboxPublishState.FAILED : OutboxPublishState.PENDING,
      });
    } catch (markError) {
      this.logger.error(
        `No se pudo registrar el fallo del evento ${event.eventId}: ${errorMessage(markError)}`,
      );
    }

    this.metrics.recordRetry(final);
    const context = `eventId=${event.eventId} eventType=${event.eventType} aggregateId=${event.aggregateId} correlationId=${event.correlationId ?? '-'} attempts=${attempts}`;

    if (final) {
      this.logger.error(
        `Evento marcado FAILED tras ${attempts} intentos: ${context} error=${message}`,
      );
    } else {
      this.logger.warn(
        `Fallo al publicar, reintento programado: ${context} nextAttemptAt=${nextAttemptAt.toISOString()} error=${message}`,
      );
    }
  }

  private backoffFor(attempts: number): number {
    const exponential =
      this.config.backoffBaseMs * 2 ** Math.max(0, attempts - 1);
    const capped = Math.min(exponential, this.config.backoffMaxMs);
    const jitter = capped * 0.2 * Math.random();
    return Math.min(
      this.config.backoffMaxMs,
      Math.round(capped * 0.8 + jitter),
    );
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function truncate(value: string, max = 2000): string {
  return value.length > max ? `${value.slice(0, max)}…` : value;
}
