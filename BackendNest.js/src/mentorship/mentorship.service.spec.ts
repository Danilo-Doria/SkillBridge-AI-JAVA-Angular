import {
  BadRequestException,
  ForbiddenException,
  NotFoundException,
  UnprocessableEntityException,
} from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  MentorshipAssignmentEntity,
  MentorshipStatus,
} from './entities/mentorship-assignment.entity.js';
import { MentorshipService } from './mentorship.service.js';

const NOW = new Date('2026-11-13T15:00:00Z');
const STUDENT = '11111111-1111-4111-8111-111111111111';
const ID = '22222222-2222-4222-8222-222222222222';

function assignment(
  overrides: Partial<MentorshipAssignmentEntity> = {},
): MentorshipAssignmentEntity {
  return {
    id: ID,
    studentId: STUDENT,
    paymentId: '33333333-3333-4333-8333-333333333333',
    status: MentorshipStatus.SCHEDULED,
    progressPercent: 0,
    accessStartsAt: new Date('2026-11-13T14:00:00Z'),
    accessEndsAt: new Date('2026-11-13T16:00:00Z'),
    completedAt: null,
    createdAt: new Date('2026-11-01T00:00:00Z'),
    ...overrides,
  };
}

describe('MentorshipService', () => {
  let service: MentorshipService;
  const repo = {
    find: vi.fn(),
    findOne: vi.fn(),
    update: vi.fn(),
    findOneByOrFail: vi.fn(),
  };

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    Object.values(repo).forEach((fn) => fn.mockReset());

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        MentorshipService,
        {
          provide: getRepositoryToken(MentorshipAssignmentEntity),
          useValue: repo,
        },
      ],
    }).compile();

    service = module.get(MentorshipService);
  });

  afterEach(() => vi.useRealTimers());

  describe('findMine', () => {
    it('filtra por el estudiante y ordena por accessStartsAt descendente', async () => {
      const rows = [assignment()];
      repo.find.mockResolvedValue(rows);

      await expect(service.findMine(STUDENT)).resolves.toBe(rows);
      expect(repo.find).toHaveBeenCalledWith({
        where: { studentId: STUDENT },
        order: { accessStartsAt: 'DESC' },
      });
    });
  });

  describe('updateProgress', () => {
    it('progreso intermedio (60) → IN_PROGRESS y actualiza el porcentaje', async () => {
      repo.findOne.mockResolvedValue(assignment());
      repo.update.mockResolvedValue({ affected: 1 });
      repo.findOneByOrFail.mockResolvedValue(
        assignment({ status: MentorshipStatus.IN_PROGRESS, progressPercent: 60 }),
      );

      const result = await service.updateProgress(ID, STUDENT, 60);

      expect(repo.update).toHaveBeenCalledWith(
        expect.objectContaining({ id: ID, studentId: STUDENT }),
        {
          progressPercent: 60,
          status: MentorshipStatus.IN_PROGRESS,
          completedAt: null,
        },
      );
      expect(result.status).toBe(MentorshipStatus.IN_PROGRESS);
    });

    it('100 → COMPLETED con completedAt = now()', async () => {
      repo.findOne.mockResolvedValue(
        assignment({ status: MentorshipStatus.IN_PROGRESS, progressPercent: 60 }),
      );
      repo.update.mockResolvedValue({ affected: 1 });
      repo.findOneByOrFail.mockResolvedValue(assignment());

      await service.updateProgress(ID, STUDENT, 100);

      expect(repo.update).toHaveBeenCalledWith(expect.anything(), {
        progressPercent: 100,
        status: MentorshipStatus.COMPLETED,
        completedAt: NOW,
      });
    });

    it('0 sobre SCHEDULED no inicia la mentoría', async () => {
      repo.findOne.mockResolvedValue(assignment());
      repo.update.mockResolvedValue({ affected: 1 });
      repo.findOneByOrFail.mockResolvedValue(assignment());

      await service.updateProgress(ID, STUDENT, 0);

      expect(repo.update).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ status: MentorshipStatus.SCHEDULED }),
      );
    });

    it.each([
      MentorshipStatus.COMPLETED,
      MentorshipStatus.CANCELLED,
      MentorshipStatus.NO_SHOW,
    ])('estado terminal %s → 400 y no escribe', async (status) => {
      repo.findOne.mockResolvedValue(
        assignment({ status, progressPercent: 100 }),
      );

      await expect(service.updateProgress(ID, STUDENT, 0)).rejects.toThrow(
        BadRequestException,
      );
      expect(repo.update).not.toHaveBeenCalled();
    });

    it('carrera: otro request la completó entre lectura y escritura → 400', async () => {
      repo.findOne.mockResolvedValue(assignment());
      repo.update.mockResolvedValue({ affected: 0 });

      await expect(service.updateProgress(ID, STUDENT, 50)).rejects.toThrow(
        BadRequestException,
      );
    });

    it('mentoría inexistente o de otro estudiante → 404', async () => {
      repo.findOne.mockResolvedValue(null);

      await expect(service.updateProgress(ID, STUDENT, 50)).rejects.toThrow(
        NotFoundException,
      );
      expect(repo.findOne).toHaveBeenCalledWith({
        where: { id: ID, studentId: STUDENT },
      });
    });

    it('antes de la ventana → 403', async () => {
      repo.findOne.mockResolvedValue(
        assignment({ accessStartsAt: new Date('2026-11-13T15:00:01Z') }),
      );

      await expect(service.updateProgress(ID, STUDENT, 50)).rejects.toThrow(
        ForbiddenException,
      );
    });

    it('justo en accessEndsAt (ventana semiabierta) → 403', async () => {
      repo.findOne.mockResolvedValue(assignment({ accessEndsAt: NOW }));

      await expect(service.updateProgress(ID, STUDENT, 50)).rejects.toThrow(
        ForbiddenException,
      );
    });

    it('bajar el progreso → 422', async () => {
      repo.findOne.mockResolvedValue(
        assignment({ status: MentorshipStatus.IN_PROGRESS, progressPercent: 60 }),
      );

      await expect(service.updateProgress(ID, STUDENT, 30)).rejects.toThrow(
        UnprocessableEntityException,
      );
      expect(repo.update).not.toHaveBeenCalled();
    });
  });
});
