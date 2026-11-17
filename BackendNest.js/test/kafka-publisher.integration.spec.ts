import type { ConfigService } from '@nestjs/config';
import { KafkaContainer, type StartedKafkaContainer } from '@testcontainers/kafka';
import { Kafka, logLevel } from 'kafkajs';
import { randomUUID } from 'node:crypto';
import type { KafkaConfig } from '../src/config/configuration.js';
import { KafkaEventPublisher } from '../src/mentorship/outbox/adapters/kafka-event-publisher.js';
import { buildBusinessEventEnvelope } from '../src/mentorship/outbox/domain/event-catalog.js';
import {
  DEFAULT_OUTBOX_PUBLISHER_CONFIG,
  type OutboxEventInput,
} from '../src/mentorship/outbox/domain/outbox.types.js';

const runKafkaTests = process.env.RUN_KAFKA_TESTS === '1';
const describeKafka = runKafkaTests ? describe : describe.skip;

describeKafka('KafkaEventPublisher (Testcontainers Kafka)', () => {
  let container: StartedKafkaContainer;
  let publisher: KafkaEventPublisher;
  let bootstrapServers: string;
  let topic: string;

  beforeAll(async () => {
    container = await new KafkaContainer('confluentinc/cp-kafka:7.6.0')
      .withKraft()
      .start();
    bootstrapServers = `${container.getHost()}:${container.getMappedPort(9093)}`;
    topic = `booking-events-${randomUUID().slice(0, 8)}`;
    const config: KafkaConfig = {
      ...DEFAULT_OUTBOX_PUBLISHER_CONFIG,
      enabled: true,
      brokers: [bootstrapServers],
      topic,
      requestTimeoutMs: 15_000,
    };
    const configService = {
      get: <T>(key: string): T | undefined =>
        key === 'kafka' ? (config as T) : undefined,
    } as unknown as ConfigService;
    publisher = new KafkaEventPublisher(configService);
  }, 180_000);

  afterAll(async () => {
    await publisher?.onModuleDestroy();
    await container?.stop();
  });

  it('publica el envelope con acks=all y lo deja consumible', async () => {
    const kafka = new Kafka({
      clientId: 'outbox-test',
      brokers: [bootstrapServers],
      logLevel: logLevel.NOTHING,
      connectionTimeout: 15_000,
      requestTimeout: 15_000,
    });
    const admin = kafka.admin();
    await admin.connect();
    await admin.createTopics({
      topics: [{ topic, numPartitions: 1, replicationFactor: 1 }],
    });
    await admin.disconnect();

    const assignmentId = randomUUID();
    const event: OutboxEventInput = {
      eventId: randomUUID(),
      eventType: 'MentorshipCompleted',
      aggregateType: 'Mentorship',
      aggregateId: assignmentId,
      version: 1,
      correlationId: 'corr-kafka',
      occurredAt: new Date('2026-11-16T14:00:00.000Z'),
      payload: {
        assignmentId,
        completedAt: '2026-11-16T13:59:00.000Z',
      },
    };

    await publisher.publish(event);

    const consumer = kafka.consumer({
      groupId: `outbox-test-${randomUUID()}`,
      logLevel: logLevel.NOTHING,
    });
    await consumer.connect();
    await consumer.subscribe({ topic, fromBeginning: true });

    const received = new Promise<string>((resolve, reject) => {
      const timer = setTimeout(
        () => reject(new Error('No se recibió el evento en 15s')),
        15_000,
      );
      void consumer
        .run({
          eachMessage: async ({ message }) => {
            clearTimeout(timer);
            resolve(message.value?.toString() ?? '');
          },
        })
        .catch(() => undefined);
    });

    const raw = await received;
    await consumer.disconnect();

    expect(JSON.parse(raw)).toEqual(buildBusinessEventEnvelope(event));
  }, 90_000);
});
