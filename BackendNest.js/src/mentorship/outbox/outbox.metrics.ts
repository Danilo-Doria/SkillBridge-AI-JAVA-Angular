import { Injectable } from '@nestjs/common';

export interface OutboxMetricsSnapshot {
  pending: number;
  claimed: number;
  published: number;
  retried: number;
  failed: number;
  lastPublishLatencyMs: number | null;
  averagePublishLatencyMs: number | null;
}

/**
 * Contadores en memoria del publicador. Se exponen por snapshot para pruebas
 * y pueden conectarse a un exporter de métricas sin cambiar el caso de uso.
 */
@Injectable()
export class OutboxMetrics {
  private pending = 0;
  private claimed = 0;
  private published = 0;
  private retried = 0;
  private failed = 0;
  private publishLatencyTotalMs = 0;
  private lastPublishLatencyMs: number | null = null;

  recordPending(count: number): void {
    this.pending = count;
  }

  recordClaimed(count: number): void {
    this.claimed += count;
  }

  recordPublished(latencyMs = 0): void {
    this.published += 1;
    this.publishLatencyTotalMs += latencyMs;
    this.lastPublishLatencyMs = latencyMs;
  }

  recordRetry(final: boolean): void {
    this.retried += 1;
    if (final) {
      this.failed += 1;
    }
  }

  snapshot(): OutboxMetricsSnapshot {
    return {
      pending: this.pending,
      claimed: this.claimed,
      published: this.published,
      retried: this.retried,
      failed: this.failed,
      lastPublishLatencyMs: this.lastPublishLatencyMs,
      averagePublishLatencyMs:
        this.published === 0
          ? null
          : Math.round(this.publishLatencyTotalMs / this.published),
    };
  }
}
