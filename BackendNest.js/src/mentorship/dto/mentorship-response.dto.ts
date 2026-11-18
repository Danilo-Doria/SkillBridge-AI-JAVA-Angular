import { ApiProperty } from '@nestjs/swagger';
import { MentorshipStatus } from '../entities/mentorship-assignment.entity.js';

export class MentorshipResponseDto {
  @ApiProperty({ example: 'a1a1a1a1-0000-4000-a000-000000000001' })
  id: string;

  @ApiProperty({ example: 'b7d9f3c1-a8e4-4c59-b1d6-8f2a3e9c4b7d' })
  studentId: string;

  @ApiProperty({ example: 'bbbbbbbb-0000-0000-0000-000000000001' })
  paymentId: string;

  @ApiProperty({ example: '11111111-1111-1111-1111-111111111111' })
  offeringId: string;

  @ApiProperty({ example: 'LIVE' })
  mode: string;

  @ApiProperty({
    enum: MentorshipStatus,
    example: MentorshipStatus.IN_PROGRESS,
  })
  status: MentorshipStatus;

  @ApiProperty({ example: 60, minimum: 0, maximum: 100 })
  progressPercent: number;

  @ApiProperty({ example: '2026-11-16T22:39:13.654Z' })
  accessStartsAt: Date;

  @ApiProperty({ example: '2026-12-17T22:39:13.654Z' })
  accessEndsAt: Date;

  @ApiProperty({
    example: null,
    nullable: true,
    type: String,
    format: 'date-time',
  })
  completedAt: Date | null;
}
