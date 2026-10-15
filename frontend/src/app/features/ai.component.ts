import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { apiBase } from '../core/api';

@Component({
  selector: 'app-ai',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="min-h-[calc(100vh-4rem)] bg-[#FBFBFA] px-4 py-16 sm:px-6 lg:py-24">
      <div class="mx-auto max-w-3xl">

        <header class="enter-rise">
          <h1 class="font-display text-5xl text-[#111111] sm:text-6xl">Asistente de Carreras</h1>
          <p class="mt-5 max-w-xl text-lg leading-8 text-[#2F3437]">
            Cuéntanos qué quieres aprender o muéstranos tu experiencia. Nuestra IA te guiará hacia el mejor camino.
          </p>
        </header>

        <!-- Compositor: un solo bloque, texto arriba, herramientas abajo -->
        <div class="enter-rise mt-12 rounded-xl border border-[#EAEAEA] bg-white transition-colors duration-150 focus-within:border-[#111111]" style="animation-delay: 70ms">
          <label class="sr-only" for="goal">¿Cuál es tu objetivo o experiencia?</label>
          <textarea
            id="goal"
            [(ngModel)]="goal"
            rows="5"
            [disabled]="!!selectedFile || recording"
            placeholder="¿Cuál es tu objetivo o experiencia? Ej: Quiero prepararme para una entrevista backend Java..."
            class="block w-full resize-none rounded-t-xl bg-transparent px-6 pb-3 pt-6 text-base leading-7 text-[#111111] outline-none placeholder:text-[#9B9A97] disabled:cursor-not-allowed disabled:text-[#9B9A97]"
          ></textarea>

          @if (selectedFile) {
            <div class="enter-modal mx-6 mb-3 flex w-fit items-center gap-2 rounded-md bg-[#F1F0ED] py-1 pl-3 pr-1 text-sm text-[#111111]">
              <span class="max-w-[220px] truncate font-medium">{{ selectedFile.name }}</span>
              <button type="button" (click)="clearSelection()" title="Quitar archivo" aria-label="Quitar archivo"
                      class="cursor-pointer rounded p-1 text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-white hover:text-[#111111] active:scale-[0.92] motion-reduce:active:scale-100">
                <svg class="h-3.5 w-3.5" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                  <path d="M205.66 194.34a8 8 0 0 1-11.32 11.32L128 139.31l-66.34 66.35a8 8 0 0 1-11.32-11.32L116.69 128 50.34 61.66a8 8 0 0 1 11.32-11.32L128 116.69l66.34-66.35a8 8 0 0 1 11.32 11.32L139.31 128Z"/>
                </svg>
              </button>
            </div>
          }

          <div class="flex flex-wrap items-center justify-between gap-3 border-t border-[#EAEAEA] px-4 py-3">
            <div class="flex items-center gap-1">
              <button
                type="button"
                (click)="toggleRecording()"
                [class]="recording ? 'bg-[#FDEBEC] text-[#9F2F2D] animate-pulse' : ''"
                class="inline-flex cursor-pointer items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100"
              >
                @if (recording) {
                  <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                    <path d="M200 48H56a8 8 0 0 0-8 8v144a8 8 0 0 0 8 8h144a8 8 0 0 0 8-8V56a8 8 0 0 0-8-8Z"/>
                  </svg>
                  Detener grabación
                } @else {
                  <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                    <path d="M128 176a48.05 48.05 0 0 0 48-48V64a48 48 0 0 0-96 0v64a48.05 48.05 0 0 0 48 48Zm80-48a8 8 0 0 0-16 0 64 64 0 0 1-128 0 8 8 0 0 0-16 0 80.11 80.11 0 0 0 72 79.6V232a8 8 0 0 0 16 0v-24.4A80.11 80.11 0 0 0 208 128Z"/>
                  </svg>
                  Grabar voz
                }
              </button>

              <label class="inline-flex cursor-pointer items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100 focus-within:outline-2 focus-within:outline-[#111111]">
                <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                  <path d="M216 40H40a16 16 0 0 0-16 16v144a16 16 0 0 0 16 16h176a16 16 0 0 0 16-16V56a16 16 0 0 0-16-16Zm0 16v102.75l-26.07-26.06a16 16 0 0 0-22.63 0l-20 20-44-44a16 16 0 0 0-22.62 0L40 149.37V56ZM40 172l52-52 80 80H40Zm176 28h-21.37l-36-36 20-20L216 181.38V200ZM144 100a12 12 0 1 1 12 12 12 12 0 0 1-12-12Z"/>
                </svg>
                Subir imagen
                <input type="file" accept="image/jpeg,image/png,image/webp" (change)="selectFile($event, 'image')" class="sr-only" />
              </label>
            </div>

            <button
              type="button"
              [disabled]="loading || (!goal.trim() && !selectedFile)"
              (click)="ask()"
              class="inline-flex cursor-pointer items-center gap-2 rounded-md bg-[#111111] px-4 py-2.5 text-sm font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#111111]"
            >
              @if (loading) {
                <svg class="h-4 w-4 animate-spin [animation-duration:600ms]" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                </svg>
                Analizando con IA...
              } @else {
                Pedir recomendación
              }
            </button>
          </div>
        </div>

        <p class="mt-4 text-sm leading-6 text-[#787774]">
          Puedes escribir un texto, grabar un audio explicando tu situación, o subir una imagen (ej. tu CV).
        </p>

        @if (error) {
          <div role="alert" class="enter-rise mt-6 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">
            <span class="font-medium">Ocurrió un error.</span> {{ error }}
          </div>
        }
      </div>
    </section>

    <!-- Resultado: panel lateral, mismo patrón que Gestionar servicios -->
    <div (click)="closeModal()"
         class="fixed inset-0 z-40 bg-[#111111]/20 transition-opacity duration-300 ease-out"
         [class.opacity-100]="showModal" [class.opacity-0]="!showModal"
         [class.pointer-events-auto]="showModal" [class.pointer-events-none]="!showModal"></div>

    <aside class="fixed inset-y-0 right-0 z-50 flex w-full max-w-lg flex-col border-l border-[#EAEAEA] bg-white shadow-[0_8px_40px_rgba(0,0,0,0.06)] transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] motion-reduce:transition-none"
           [class.translate-x-0]="showModal" [class.translate-x-full]="!showModal"
           [attr.inert]="showModal ? null : ''" [attr.aria-hidden]="!showModal"
           (keydown.escape)="closeModal()" role="dialog" aria-modal="true" aria-label="Recomendación personalizada">

      <div class="flex items-center justify-between px-8 pb-2 pt-8">
        <h2 class="font-display text-3xl text-[#111111]">Recomendación personalizada</h2>
        <button (click)="closeModal()" aria-label="Cerrar"
                class="cursor-pointer rounded-md p-2 text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.94] motion-reduce:active:scale-100">
          <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
            <path d="M205.66 194.34a8 8 0 0 1-11.32 11.32L128 139.31l-66.34 66.35a8 8 0 0 1-11.32-11.32L116.69 128 50.34 61.66a8 8 0 0 1 11.32-11.32L128 116.69l66.34-66.35a8 8 0 0 1 11.32 11.32L139.31 128Z"/>
          </svg>
        </button>
      </div>

      <div class="flex-1 overflow-y-auto px-8 py-6">
        @if (sourceText && resultInputType !== 'TEXT') {
          <div class="rounded-lg bg-[#F7F6F3] p-4">
            <h3 class="text-sm font-medium text-[#787774]">Lo que la IA detectó</h3>
            <p class="mt-1 text-sm italic leading-6 text-[#2F3437]">"{{ sourceText }}"</p>
          </div>
        }

        <div class="whitespace-pre-line text-base leading-7 text-[#2F3437]" [class.mt-6]="sourceText && resultInputType !== 'TEXT'">
          {{ answer }}
        </div>

        @if (recommendations.length > 0) {
          <h3 class="mt-12 text-sm font-medium text-[#111111]">Cursos sugeridos para ti</h3>
          <ul class="mt-3 border-t border-[#EAEAEA]">
            @for (item of recommendations; track item.offeringId; let i = $index) {
              <li class="flex flex-col gap-4 border-b border-[#EAEAEA] py-5 sm:flex-row sm:items-center sm:justify-between">
                <div class="min-w-0 flex-1">
                  <p class="text-sm leading-6 text-[#111111]">{{ item.reason }}</p>
                  <span class="mt-2 inline-block rounded-md bg-[#EDF3EC] px-2 py-0.5 text-xs font-medium text-[#346538] tabular-nums">
                    Relevancia: {{ (item.score * 100).toFixed(0) }}%
                  </span>
                </div>
                <button
                  (click)="goToBooking(item.offeringId)"
                  class="shrink-0 cursor-pointer rounded-md bg-[#111111] px-4 py-2 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.03] active:scale-[0.97] motion-reduce:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#111111]"
                >
                  Agendar cita
                </button>
              </li>
            }
          </ul>
        }
      </div>
    </aside>
  `
})
export class AiComponent {
  goal = '';
  answer = '';
  error = '';
  selectedFile: File | null = null;
  selectedType: 'voice' | 'image' | null = null;
  
  resultInputType = ''; 
  sourceText = ''; 
  recommendations: { offeringId: string; score: number; reason: string }[] = [];
  
  loading = false;
  recording = false; 
  showModal = false;
  private recorder: MediaRecorder | null = null; 
  private chunks: Blob[] = [];

  constructor(private http: HttpClient, private router: Router) {}

  async toggleRecording() { 
    if (this.recording) { 
      this.recorder?.stop(); 
      return; 
    } 
    try { 
      const stream = await navigator.mediaDevices.getUserMedia({audio: true}); 
      this.chunks = []; 
      this.recorder = new MediaRecorder(stream); 
      this.recorder.ondataavailable = e => this.chunks.push(e.data); 
      this.recorder.onstop = () => { 
        const file = new File([new Blob(this.chunks, {type: this.recorder?.mimeType || 'audio/webm'})], 'nota-de-voz.webm', {type: this.recorder?.mimeType || 'audio/webm'}); 
        this.selectedFile = file; 
        this.selectedType = 'voice'; 
        this.recording = false; 
        stream.getTracks().forEach(t => t.stop()); 
      }; 
      this.recorder.start(); 
      this.recording = true; 
    } catch { 
      this.error = 'No fue posible acceder al micrófono. Verifica los permisos de tu navegador.'; 
    } 
  }

  selectFile(event: Event, type: 'voice' | 'image') { 
    const file = (event.target as HTMLInputElement).files?.[0] ?? null; 
    this.selectedFile = file; 
    this.selectedType = file ? type : null; 
  }
  
  clearSelection() {
    this.selectedFile = null;
    this.selectedType = null;
    this.goal = '';
  }

  closeModal() {
    this.showModal = false;
  }

  goToBooking(offeringId: string) {
    this.closeModal();
    this.router.navigate(['/book'], { queryParams: { offeringId } });
  }

  private formData() { 
    const data = new FormData(); 
    data.append('file', this.selectedFile!); 
    return data; 
  }

  ask() {
    this.error = '';
    this.answer = ''; 
    this.sourceText = ''; 
    this.recommendations = [];
    this.showModal = false;
    this.loading = true;

    const url = this.selectedFile 
      ? `${apiBase()}/ai/recommendations/${this.selectedType}` 
      : `${apiBase()}/ai/recommendations`;
      
    const payload = this.selectedFile 
      ? this.formData() 
      : { goal: this.goal };

    this.http.post<any>(url, payload).subscribe({
      next: (r) => {
        this.answer = r.explanation; 
        this.resultInputType = r.inputType; 
        this.sourceText = r.sourceText; 
        this.recommendations = r.recommendations ?? [];
        this.loading = false;
        this.showModal = true;
      },
      error: (e) => {
        this.error = e?.error?.detail || 'Ocurrió un problema procesando tu solicitud. Intenta de nuevo más tarde.';
        this.loading = false;
      }
    });
  }
}