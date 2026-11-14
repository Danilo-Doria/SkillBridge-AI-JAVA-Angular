import {
  Column, CreateDateColumn, Entity, Index, PrimaryGeneratedColumn,
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
  @Column('uuid')
  studentId: string;

  // 1 pago = 1 mentoría: el UNIQUE lo garantiza a nivel de BD
  @Index({ unique: true })
  @Column('uuid')
  paymentId: string;

  @Column({ type: 'enum', enum: MentorshipStatus, default: MentorshipStatus.SCHEDULED })
  status: MentorshipStatus;

  @Column({ type: 'int', default: 0 })
  progressPercent: number;

  @Column({ type: 'timestamptz' })
  accessStartsAt: Date;

  @Column({ type: 'timestamptz' })
  accessEndsAt: Date;

  @Column({ type: 'timestamptz', nullable: true })
  completedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}