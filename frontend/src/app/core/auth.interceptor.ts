import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const AUTH_PATHS = ['/auth/login', '/auth/register', '/auth/logout', '/auth/me'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Asegurar que las cookies viajen en peticiones cross-origin
  const clonedReq = req.clone({
    withCredentials: true
  });

  return next(clonedReq).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthCall = AUTH_PATHS.some(p => req.url.includes(p));
      if (err.status === 401 && !isAuthCall && auth.isAuthenticated()) {
        auth.clearSession();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    })
  );
};
