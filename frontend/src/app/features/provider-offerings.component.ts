import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { Category, CategoryService } from '../core/category.service';
import { Offering, OfferingPayload, OfferingService } from '../core/offering.service';

@Component({
  standalone: true,
  imports: [FormsModule, CurrencyPipe],
  template: `
    <section class="min-h-screen bg-slate-50 px-4 py-8 sm:px-6 lg:px-8">
      <div class="mx-auto max-w-7xl">
        <div class="mb-6">
          <span class="inline-flex items-center rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-blue-700">
            Gestión de catálogo
          </span>
          <h1 class="mt-3 text-3xl font-bold tracking-tight text-slate-900">
            {{ isAdmin ? 'Todos los servicios (Admin)' : 'Mis servicios' }}
          </h1>
          <p class="mt-2 max-w-3xl text-sm text-slate-500">
            Crea servicios usando las categorías disponibles y administra tu catálogo sin hacer crecer toda la página.
          </p>
        </div>

        @if (error) {
          <div role="alert" class="mb-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {{ error }}
          </div>
        }

        <div class="grid items-start gap-6 lg:grid-cols-2">
          <!-- Columna izquierda: formularios -->
          <div class="flex min-w-0 flex-col gap-6">
            <form class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" (ngSubmit)="save()">
              <div class="mb-4">
                <h2 class="text-lg font-semibold text-slate-900">
                  {{ editingId ? 'Editar servicio' : 'Nuevo servicio' }}
                </h2>
                <p class="mt-1 text-sm text-slate-500">Completa los datos del servicio.</p>
              </div>

              @if (isAdmin && !editingId) {
                <input class="mb-3 w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                       name="providerId" [(ngModel)]="form.providerId"
                       placeholder="providerId (UUID del Provider dueño)" required>
              }

              <div class="grid gap-3 sm:grid-cols-2">
                <input class="w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                       name="title" [(ngModel)]="form.title" placeholder="Título" required>

                <select class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                        name="category" [(ngModel)]="form.category" required>
                  <option value="">Selecciona una categoría</option>
                  @for (category of categories; track category.id) {
                    <option [value]="category.name">{{ category.name }}</option>
                  }
                </select>
              </div>

              @if (categories.length === 0) {
                <p class="mt-2 text-xs text-amber-700">
                  No hay categorías disponibles. Crea una categoría en el formulario inferior.
                </p>
              }

              <textarea class="mt-3 min-h-28 w-full resize-y rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                        name="description" [(ngModel)]="form.description" placeholder="Descripción" required></textarea>

              <input class="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                     type="number" min="0" name="price" [(ngModel)]="form.price" placeholder="Precio" required>

              <div class="mt-4 flex flex-wrap gap-2">
                <button type="submit" [disabled]="loading || categories.length === 0"
                        class="cursor-pointer rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-900 disabled:cursor-not-allowed disabled:opacity-50">
                  {{ editingId ? 'Guardar cambios' : 'Crear servicio' }}
                </button>
                @if (editingId) {
                  <button type="button" (click)="reset()"
                          class="cursor-pointer rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">
                    Cancelar
                  </button>
                }
              </div>
            </form>

            <form class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" (ngSubmit)="createCategory()">
              <div class="mb-4">
                <h2 class="text-lg font-semibold text-slate-900">Crear categoría</h2>
                <p class="mt-1 text-sm text-slate-500">La nueva categoría quedará disponible inmediatamente para crear servicios.</p>
              </div>

              <div class="flex flex-col gap-3 sm:flex-row">
                <input class="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                       name="newCategory" [(ngModel)]="newCategoryName"
                       placeholder="Ej. Marketing Digital" maxlength="80" required>
                <button type="submit" [disabled]="categoryLoading"
                        class="cursor-pointer rounded-lg bg-blue-700 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-800 disabled:cursor-not-allowed disabled:opacity-50">
                  {{ categoryLoading ? 'Creando...' : 'Crear categoría' }}
                </button>
              </div>
            </form>
          </div>

          <!-- Columna derecha: listados independientes -->
          <div class="flex min-w-0 flex-col gap-6">
            <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div class="mb-4 flex items-center justify-between gap-3">
                <div>
                  <h2 class="text-lg font-semibold text-slate-900">Servicios creados</h2>
                  <p class="text-sm text-slate-500">{{ offerings.length }} servicio{{ offerings.length === 1 ? '' : 's' }}</p>
                </div>
              </div>

              <div class="max-h-[520px] space-y-4 overflow-y-auto pr-2">
                @for (o of offerings; track o.id) {
                  <article class="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between"
                           [class.opacity-60]="!o.active">
                    <div class="min-w-0">
                      <div class="flex flex-wrap items-center gap-2">
                        <h3 class="font-bold text-slate-900">{{ o.title }}</h3>
                        <span class="rounded bg-blue-50 px-2 py-0.5 text-xs font-bold text-blue-800">{{ o.category }}</span>
                        @if (!o.active) {
                          <span class="rounded bg-slate-200 px-2 py-0.5 text-xs font-bold text-slate-600">Inactivo</span>
                        }
                      </div>
                      <p class="mt-1 text-sm text-slate-500">{{ o.description }}</p>
                      <p class="mt-1 text-sm font-semibold text-slate-800">{{ o.price | currency:'COP':'symbol-narrow':'1.0-0' }}</p>
                    </div>

                    <div class="flex shrink-0 gap-2">
                      <button type="button" (click)="edit(o)"
                              class="cursor-pointer rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-semibold hover:bg-slate-50">
                        Editar
                      </button>
                      @if (o.active) {
                        <button type="button" (click)="toggle(o)"
                                class="cursor-pointer rounded-lg border border-red-300 bg-white px-3 py-1.5 text-sm font-semibold text-red-700 hover:bg-red-50">
                          Desactivar
                        </button>
                      } @else {
                        <button type="button" (click)="toggle(o)"
                                class="cursor-pointer rounded-lg border border-emerald-300 bg-white px-3 py-1.5 text-sm font-semibold text-emerald-700 hover:bg-emerald-50">
                          Activar
                        </button>
                      }
                    </div>
                  </article>
                } @empty {
                  <p class="rounded-2xl border border-dashed border-slate-300 px-6 py-10 text-center text-sm text-slate-500">
                    No hay servicios todavía.
                  </p>
                }
              </div>
            </section>

            <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div class="mb-4 flex items-center justify-between gap-3">
                <div>
                  <h2 class="text-lg font-semibold text-slate-900">Categorías</h2>
                  <p class="text-sm text-slate-500">{{ categories.length }} categoría{{ categories.length === 1 ? '' : 's' }}</p>
                </div>
              </div>

              <div class="max-h-[280px] space-y-2 overflow-y-auto pr-2">
                @for (category of categories; track category.id) {
                  <div class="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                    <span class="font-medium text-slate-800">{{ category.name }}</span>
                    <span class="text-xs font-semibold text-emerald-700">Activa</span>
                  </div>
                } @empty {
                  <p class="rounded-xl border border-dashed border-slate-300 px-4 py-8 text-center text-sm text-slate-500">
                    No hay categorías registradas.
                  </p>
                }
              </div>
            </section>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class ProviderOfferingsComponent implements OnInit {
  private service = inject(OfferingService);
  private categoryService = inject(CategoryService);
  private auth = inject(AuthService);

  offerings: Offering[] = [];
  categories: Category[] = [];
  form: Partial<OfferingPayload> = {};
  newCategoryName = '';
  editingId: string | null = null;
  loading = false;
  categoryLoading = false;
  error = '';

  get isAdmin(): boolean { return this.auth.hasRole('ADMIN'); }

  ngOnInit(): void {
    this.load();
    this.loadCategories();
  }

  load(): void {
    const req = this.isAdmin ? this.service.listAll() : this.service.listMine();
    req.subscribe({
      next: r => this.offerings = r,
      error: e => this.fail(e, 'No fue posible cargar los servicios.'),
    });
  }

  loadCategories(): void {
    this.categoryService.list().subscribe({
      next: r => this.categories = r,
      error: e => this.fail(e, 'No fue posible cargar las categorías.'),
    });
  }

  save(): void {
    this.error = '';
    const p = this.form as OfferingPayload;
    if (!p.title || !p.description || !p.category || p.price == null) {
      this.error = 'Completa todos los campos del servicio.';
      return;
    }

    this.loading = true;
    const req = this.editingId ? this.service.update(this.editingId, p) : this.service.create(p);
    req.subscribe({
      next: () => {
        this.loading = false;
        this.reset();
        this.load();
      },
      error: e => {
        this.loading = false;
        this.fail(e, 'No fue posible guardar el servicio.');
      },
    });
  }

  createCategory(): void {
    this.error = '';
    const name = this.newCategoryName.trim();
    if (!name) {
      this.error = 'Escribe el nombre de la categoría.';
      return;
    }

    this.categoryLoading = true;
    this.categoryService.create({ name }).subscribe({
      next: category => {
        this.categories = [...this.categories, category].sort((a, b) => a.name.localeCompare(b.name));
        this.form.category = category.name;
        this.newCategoryName = '';
        this.categoryLoading = false;
      },
      error: e => {
        this.categoryLoading = false;
        this.fail(e, 'No fue posible crear la categoría.');
      },
    });
  }

  edit(o: Offering): void {
    this.editingId = o.id;
    this.form = { title: o.title, description: o.description, category: o.category, price: o.price };
  }

  toggle(o: Offering): void {
    this.error = '';
    const req = o.active ? this.service.deactivate(o.id) : this.service.activate(o.id);
    req.subscribe({
      next: () => this.load(),
      error: e => this.fail(e, 'No fue posible cambiar el estado.'),
    });
  }

  reset(): void {
    this.form = {};
    this.editingId = null;
  }

  private fail(e: HttpErrorResponse, fallback: string): void {
    if (e.status === 403) this.error = 'No tienes permiso para realizar esta acción.';
    else if (e.status === 401) this.error = 'Tu sesión expiró. Inicia sesión de nuevo.';
    else this.error = e?.error?.detail || fallback;
  }
}
