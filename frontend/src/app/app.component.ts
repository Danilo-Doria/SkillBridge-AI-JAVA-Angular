import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="sticky top-0 z-50 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">

        <!-- Logo / Marca -->
        <a
          routerLink="/"
          class="flex items-center gap-2.5 transition-opacity hover:opacity-80"
        >
          <span
            class="flex h-9 w-9 items-center justify-center rounded-xl
                   bg-slate-900 text-sm font-bold text-white shadow-sm"
          >
            S
          </span>

          <span class="text-lg font-bold tracking-tight text-slate-900">
            SkillBridge
            <span class="text-blue-600">AI</span>
          </span>
        </a>

        <!-- Navegación -->
        <nav class="hidden items-center gap-1 md:flex">

          <a
            routerLink="/"
            routerLinkActive="bg-slate-100 text-slate-900"
            [routerLinkActiveOptions]="{ exact: true }"
            class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                  transition hover:bg-slate-50 hover:text-slate-900"
          >
            Servicios
          </a>

          <a
            routerLink="/book"
            routerLinkActive="bg-slate-100 text-slate-900"
            class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                   transition hover:bg-slate-50 hover:text-slate-900"
          >
            Reservar
          </a>

          <a
            routerLink="/ai"
            routerLinkActive="bg-slate-100 text-slate-900"
            class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                   transition hover:bg-slate-50 hover:text-slate-900"
          >
            IA
          </a>

          <a
            routerLink="/bookings/me"
            routerLinkActive="bg-slate-100 text-slate-900"
            class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                   transition hover:bg-slate-50 hover:text-slate-900"
          >
            Mis reservas
          </a>

          <!-- Usuario no autenticado -->
          @if (!auth.isAuthenticated()) {

            <a
              routerLink="/login"
              class="ml-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm
                    font-semibold text-slate-700 shadow-sm transition
                    hover:bg-slate-900 hover:text-white"
            >
              Ingresar
            </a>

          } @else {

            <!-- Usuario autenticado -->
            <button
              type="button"
              (click)="auth.logout()"
              class="ml-2 rounded-xl border border-slate-200 bg-white px-4 py-2
                     text-sm font-semibold text-slate-700 transition
                     hover:border-slate-300 hover:bg-slate-50"
            >
              Salir
            </button>

          }

        </nav>

        <!-- Menú móvil -->
        <button
          type="button"
          class="rounded-lg p-2 text-slate-600 transition
                 hover:bg-slate-100 md:hidden"
          aria-label="Abrir menú"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            class="h-6 w-6"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            stroke-width="1.8"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M4 6h16M4 12h16M4 18h16"
            />
          </svg>
        </button>

      </div>
    </header>

    <!-- Contenido de cada ruta -->
    <main>
      <router-outlet />
    </main>
  `,
})
export class AppComponent {
  // Permite consultar el estado de autenticación y cerrar sesión.
  auth = inject(AuthService);
}