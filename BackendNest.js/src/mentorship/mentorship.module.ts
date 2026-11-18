import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtAuthGuard } from '../auth/jwt-auth.guard.js';
import { MentorshipAssignmentEntity } from './entities/mentorship-assignment.entity.js';
import { MentorshipController } from './mentorship.controller.js';
import { MentorshipService } from './mentorship.service.js';

@Module({
  imports: [
    TypeOrmModule.forFeature([MentorshipAssignmentEntity]),
    // Sin clave aquí: el guard pasa la pública (JWT_PUBLIC_KEY) en cada verificación.
    JwtModule.register({}),
  ],
  controllers: [MentorshipController],
  providers: [MentorshipService, JwtAuthGuard],
})
export class MentorshipModule {}
