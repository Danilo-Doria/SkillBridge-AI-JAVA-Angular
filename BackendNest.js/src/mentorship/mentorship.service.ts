import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
  UnprocessableEntityException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Not, Repository } from 'typeorm';
import {
  MentorshipAssignmentEntity,
  MentorshipStatus,
} from './entities/mentorship-assignment.entity.js';

/** Estados sin salida: el pago ya está consumido o la asignación cerrada. */
export const TERMINAL_STATUSES = [
  MentorshipStatus.COMPLETED,
  MentorshipStatus.CANCELLED,
  MentorshipStatus.NO_SHOW,
];

@Injectable()
export class MentorshipService {
  constructor(
    @InjectRepository(MentorshipAssignmentEntity)
    private readonly repo: Repository<MentorshipAssignmentEntity>,
  ) {}

  /** Solo lee: en este servicio las asignaciones las crea únicamente el evento de pago. */
  findMine(studentId: string) {
    return this.repo.find({
      where: { studentId },
      order: { accessStartsAt: 'DESC' },
    });
  }

  async updateProgress(id: string, studentId: string, percent: number) {
    // Filtrar por dueño: una mentoría ajena responde 404 y no revela que existe.
    const m = await this.repo.findOne({ where: { id, studentId } });
    if (!m) throw new NotFoundException('Mentoría no encontrada');

    if (TERMINAL_STATUSES.includes(m.status)) {
      throw new BadRequestException(
        `La mentoría está en estado terminal (${m.status}) y no admite cambios de progreso`,
      );
    }

    // Ventana semiabierta [accessStartsAt, accessEndsAt)
    const now = new Date();
    if (now < m.accessStartsAt || now >= m.accessEndsAt) {
      throw new ForbiddenException('Fuera de la ventana de acceso');
    }

    // Progreso monótono: bajarlo sería "reiniciar" la mentoría.
    if (percent < m.progressPercent) {
      throw new UnprocessableEntityException(
        `El progreso no puede bajar (actual ${m.progressPercent}%)`,
      );
    }

    const done = percent === 100;
    const status = done
      ? MentorshipStatus.COMPLETED
      : percent > 0
        ? MentorshipStatus.IN_PROGRESS
        : m.status;

    // UPDATE condicionado: si otro request completó la mentoría en paralelo,
    // affected = 0 y se rechaza (el pago consumido no se puede reiniciar).
    const res = await this.repo.update(
      { id, studentId, status: Not(In(TERMINAL_STATUSES)) },
      {
        progressPercent: percent,
        status,
        completedAt: done ? now : null,
      },
    );

    if (!res.affected) {
      throw new BadRequestException(
        'La mentoría ya no admite cambios de progreso',
      );
    }
    return this.repo.findOneByOrFail({ id });
  }
}
