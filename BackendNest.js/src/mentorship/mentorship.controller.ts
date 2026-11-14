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
  JwtAuthGuard,
  type AuthenticatedRequest,
} from '../auth/jwt-auth.guard.js';
import { UpdateProgressDto } from './dto/update-progress.dto.js';
import { MentorshipService } from './mentorship.service.js';

@Controller('api/v1/mentorships')
@UseGuards(JwtAuthGuard)
export class MentorshipController {
  constructor(private readonly service: MentorshipService) {}

  @Get('me')
  findMine(@Req() req: AuthenticatedRequest) {
    return this.service.findMine(req.user.id);
  }

  @Patch(':id/progress')
  updateProgress(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateProgressDto,
    @Req() req: AuthenticatedRequest,
  ) {
    return this.service.updateProgress(id, req.user.id, dto.progressPercent);
  }
}
