import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { AuthenticatedRequest } from '../jwt-auth.guard.js';
import { Role } from '../Roles/role.enum.js';
import { ROLES_KEY } from '../Decoradores/Roles.decorador.js';

@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const required = this.reflector.getAllAndOverride<Role[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    // Sin @Roles(), la ruta solo exige estar autenticado.
    if (!required || required.length === 0) return true;

    const { user } = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const userRoles: string[] = Array.isArray(user?.role)
      ? user.role
      : user?.role
        ? [user.role]
        : [];

    if (!required.some((role) => userRoles.includes(role))) {
      throw new ForbiddenException(
        'No tienes el rol necesario para este recurso',
      );
    }
    return true;
  }
}
