import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';

/** Emisor fijado por el monolito (docs/contracts/mentorships/security.md). */
export const JWT_ISSUER = 'skillbridge';

export interface AuthenticatedUser {
  /** Claim `uid`: UUID del usuario. `sub` es el correo y NO se usa como id. */
  id: string;
  email: string;
  role: string;
}

export interface AuthenticatedRequest {
  user: AuthenticatedUser;
}

interface SkillBridgeClaims {
  uid?: unknown;
  email?: unknown;
  role?: unknown;
}

/**
 * Valida el JWT RS256 emitido por el monolito con `JWT_PUBLIC_KEY` y exige rol CUSTOMER.
 * Solo acepta `Authorization: Bearer` (la cookie `access_token` requeriría además CSRF).
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
  ) {}

  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const req = ctx.switchToHttp().getRequest<{
      headers: Record<string, string | undefined>;
      user?: AuthenticatedUser;
    }>();
    const [type, token] = req.headers.authorization?.split(' ') ?? [];
    if (type !== 'Bearer' || !token) throw new UnauthorizedException();

    // Clave ausente = error de configuración (500), no un 401 silencioso.
    const publicKey = this.config
      .getOrThrow<string>('JWT_PUBLIC_KEY')
      .replace(/\\n/g, '\n'); // permite la clave en una sola línea del .env

    let claims: SkillBridgeClaims;
    try {
      claims = await this.jwt.verifyAsync<SkillBridgeClaims & object>(token, {
        publicKey,
        algorithms: ['RS256'],
        issuer: JWT_ISSUER,
      });
    } catch {
      throw new UnauthorizedException();
    }

    if (typeof claims.uid !== 'string' || typeof claims.role !== 'string') {
      throw new UnauthorizedException();
    }

    req.user = {
      id: claims.uid,
      email: typeof claims.email === 'string' ? claims.email : '',
      role: claims.role,
    };

    if (req.user.role !== 'CUSTOMER') throw new ForbiddenException();
    return true;
  }
}
