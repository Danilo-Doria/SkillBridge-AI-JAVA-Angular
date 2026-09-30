import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../core/api';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="container section"><div class="card">
      <p class="eyebrow">JAVA + GEMINI</p><h1>Asistente de recomendaciones</h1>
      <p class="muted">La API key vive únicamente en Spring Boot. Angular nunca habla directamente con Gemini.</p>
      <label class="field">Tu objetivo<textarea rows="5" [(ngModel)]="goal" placeholder="Ej: Quiero prepararme para una entrevista backend Java..."></textarea></label>
      <button class="btn" [disabled]="loading" (click)="ask()">{{ loading ? 'Generando...' : 'Pedir recomendación' }}</button>
      @if (error) { <p class="error">{{ error }}</p> }
      @if (answer) { <div class="answer"><h3>Respuesta</h3><p>{{ answer }}</p></div> }
    </div></section>
  `,
  styles: [`.section{padding:50px 0}.card{max-width:760px;margin:auto}.eyebrow{font-weight:800;letter-spacing:.1em}.answer{margin-top:22px;padding:18px;background:#f3f6fb;border-radius:12px;white-space:pre-line}`]
})
export class AiComponent {
  goal=''; answer=''; error=''; loading=false;
  constructor(private http: HttpClient) {}
  ask(){
    this.error=''; this.answer=''; this.loading=true;
    this.http.post<{recommendation:string}>(`${apiBase()}/ai/recommendations`, { goal: this.goal }).subscribe({
      next: r => { this.answer = r.recommendation; this.loading=false; },
      error: e => { this.error = e?.error?.detail || 'Inicia sesión y verifica GEMINI_API_KEY.'; this.loading=false; }
    });
  }
}
