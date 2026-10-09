import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../core/api';

@Component({
  selector: 'app-ai',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="min-h-[calc(100vh-4rem)] bg-slate-50 px-4 py-12 sm:px-6 lg:py-20">
      <div class="mx-auto max-w-3xl">
        <div class="mb-8 text-center">
          <span class="mb-4 inline-flex items-center rounded-full border border-blue-100 bg-blue-50 px-4 py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-blue-800">
            Inteligencia Artificial
          </span>
          <h1 class="text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Asistente de Carreras
          </h1>
          <p class="mx-auto mt-4 max-w-2xl text-sm leading-7 text-slate-500 sm:text-base">
            Cuéntanos qué quieres aprender o muéstranos tu experiencia. Nuestra IA te guiará hacia el mejor camino.
          </p>
        </div>

        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-10">
          <div class="space-y-4">
            <label class="block text-sm font-semibold text-slate-800">
              ¿Cuál es tu objetivo o experiencia?
            </label>

            <!-- Text Area -->
            <textarea
              [(ngModel)]="goal"
              rows="4"
              [disabled]="!!selectedFile || recording"
              placeholder="Ej: Quiero prepararme para una entrevista backend Java..."
              class="w-full resize-y rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-800 outline-none transition duration-200 placeholder:text-slate-400 hover:border-slate-400 focus:border-blue-800 focus:ring-4 focus:ring-blue-800/10 disabled:bg-slate-50 disabled:text-slate-400"
            ></textarea>

            <!-- Multimedia controls -->
            <div class="flex flex-wrap items-center gap-3">
              <button 
                type="button" 
                (click)="toggleRecording()" 
                [class.bg-red-50]="recording"
                [class.text-red-700]="recording"
                [class.border-red-200]="recording"
                [class.animate-pulse]="recording"
                class="inline-flex items-center gap-2 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-slate-200"
              >
                @if (recording) {
                  <span class="h-2 w-2 rounded-full bg-red-600"></span>
                  Detener grabación
                } @else {
                  🎙️ Grabar Voz
                }
              </button>

              <label class="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-slate-200">
                🖼️ Subir Imagen
                <input type="file" accept="image/jpeg,image/png,image/webp" (change)="selectFile($event, 'image')" class="hidden" />
              </label>

              @if (selectedFile) {
                <div class="flex items-center gap-2 rounded-lg bg-blue-50 px-3 py-1.5 text-sm text-blue-800">
                  <span class="truncate max-w-[200px] font-medium">{{ selectedFile.name }}</span>
                  <button type="button" (click)="clearSelection()" class="text-blue-500 hover:text-blue-700" title="Quitar archivo">
                    ✕
                  </button>
                </div>
              }
            </div>
            
            <p class="text-xs text-slate-400 mt-1">
              Puedes escribir un texto, grabar un audio explicando tu situación, o subir una imagen (ej. tu CV).
            </p>
          </div>

          <!-- Submit Button -->
          <div class="mt-8 border-t border-slate-100 pt-6">
            <button
              type="button"
              [disabled]="loading || (!goal.trim() && !selectedFile)"
              (click)="ask()"
              class="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-slate-900 px-6 py-3.5 text-sm font-semibold text-white shadow-sm transition duration-200 hover:bg-slate-800 focus:outline-none focus:ring-4 focus:ring-slate-900/20 disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto"
            >
              @if (loading) {
                <svg class="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                </svg>
                Analizando con IA...
              } @else {
                ✨ Pedir recomendación
              }
            </button>
          </div>

          <!-- Error Message -->
          @if (error) {
            <div class="mt-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              <div class="font-medium">Ocurrió un error:</div>
              <div class="mt-1">{{ error }}</div>
            </div>
          }

          <!-- AI Response Section -->
          @if (answer) {
            <div class="mt-8 rounded-2xl bg-slate-50 p-6 border border-slate-100">
              <div class="mb-4 flex items-center gap-3">
                <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-100 text-blue-700 shadow-sm">
                  ✨
                </div>
                <h2 class="text-lg font-bold text-slate-900">
                  Recomendación Personalizada
                </h2>
              </div>

              <!-- Transcription display if audio or image was used -->
              @if (sourceText && resultInputType !== 'TEXT') {
                <div class="mb-6 rounded-lg bg-white p-4 border border-slate-200 shadow-sm">
                  <h3 class="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2">Lo que la IA entendió:</h3>
                  <p class="text-sm italic text-slate-600">"{{ sourceText }}"</p>
                </div>
              }

              <!-- Main Explanation -->
              <div class="whitespace-pre-line text-sm leading-relaxed text-slate-700">
                {{ answer }}
              </div>

              <!-- Recommended Courses / Offerings -->
              @if (recommendations.length > 0) {
                <div class="mt-8">
                  <h3 class="text-sm font-bold text-slate-800 mb-4">Cursos Sugeridos para ti:</h3>
                  <div class="space-y-3">
                    @for (item of recommendations; track item.offeringId) {
                      <div class="rounded-xl border border-blue-100 bg-white p-4 shadow-sm transition hover:shadow-md">
                        <div class="flex items-start justify-between gap-4">
                          <div class="flex-1">
                            <p class="text-sm text-slate-600">{{ item.reason }}</p>
                          </div>
                          <span class="inline-flex items-center rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-semibold text-green-700 border border-green-200">
                            Relevancia: {{ (item.score * 100).toFixed(0) }}%
                          </span>
                        </div>
                      </div>
                    }
                  </div>
                </div>
              }
            </div>
          }
        </div>
      </div>
    </section>
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
  private recorder: MediaRecorder | null = null; 
  private chunks: Blob[] = [];

  constructor(private http: HttpClient) {}

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
      },
      error: (e) => {
        this.error = e?.error?.detail || 'Ocurrió un problema procesando tu solicitud. Intenta de nuevo más tarde.';
        this.loading = false;
      }
    });
  }
}