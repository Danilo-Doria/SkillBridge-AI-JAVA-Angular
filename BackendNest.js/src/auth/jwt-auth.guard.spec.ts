import { generateKeyPairSync } from 'node:crypto';
import {
  ExecutionContext,
  ForbiddenException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { JWT_ISSUER, JwtAuthGuard } from './jwt-auth.guard.js';

const keys = generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
});
const otherKeys = generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
});

const UID = '11111111-1111-4111-8111-111111111111';
const jwt = new JwtService();

/** Firma como lo hace el monolito: sub = correo, uid = UUID, iss = skillbridge. */
function sign(
  payload: Record<string, unknown> = {},
  opts: { privateKey?: string; issuer?: string; expiresIn?: number } = {},
) {
  return jwt.sign(
    { sub: 'ana@test.com', uid: UID, email: 'ana@test.com', role: 'CUSTOMER', ...payload },
    {
      privateKey: opts.privateKey ?? keys.privateKey,
      algorithm: 'RS256',
      issuer: opts.issuer ?? JWT_ISSUER,
      expiresIn: opts.expiresIn ?? 3600,
    },
  );
}

function context(authorization?: string) {
  const req: { headers: Record<string, string | undefined>; user?: unknown } = {
    headers: { authorization },
  };
  const ctx = {
    switchToHttp: () => ({ getRequest: () => req }),
  } as unknown as ExecutionContext;
  return { ctx, req };
}

/** `null` = variable no configurada (`undefined` activaría el valor por defecto). */
function guardWithKey(publicKey: string | null = keys.publicKey) {
  const config = {
    getOrThrow: (key: string) => {
      if (key !== 'JWT_PUBLIC_KEY' || publicKey === null) {
        throw new TypeError(`Configuration key "${key}" does not exist`);
      }
      return publicKey;
    },
  } as unknown as ConfigService;
  return new JwtAuthGuard(jwt, config);
}

describe('JwtAuthGuard', () => {
  it('acepta un CUSTOMER válido y toma el id del claim uid (no de sub)', async () => {
    const { ctx, req } = context(`Bearer ${sign()}`);

    await expect(guardWithKey().canActivate(ctx)).resolves.toBe(true);
    expect(req.user).toEqual({ id: UID, email: 'ana@test.com', role: 'CUSTOMER' });
  });

  it('acepta la clave pública en una sola línea con \\n literales', async () => {
    const oneLine = keys.publicKey.replace(/\n/g, '\\n');
    const { ctx } = context(`Bearer ${sign()}`);

    await expect(guardWithKey(oneLine).canActivate(ctx)).resolves.toBe(true);
  });

  it.each([
    ['sin cabecera', undefined],
    ['esquema distinto de Bearer', `Basic abc`],
    ['Bearer sin token', 'Bearer '],
    ['token basura', 'Bearer no.es.jwt'],
  ])('%s → 401', async (_name, header) => {
    await expect(guardWithKey().canActivate(context(header).ctx)).rejects.toThrow(
      UnauthorizedException,
    );
  });

  it('firmado con otra clave → 401', async () => {
    const token = sign({}, { privateKey: otherKeys.privateKey });
    await expect(
      guardWithKey().canActivate(context(`Bearer ${token}`).ctx),
    ).rejects.toThrow(UnauthorizedException);
  });

  it('emisor distinto de skillbridge → 401', async () => {
    const token = sign({}, { issuer: 'otro' });
    await expect(
      guardWithKey().canActivate(context(`Bearer ${token}`).ctx),
    ).rejects.toThrow(UnauthorizedException);
  });

  it('token expirado → 401', async () => {
    const token = sign({}, { expiresIn: -10 });
    await expect(
      guardWithKey().canActivate(context(`Bearer ${token}`).ctx),
    ).rejects.toThrow(UnauthorizedException);
  });

  it('HS256 (secreto heredado) → 401 aunque la firma sea válida para ese secreto', async () => {
    const token = jwt.sign(
      { uid: UID, role: 'CUSTOMER' },
      { secret: 'x'.repeat(32), algorithm: 'HS256', issuer: JWT_ISSUER },
    );
    await expect(
      guardWithKey().canActivate(context(`Bearer ${token}`).ctx),
    ).rejects.toThrow(UnauthorizedException);
  });

  it('sin claim uid → 401', async () => {
    const token = sign({ uid: undefined });
    await expect(
      guardWithKey().canActivate(context(`Bearer ${token}`).ctx),
    ).rejects.toThrow(UnauthorizedException);
  });

  it.each(['PROVIDER', 'ADMIN'])('rol %s → 403', async (role) => {
    const token = sign({ role });
    await expect(
      guardWithKey().canActivate(context(`Bearer ${token}`).ctx),
    ).rejects.toThrow(ForbiddenException);
  });

  it('JWT_PUBLIC_KEY ausente → error de configuración, no 401', async () => {
    const promise = guardWithKey(null).canActivate(
      context(`Bearer ${sign()}`).ctx,
    );
    await expect(promise).rejects.toThrow();
    await expect(promise).rejects.not.toBeInstanceOf(UnauthorizedException);
  });
});
