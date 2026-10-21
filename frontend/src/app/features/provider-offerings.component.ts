import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { Category, CategoryService } from '../core/category.service';
import { Offering, OfferingPayload, OfferingService } from '../core/offering.service';
import { MentorshipResource, MentorshipResourceService, ResourcePayload } from '../core/mentorship-resource.service';
import { firstValueFrom } from 'rxjs';

interface ResourceDraft extends ResourcePayload { id?: string; file?: File; fileName?: string; }

@Component({
  standalone: true,
  imports: [FormsModule, CurrencyPipe],
  template: `
    <section class="min-h-screen bg-[#FBFBFA] px-4 py-16 sm:px-6 lg:py-24">
      <div class="mx-auto max-w-4xl">

        <!-- Encabezado: una sola idea, una sola acción -->
        <header class="enter-rise flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 class="font-display text-5xl text-[#111111] sm:text-6xl">
              {{ isAdmin ? 'Todos los servicios' : 'Mis servicios' }}
            </h1>
            <p class="mt-4 text-base text-[#787774]">
              {{ offerings.length }} {{ offerings.length === 1 ? 'servicio' : 'servicios' }}
              · {{ activeCount }} {{ activeCount === 1 ? 'activo' : 'activos' }}
            </p>
          </div>

          <button type="button" (click)="openNew()"
                  class="inline-flex cursor-pointer items-center gap-2 self-start rounded-md bg-[#111111] px-4 py-2.5 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] motion-reduce:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#111111] sm:self-auto">
            <svg class="h-3.5 w-3.5" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
              <path d="M224 128a8 8 0 0 1-8 8h-80v80a8 8 0 0 1-16 0v-80H40a8 8 0 0 1 0-16h80V40a8 8 0 0 1 16 0v80h80a8 8 0 0 1 8 8Z"/>
            </svg>
            Nuevo servicio
          </button>
        </header>

        @if (error && !panelOpen) {
          <div role="alert" class="enter-rise mt-8 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">
            {{ error }}
          </div>
        }

        <!-- Lista editorial: filas separadas por una línea, sin tarjetas -->
        <ul class="mt-14 border-t border-[#EAEAEA]">
          @for (o of offerings; track o.id; let i = $index) {
            <li class="enter-rise flex flex-col gap-4 border-b border-[#EAEAEA] py-6 sm:flex-row sm:items-center sm:gap-8"
                [style.animation-delay.ms]="(i < 8 ? i : 8) * 40">

              <div class="min-w-0 flex-1 transition-opacity duration-200" [class.opacity-50]="!o.active">
                <h3 class="truncate text-lg font-medium tracking-[-0.01em] text-[#111111]">{{ o.title }}</h3>
                <p class="mt-1 truncate text-sm text-[#787774]">{{ o.description }}</p>
              </div>

              <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
                <span class="rounded-md bg-[#E1F3FE] px-2 py-0.5 text-xs font-medium text-[#1F6C9F]"
                      [class.opacity-50]="!o.active">{{ o.category }}</span>

                <span class="w-28 text-sm font-medium text-[#111111] tabular-nums sm:text-right"
                      [class.opacity-50]="!o.active">
                  {{ o.price | currency:'COP':'symbol-narrow':'1.0-0' }}
                </span>

                <!-- Interruptor activo / inactivo -->
                <button type="button" role="switch" (click)="toggle(o)"
                        [attr.aria-checked]="o.active"
                        [attr.aria-label]="(o.active ? 'Desactivar ' : 'Activar ') + o.title"
                        [class]="o.active ? 'bg-[#111111]' : 'bg-[#DADAD7]'"
                        class="relative h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-[cubic-bezier(0.23,1,0.32,1)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#111111]">
                  <span class="absolute left-0.5 top-0.5 h-4 w-4 rounded-full bg-white transition-transform duration-200 ease-[cubic-bezier(0.23,1,0.32,1)] motion-reduce:transition-none"
                        [class.translate-x-4]="o.active"></span>
                </button>

                <button type="button" (click)="edit(o)"
                        class="cursor-pointer rounded-md px-2.5 py-1.5 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100">
                  Editar
                </button>
              </div>
            </li>
          } @empty {
            <li class="py-24 text-center">
              <p class="font-display text-3xl text-[#111111]">
                {{ offeringsLoading ? 'Cargando servicios…' : 'Aún no hay servicios' }}
              </p>
              @if (!offeringsLoading) {
                <p class="mx-auto mt-3 max-w-sm text-sm text-[#787774]">Crea el primero y aparecerá aquí, listo para recibir reservas.</p>
              }
            </li>
          }
        </ul>

        <!-- Categorías: secundario, compacto, al final -->
        <div class="mt-20">
          <h2 class="text-sm font-medium text-[#111111]">Categorías</h2>

          <div class="mt-4 flex flex-wrap gap-2">
            @for (category of categories; track category.id) {
              <span class="rounded-md bg-[#F1F0ED] px-3 py-1.5 text-sm text-[#2F3437]">{{ category.name }}</span>
            } @empty {
              <p class="text-sm text-[#787774]">{{ categoriesLoading ? 'Cargando categorías…' : 'Todavía no hay categorías.' }}</p>
            }
          </div>

          <form class="mt-5 flex max-w-sm gap-2" (ngSubmit)="createCategory()">
            <input class="min-w-0 flex-1 rounded-md border border-[#EAEAEA] bg-white px-3 py-2 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                   name="newCategory" [(ngModel)]="newCategoryName"
                   placeholder="Nueva categoría" maxlength="80" required>
            <button type="submit" [disabled]="categoryLoading"
                    class="cursor-pointer rounded-md px-3 py-2 text-sm font-medium text-[#111111] transition-[background-color,transform,opacity] duration-150 hover:bg-[#F1F0ED] active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100">
              {{ categoryLoading ? 'Añadiendo…' : 'Añadir' }}
            </button>
          </form>
        </div>
      </div>
    </section>

    <!-- Panel lateral: entra y sale por el mismo borde -->
    <div (click)="closePanel()"
         class="fixed inset-0 z-40 bg-[#111111]/20 transition-opacity duration-300 ease-out"
         [class.opacity-100]="panelOpen" [class.opacity-0]="!panelOpen"
         [class.pointer-events-auto]="panelOpen" [class.pointer-events-none]="!panelOpen"></div>

    <aside class="fixed inset-y-0 right-0 z-50 w-full max-w-md border-l border-[#EAEAEA] bg-white shadow-[0_8px_40px_rgba(0,0,0,0.06)] transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] motion-reduce:transition-none"
           [class.translate-x-0]="panelOpen" [class.translate-x-full]="!panelOpen"
           [attr.inert]="panelOpen ? null : ''" [attr.aria-hidden]="!panelOpen"
           (keydown.escape)="closePanel()" role="dialog" aria-modal="true" aria-label="Formulario de servicio">

      <form class="flex h-full flex-col" (ngSubmit)="save()">
        <div class="flex items-center justify-between px-8 pb-2 pt-8">
          <h2 class="font-display text-3xl text-[#111111]">{{ editingId ? 'Editar servicio' : 'Nuevo servicio' }}</h2>
          <button type="button" (click)="reset()" aria-label="Cerrar"
                  class="cursor-pointer rounded-md p-2 text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.94] motion-reduce:active:scale-100">
            <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
              <path d="M205.66 194.34a8 8 0 0 1-11.32 11.32L128 139.31l-66.34 66.35a8 8 0 0 1-11.32-11.32L116.69 128 50.34 61.66a8 8 0 0 1 11.32-11.32L128 116.69l66.34-66.35a8 8 0 0 1 11.32 11.32L139.31 128Z"/>
            </svg>
          </button>
        </div>

        <div class="flex-1 space-y-7 overflow-y-auto px-8 py-6">

          @if (error) {
            <div role="alert" class="enter-rise rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">{{ error }}</div>
          }

          @if (isAdmin && !editingId) {
            <div>
              <label class="mb-2 block text-sm text-[#787774]">ID del proveedor</label>
              <input class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                     name="providerId" [(ngModel)]="form.providerId"
                     placeholder="UUID del proveedor dueño" required>
            </div>
          }

          <div>
            <label class="mb-2 block text-sm text-[#787774]">Título</label>
            <input class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                   name="title" [(ngModel)]="form.title" placeholder="Ej. Asesoría financiera" required>
          </div>

          <fieldset class="space-y-4 border-t border-[#EAEAEA] pt-6" [disabled]="loading || resourceLoading">
            <legend class="text-sm font-medium">Recursos del servicio</legend>
            <p class="text-xs text-[#787774]">Archivos del PC (cualquier formato, máximo 10 MB), enlaces a páginas o videos de YouTube y otras plataformas.</p>
            @if (resourceLoading) { <p role="status" class="text-sm">Cargando recursos…</p> }
            @if (resourceError) { <p role="alert" class="text-sm text-[#9F2F2D]">{{ resourceError }}</p> }
            @for (resource of draftResources; track resource; let i = $index) {
              <div class="space-y-3 rounded-md border border-[#EAEAEA] p-3">
                <label class="block text-sm">Título del recurso
                  <input [name]="'resourceTitle' + i" [(ngModel)]="resource.title" maxlength="160" required class="mt-1 w-full rounded-md border px-3 py-2">
                </label>
                <label class="block text-sm">Tipo
                  <select [name]="'resourceType' + i" [(ngModel)]="resource.resourceType" (ngModelChange)="changeResourceType(resource)" class="mt-1 w-full rounded-md border px-3 py-2">
                    <option value="DOCUMENT">Archivo del PC</option><option value="LINK">Enlace</option><option value="VIDEO">Video</option>
                  </select>
                </label>
                @if (resource.resourceType === 'DOCUMENT') {
                  @if (resource.fileName) { <p class="break-all text-xs">{{ resource.fileName }}</p> }
                  @if (!resource.fileName && resource.url) { <p class="break-all text-xs">Documento actual: {{ resource.url }}</p> }
                  <label class="block text-sm">{{ resource.url || resource.file ? 'Reemplazar archivo' : 'Seleccionar archivo' }}
                    <input type="file" (change)="selectFile($event, resource)" class="mt-2 block w-full text-xs">
                  </label>
                } @else {
                  <label class="block text-sm">{{ resource.resourceType === 'VIDEO' ? 'Enlace del video o streaming' : 'Enlace a la página' }}
                    <input type="url" [name]="'resourceUrl' + i" [(ngModel)]="resource.url" placeholder="https://..." maxlength="1500" required class="mt-1 w-full rounded-md border px-3 py-2">
                  </label>
                }
                <button type="button" (click)="removeDraft(i)" class="text-sm text-[#9F2F2D]">Quitar</button>
              </div>
            }
            @if (!draftResources.length && !resourceLoading) { <p class="text-sm text-[#787774]">No hay recursos añadidos.</p> }
            <button type="button" (click)="addDraft()" class="rounded-md border px-3 py-2 text-sm">Añadir material</button>
            <p class="text-xs text-[#787774]">Los cambios en los recursos se aplican al guardar el servicio.</p>
          </fieldset>

          <!-- Categoría: selección directa, sin desplegable -->
          <div>
            <label class="mb-2 block text-sm text-[#787774]">Categoría</label>
            <div class="flex flex-wrap gap-2">
              @for (category of categories; track category.id) {
                <button type="button" (click)="form.category = category.name"
                        [class]="form.category === category.name ? 'bg-[#111111] text-white' : 'bg-[#F1F0ED] text-[#2F3437] hover:bg-[#E9E8E4]'"
                        class="cursor-pointer rounded-md px-3 py-1.5 text-sm font-medium transition-[background-color,color,transform] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] active:scale-[0.97] motion-reduce:active:scale-100">
                  {{ category.name }}
                </button>
              }
            </div>
            @if (categories.length === 0) {
              <p class="mt-2 text-sm text-[#956400]">Crea primero una categoría en la página principal.</p>
            }
          </div>

          <div>
            <label class="mb-2 block text-sm text-[#787774]">Descripción</label>
            <textarea class="min-h-[110px] w-full resize-y rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm leading-6 text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                      name="description" [(ngModel)]="form.description" placeholder="Qué incluye el servicio" required></textarea>
          </div>

          <div>
            <label class="mb-2 block text-sm text-[#787774]">Precio (COP)</label>
            <input class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] tabular-nums outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                   type="number" min="0" name="price" [(ngModel)]="form.price" placeholder="0" required>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 border-t border-[#EAEAEA] px-8 py-5">
          <button type="button" (click)="reset()"
                  class="cursor-pointer rounded-md px-4 py-2.5 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100">
            Cancelar
          </button>
          <button type="submit" [disabled]="loading || resourceLoading || !!resourceError || categories.length === 0"
                  class="cursor-pointer rounded-md bg-[#111111] px-5 py-2.5 text-sm font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100">
            {{ loading ? 'Guardando…' : (editingId ? 'Guardar cambios' : 'Crear servicio') }}
          </button>
        </div>
      </form>
    </aside>
  `,
})
export class ProviderOfferingsComponent implements OnInit {
  private service = inject(OfferingService);
  private categoryService = inject(CategoryService);
  private auth = inject(AuthService);
  private resourceService = inject(MentorshipResourceService);

  offerings: Offering[] = [];
  categories: Category[] = [];
  form: Partial<OfferingPayload> = {};
  newCategoryName = '';
  editingId: string | null = null;
  loading = false;
  categoryLoading = false;
  offeringsLoading = true;
  categoriesLoading = true;
  error = '';
  draftResources: ResourceDraft[] = [];
  removedResourceIds: string[] = [];
  resourceLoading = false;
  resourceError = '';
  private resourceLoadVersion = 0;

  // Se conserva por compatibilidad (el selector ahora son chips)
  categoryDropdownOpen = false;

  // NUEVO: estado del panel lateral
  panelOpen = false;

  get isAdmin(): boolean { return this.auth.hasRole('ADMIN'); }
  get activeCount(): number { return this.offerings.filter(o => o.active).length; }

  ngOnInit(): void {
    this.load();
    this.loadCategories();
  }

  load(): void {
    const req = this.isAdmin ? this.service.listAll() : this.service.listMine();
    req.subscribe({
      next: r => { this.offerings = r; this.offeringsLoading = false; },
      error: e => { this.offeringsLoading = false; this.fail(e, 'No fue posible cargar los servicios.'); },
    });
  }

  loadCategories(): void {
    this.categoryService.list().subscribe({
      next: r => { this.categories = r; this.categoriesLoading = false; },
      error: e => { this.categoriesLoading = false; this.fail(e, 'No fue posible cargar las categorías.'); },
    });
  }

  async save(): Promise<void> {
    if (this.loading || this.resourceLoading || this.resourceError) return;
    this.error = '';
    const p = this.form as OfferingPayload;
    if (!p.title || !p.description || !p.category || p.price == null) {
      this.error = 'Completa todos los campos obligatorios del servicio.';
      return;
    }
    for (const resource of this.draftResources) {
      if (!resource.title.trim() || (resource.resourceType === 'DOCUMENT' ? !resource.file && !resource.url : !this.validUrl(resource.url))) {
        this.error = 'Cada recurso necesita un título y un archivo o un enlace válido (http:// o https://).';
        return;
      }
    }
    this.loading = true;
    let serviceSaved = false;
    try {
      const offering = await firstValueFrom(this.editingId ? this.service.update(this.editingId, p) : this.service.create(p));
      this.editingId = offering.id;
      serviceSaved = true;
      // Record each successful operation so retrying cannot duplicate resources or the service.
      for (const resource of this.draftResources) {
        const saved = await firstValueFrom(this.resourceService.save(offering.id, resource, resource.id, resource.file));
        Object.assign(resource, saved);
        resource.file = undefined;
      }
      for (const id of [...this.removedResourceIds]) {
        await firstValueFrom(this.resourceService.delete(id));
        this.removedResourceIds = this.removedResourceIds.filter(value => value !== id);
      }
      this.finishSave();
    } catch (e) {
      this.loading = false;
      this.fail(e as HttpErrorResponse, serviceSaved ? 'El servicio se guardó, pero faltan cambios en los recursos. Puedes volver a guardar para reintentarlo.' : 'No fue posible guardar el servicio.');
      this.load();
    }
  }

  private validUrl(value: string): boolean {
    try { const url = new URL(value.trim()); return ['http:', 'https:'].includes(url.protocol); }
    catch { return false; }
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

  // NUEVO: abre el panel en modo creación
  openNew(): void {
    this.reset();
    this.error = '';
    this.form = {};
    this.editingId = null;
    this.draftResources = [];
    this.panelOpen = true;
  }

  // NUEVO: cierra el panel sin tocar el formulario (lo limpia reset())
  closePanel(): void {
    this.reset();
  }

  edit(o: Offering): void {
    this.reset();
    this.error = '';
    this.editingId = o.id;
    this.form = { title: o.title, description: o.description, category: o.category, price: o.price };
    this.panelOpen = true;
    this.resourceLoading = true;
    const version = ++this.resourceLoadVersion;
    this.resourceService.listForOffering(o.id).subscribe({
      next: resources => {
        if (version !== this.resourceLoadVersion) return;
        this.draftResources = resources.map(resource => ({ ...resource }));
        this.resourceLoading = false;
      },
      error: e => {
        if (version !== this.resourceLoadVersion) return;
        this.resourceLoading = false;
        this.resourceError = e?.error?.detail || 'No fue posible cargar los recursos. Cierra y vuelve a abrir el servicio.';
      },
    });
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
    if (this.loading) return;
    this.resourceLoadVersion++;
    this.resourceLoading = false;
    this.resourceError = '';
    this.removedResourceIds = [];
    this.form = {};
    this.editingId = null;
    this.panelOpen = false; // NUEVO
    this.draftResources = [];
  }

  addDraft(): void { this.draftResources.push({ title: '', resourceType: 'LINK', url: '' }); }
  removeDraft(index: number): void {
    const resource = this.draftResources[index];
    if (resource.id) this.removedResourceIds.push(resource.id);
    this.draftResources.splice(index, 1);
  }
  changeResourceType(resource: ResourceDraft): void {
    resource.url = ''; resource.file = undefined; resource.fileName = undefined;
  }
  selectFile(event: Event, resource: ResourceDraft): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!file.size || file.size > 10 * 1024 * 1024) {
      this.error = 'El archivo debe tener contenido y pesar máximo 10 MB.';
      input.value = '';
      return;
    }
    this.error = '';
    resource.file = file;
    resource.fileName = file.name;
    if (!resource.title.trim()) resource.title = file.name.slice(0, 160);
  }
  private finishSave(): void { this.loading = false; this.reset(); this.load(); }

  private fail(e: HttpErrorResponse, fallback: string): void {
    if (e.status === 403) this.error = 'No tienes permiso para realizar esta acción.';
    else if (e.status === 401) this.error = 'Tu sesión expiró. Inicia sesión de nuevo.';
    else this.error = e?.error?.detail || fallback;
  }
}
