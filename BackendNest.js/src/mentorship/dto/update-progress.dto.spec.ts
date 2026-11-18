import 'reflect-metadata';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { UpdateProgressDto } from './update-progress.dto.js';

async function errorsFor(body: Record<string, unknown>) {
  const dto = plainToInstance(UpdateProgressDto, body);
  return validate(dto);
}

describe('UpdateProgressDto', () => {
  it.each([0, 60, 100])('acepta %i', async (value) => {
    await expect(errorsFor({ progressPercent: value })).resolves.toHaveLength(
      0,
    );
  });

  it.each([-1, 101, 50.5])('rechaza %d', async (value) => {
    const errors = await errorsFor({ progressPercent: value });
    expect(errors.map((e) => e.property)).toContain('progressPercent');
  });

  it('rechaza el campo ausente', async () => {
    const errors = await errorsFor({});
    expect(errors.map((e) => e.property)).toContain('progressPercent');
  });
});
