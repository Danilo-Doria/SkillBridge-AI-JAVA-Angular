import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { OutboxEventEntity } from '../entities/outbox-event.entity.js';
import { KafkaEventPublisher } from './adapters/kafka-event-publisher.js';
import { TypeOrmOutboxRepository } from './adapters/typeorm-outbox.repository.js';
import { OutboxAppendService } from './outbox-append.service.js';
import { OutboxMetrics } from './outbox.metrics.js';
import { OutboxPublisherService } from './outbox-publisher.service.js';
import { EVENT_PUBLISHER } from './ports/event-publisher.port.js';
import { OUTBOX_REPOSITORY } from './ports/outbox.repository.js';

@Module({
  imports: [TypeOrmModule.forFeature([OutboxEventEntity])],
  providers: [
    OutboxAppendService,
    OutboxMetrics,
    OutboxPublisherService,
    { provide: OUTBOX_REPOSITORY, useClass: TypeOrmOutboxRepository },
    { provide: EVENT_PUBLISHER, useClass: KafkaEventPublisher },
  ],
  exports: [OutboxAppendService, OutboxPublisherService, OutboxMetrics],
})
export class OutboxModule {}
