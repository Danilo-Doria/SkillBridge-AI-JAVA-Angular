import { ApiProperty } from '@nestjs/swagger';
import { IsInt, Max, Min } from 'class-validator';

export class UpdateProgressDto {
  @ApiProperty({
    description:
      'Porcentaje de avance. 100 completa la mentoría de forma irreversible.',
    minimum: 0,
    maximum: 100,
    example: 60,
  })
  @IsInt()
  @Min(0)
  @Max(100)
  progressPercent: number;
}
