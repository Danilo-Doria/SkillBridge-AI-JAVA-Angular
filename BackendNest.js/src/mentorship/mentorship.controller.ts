import {
  Body,
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Patch,
  Req,
  UseGuards,
} from '@nestjs/common';
import {
  ApiBadRequestResponse,
  ApiBearerAuth,
  ApiBody,
  ApiForbiddenResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiTags,
  ApiUnauthorizedResponse,
  ApiUnprocessableEntityResponse,
} from '@nestjs/swagger';
import {
  JwtAuthGuard,
  type AuthenticatedRequest,
} from '../auth/jwt-auth.guard.js';
import { MentorshipResponseDto } from './dto/mentorship-response.dto.js';
import { UpdateProgressDto } from './dto/update-progress.dto.js';
import { MentorshipService } from './mentorship.service.js';

@ApiTags('Mentorships')
@ApiBearerAuth()
@ApiUnauthorizedResponse({ description: 'JWT ausente, inválido o expirado' })
@Controller('api/v1/mentorships')
@UseGuards(JwtAuthGuard)
export class MentorshipController {
  constructor(private readonly service: MentorshipService) {}

  @Get('me')
  @ApiOperation({
    summary: 'Consultar mis mentorías',
    description:
      'Requiere rol CUSTOMER. Devuelve las asignaciones del estudiante autenticado, ordenadas por accessStartsAt descendente.',
  })
  @ApiOkResponse({
    type: MentorshipResponseDto,
    isArray: true,
    description: 'Lista de mentorías propias',
  })
  @ApiForbiddenResponse({ description: 'El usuario no tiene rol CUSTOMER' })
  findMine(@Req() req: AuthenticatedRequest) {
    return this.service.findMine(req.user.id);
  }

  @Patch(':id/progress')
  @ApiOperation({
    summary: 'Reportar porcentaje de avance',
    description:
      '1 pago = 1 mentoría. De 1 a 99 pasa a IN_PROGRESS. Con 100 pasa a COMPLETED, registra completedAt y el pago queda consumido (irreversible).',
  })
  @ApiParam({
    name: 'id',
    format: 'uuid',
    example: 'a1a1a1a1-0000-4000-a000-000000000001',
  })
  @ApiBody({
    type: UpdateProgressDto,
    examples: {
      intermedio: {
        summary: 'Progreso intermedio (IN_PROGRESS)',
        value: { progressPercent: 60 },
      },
      finalizar: {
        summary: 'Finalizar (COMPLETED, irreversible)',
        value: { progressPercent: 100 },
      },
      invalido: { summary: 'Inválido (400)', value: { progressPercent: 150 } },
    },
  })
  @ApiOkResponse({
    type: MentorshipResponseDto,
    description: 'Progreso actualizado',
  })
  @ApiBadRequestResponse({
    description:
      'id no es UUID, progressPercent inválido (fuera de 0-100 o no entero), o mentoría en estado terminal (COMPLETED, CANCELLED, NO_SHOW)',
  })
  @ApiForbiddenResponse({
    description: 'Rol distinto de CUSTOMER, o fuera de la ventana de acceso',
  })
  @ApiNotFoundResponse({
    description: 'La mentoría no existe o no pertenece al usuario',
  })
  @ApiUnprocessableEntityResponse({
    description: 'El progreso no puede bajar respecto al valor actual',
  })
  updateProgress(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateProgressDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.service.updateProgress(id, req.user.id, dto.progressPercent);
  }
}
