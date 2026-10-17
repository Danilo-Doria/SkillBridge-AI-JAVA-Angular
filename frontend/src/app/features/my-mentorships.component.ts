import { Component, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Mentorship, MentorshipService } from '../core/mentorship.service';

@Component({selector: 'app-my-mentorships', standalone: true, imports: [CommonModule, DatePipe], template: `
<section class="mx-auto min-h-screen max-w-4xl px-4 py-16"><h1 class="font-display text-5xl">Mis mentorías</h1><p class="mt-3 text-[#787774]">Tu acceso se habilita según el pago, la fecha y el estado de cada mentoría.</p>
@if (loading) { <p class="mt-10">Cargando mentorías…</p> } @else if (error) { <p class="mt-10 text-red-700">{{ error }}</p> } @else if (!items.length) { <p class="mt-10">Aún no tienes mentorías asignadas.</p> } @else { <div class="mt-10 space-y-4">@for (item of items; track item.id) { <article class="rounded-lg border border-[#EAEAEA] bg-white p-5"><div class="flex items-start justify-between gap-4"><div><h2 class="text-lg font-medium">{{ item.mode === 'LIVE' ? 'Mentoría en vivo' : 'Aula asincrónica' }}</h2><p class="mt-1 text-sm text-[#787774]">Disponible: {{ item.accessStartsAt | date:'medium' }} — {{ item.accessEndsAt | date:'medium' }}</p></div><span class="rounded bg-[#F1F0ED] px-2 py-1 text-xs">{{ item.status }}</span></div><p class="mt-4 text-sm">Progreso: {{ item.progressPercent }}%</p><div class="mt-4 flex gap-3"><button (click)="open(item)" class="rounded bg-[#111] px-4 py-2 text-sm text-white">Acceder</button>@if (item.mode === 'ASYNCHRONOUS' && item.status !== 'COMPLETED') {<button (click)="complete(item)" class="rounded border px-4 py-2 text-sm">Marcar completada</button>}</div></article>}</div>}
@if (message) {<p class="mt-6 text-sm" [class.text-red-700]="isError">{{message}}</p>}</section>`})
export class MyMentorshipsComponent {
  private api=inject(MentorshipService); items: Mentorship[]=[]; loading=true; error=''; message=''; isError=false;
  constructor(){ this.refresh(); }
  refresh(){ this.api.mine().subscribe({next: x=>{this.items=x;this.loading=false;},error:()=>{this.error='No fue posible cargar tus mentorías.';this.loading=false;}}); }
  open(item: Mentorship){ this.api.access(item.id).subscribe({next: a=>{this.isError=false;this.message=a.mode==='LIVE' ? (a.liveRoomUrl ? 'La sala está habilitada: '+a.liveRoomUrl : 'La sesión está habilitada. El mentor compartirá el acceso seguro.') : 'El aula está habilitada hasta '+new Date(a.accessEndsAt).toLocaleString();},error:e=>{this.isError=true;this.message=e.error?.detail || 'El acceso no está habilitado para esta mentoría.';}}); }
  complete(item: Mentorship){ this.api.updateProgress(item.id,100).subscribe({next: updated=>{item.status=updated.status;item.progressPercent=updated.progressPercent;this.isError=false;this.message='Mentoría completada. Este pago ya no puede reiniciar otra mentoría.';},error:e=>{this.isError=true;this.message=e.error?.detail || 'No se pudo actualizar el progreso.';}}); }
}
