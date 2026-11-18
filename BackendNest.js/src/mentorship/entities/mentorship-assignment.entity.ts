import {
  Column,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  VersionColumn,
} from 'typeorm';

export enum MentorshipStatus {
  SCHEDULED = 'SCHEDULED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  NO_SHOW = 'NO_SHOW',
}

@Entity('mentorship_assignments')
export class MentorshipAssignmentEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'customer_id', type: 'uuid' })
  studentId: string;

  // 1 pago = 1 mentoría: UNIQUE uk_assignment_booking en la BD
  @Column({ name: 'booking_id', type: 'uuid', unique: true })
  paymentId: string;

  @Column({ name: 'offering_id', type: 'uuid' })
  offeringId: string;

  @Column({ type: 'varchar', length: 30 })
  mode: string;

  @Column({ type: 'varchar', length: 30, default: MentorshipStatus.SCHEDULED })
  status: MentorshipStatus;

  @Column({ name: 'progress_percent', type: 'int', default: 0 })
  progressPercent: number;

  @Column({ name: 'access_starts_at', type: 'timestamptz' })
  accessStartsAt: Date;

  @Column({ name: 'access_ends_at', type: 'timestamptz' })
  accessEndsAt: Date;

  @Column({ name: 'completed_at', type: 'timestamptz', nullable: true })
  completedAt: Date | null;

  @VersionColumn({ type: 'bigint' })
  version: string;
}
