import {
  Injectable,
  Logger,
  type OnModuleDestroy,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Kafka, logLevel, type Producer } from 'kafkajs';
import type { KafkaConfig } from '../../../config/configuration.js';
import { buildBusinessEventEnvelope } from '../domain/event-catalog.js';
import {
  DEFAULT_OUTBOX_PUBLISHER_CONFIG,
  type OutboxEventInput,
} from '../domain/outbox.types.js';
import type { EventPublisherPort } from '../ports/event-publisher.port.js';

@Injectable()
export class KafkaEventPublisher implements EventPublisherPort, OnModuleDestroy {
  private readonly logger = new Logger(KafkaEventPublisher.name);
  private readonly config: KafkaConfig;
  private producer?: Producer;
  private connected = false;
  private connecting?: Promise<void>;

  constructor(configService: ConfigService) {
    this.config =
      configService.get<KafkaConfig>('kafka') ??
      DEFAULT_OUTBOX_PUBLISHER_CONFIG;
  }

  async publish(event: OutboxEventInput): Promise<void> {
    if (!this.config.enabled) {
      throw new Error('Kafka está deshabilitado para el publicador de outbox');
    }

    await this.ensureConnected();
    const envelope = buildBusinessEventEnvelope(event);
    await this.getProducer().send({
      topic: this.config.topic,
      acks: -1,
      timeout: this.config.requestTimeoutMs,
      messages: [
        {
          key: event.aggregateId,
          value: JSON.stringify(envelope),
        },
      ],
    });

    this.logger.debug(
      `Evento publicado eventId=${event.eventId} eventType=${event.eventType} aggregateId=${event.aggregateId} correlationId=${event.correlationId ?? '-'}`,
    );
  }

  async onModuleDestroy(): Promise<void> {
    if (!this.producer) {
      return;
    }
    try {
      await this.producer.disconnect();
    } catch (error) {
      this.logger.warn(
        `No se pudo desconectar el productor Kafka: ${errorMessage(error)}`,
      );
    } finally {
      this.connected = false;
      this.producer = undefined;
    }
  }

  private async ensureConnected(): Promise<void> {
    if (this.connected) {
      return;
    }
    if (!this.connecting) {
      this.connecting = (async () => {
        const producer = this.getProducer();
        await producer.connect();
        this.connected = true;
      })().finally(() => {
        this.connecting = undefined;
      });
    }
    await this.connecting;
  }

  private getProducer(): Producer {
    if (!this.producer) {
      const kafka = new Kafka({
        clientId: this.config.clientId,
        brokers: this.config.brokers,
        connectionTimeout: this.config.requestTimeoutMs,
        requestTimeout: this.config.requestTimeoutMs,
        logLevel: logLevel.NOTHING,
      });
      this.producer = kafka.producer({
        idempotent: true,
        maxInFlightRequests: 1,
        allowAutoTopicCreation: false,
      });
    }
    return this.producer;
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
