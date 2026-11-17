import type { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Metadata de publicación del outbox (HU-MNT-08).
 *
 * Aditiva sobre `outbox_events`: no edita la migración inicial de MNT-07 y
 * conserva `publishedAt` como fuente de verdad de "publicado".
 */
export class OutboxPublicationMetadata1800000000000 implements MigrationInterface {
  name = 'OutboxPublicationMetadata1800000000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      ALTER TABLE "outbox_events"
        ADD COLUMN "sequence" bigserial,
        ADD COLUMN "publishState" varchar(16) NOT NULL DEFAULT 'PENDING',
        ADD COLUMN "attempts" int NOT NULL DEFAULT 0,
        ADD COLUMN "nextAttemptAt" timestamptz NOT NULL DEFAULT now(),
        ADD COLUMN "lastError" text,
        ADD COLUMN "lockedUntil" timestamptz,
        ADD COLUMN "lockedBy" varchar(100)
    `);

    await queryRunner.query(`
      UPDATE "outbox_events"
      SET "publishState" = 'PUBLISHED'
      WHERE "publishedAt" IS NOT NULL
    `);

    await queryRunner.query(`
      CREATE INDEX "idx_outbox_events_publication_queue"
      ON "outbox_events" ("publishState", "nextAttemptAt")
      WHERE "publishedAt" IS NULL
    `);

    await queryRunner.query(`
      CREATE INDEX "idx_outbox_events_aggregate_order"
      ON "outbox_events" ("aggregateId", "occurredAt", "sequence")
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP INDEX "idx_outbox_events_aggregate_order"`);
    await queryRunner.query(`DROP INDEX "idx_outbox_events_publication_queue"`);
    await queryRunner.query(`
      ALTER TABLE "outbox_events"
        DROP COLUMN "lockedBy",
        DROP COLUMN "lockedUntil",
        DROP COLUMN "lastError",
        DROP COLUMN "nextAttemptAt",
        DROP COLUMN "attempts",
        DROP COLUMN "publishState",
        DROP COLUMN "sequence"
    `);
  }
}
