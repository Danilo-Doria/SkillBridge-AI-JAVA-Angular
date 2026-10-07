import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../../core/api';

@Component({
  selector: 'app-payment-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: 
    <!-- Overlay de fondo -->
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm transition-opacity">
      <!-- Contenedor del Modal -->
      <div class="relative w-full max-w-md transform overflow-hidden rounded-2xl bg-white p-6 text-left shadow-xl transition-all sm:my-8 sm:w-full sm:max-w-lg">
        
        <!-- Encabezado -->
        <div class="mb-4 flex items-center justify-between">
          <h3 class="text-xl font-bold text-slate-900">
            Pagar Reserva
          </h3>
          <button 
            type="button" 
            (click)="close.emit()" 
            class="rounded-full p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-500"
          >
            <svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
              <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
            </svg>
          </button>
        </div>

        <div class="mb-6 rounded-lg bg-blue-50 p-4 border border-blue-100">
          <div class="flex">
            <div class="flex-shrink-0">
              <svg class="h-5 w-5 text-blue-400" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
              </svg>
            </div>
            <div class="ml-3 flex-1 md:flex md:justify-between">
              <p class="text-sm text-blue-700">
                <strong>Entorno de Simulación.</strong> Los datos ingresados a continuación no serán almacenados ni procesados realmente.
              </p>
            </div>
          </div>
        </div>

        @if (errorMsg) {
          <div class="mb-4 rounded-lg bg-red-50 p-4 border border-red-100">
            <p class="text-sm text-red-700">{{ errorMsg }}</p>
          </div>
        }

        <form (ngSubmit)="submitPayment()" #paymentForm="ngForm">
          <div class="space-y-4">
            
            <!-- Amount (readonly) -->
            <div>
              <label class="block text-sm font-medium text-slate-700">Monto a pagar</label>
              <div class="mt-1">
                <input 
                  type="text" 
                  disabled 
                  [value]="'$' + amount" 
                  class="block w-full rounded-lg border-slate-300 bg-slate-100 py-2.5 px-3 text-slate-700 sm:text-sm"
                >
              </div>
            </div>

            <!-- Card Number -->
            <div>
              <label class="block text-sm font-medium text-slate-700">Número de Tarjeta</label>
              <div class="mt-1">
                <input 
                  type="text" 
                  name="cardNumber" 
                  [(ngModel)]="cardData.cardNumber"
                  placeholder="0000 0000 0000 0000"
                  required
                  class="block w-full rounded-lg border-slate-300 py-2.5 px-3 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                >
              </div>
            </div>

            <div class="flex gap-4">
              <!-- Expiry -->
              <div class="w-1/2">
                <label class="block text-sm font-medium text-slate-700">Vencimiento</label>
                <div class="mt-1">
                  <input 
                    type="text" 
                    name="expiryDate" 
                    [(ngModel)]="cardData.expiryDate"
                    placeholder="MM/YY"
                    required
                    class="block w-full rounded-lg border-slate-300 py-2.5 px-3 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                  >
                </div>
              </div>

              <!-- CVV -->
              <div class="w-1/2">
                <label class="block text-sm font-medium text-slate-700">CVV</label>
                <div class="mt-1">
                  <input 
                    type="text" 
                    name="cvv" 
                    [(ngModel)]="cardData.cvv"
                    placeholder="123"
                    required
                    class="block w-full rounded-lg border-slate-300 py-2.5 px-3 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                  >
                </div>
              </div>
            </div>
            
          </div>

          <div class="mt-6 flex justify-end gap-3">
            <button 
              type="button" 
              (click)="close.emit()" 
              class="rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50 focus:outline-none"
            >
              Cancelar
            </button>
            <button 
              type="submit" 
              [disabled]="loading || !paymentForm.valid"
              class="inline-flex justify-center rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-900 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ loading ? 'Procesando...' : 'Confirmar Pago' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  
})
export class PaymentModal {
  @Input() bookingId!: string;
  @Input() amount: number = 0;
  
  @Output() close = new EventEmitter<void>();
  @Output() paymentSuccess = new EventEmitter<void>();

  cardData = {
    cardNumber: '',
    expiryDate: '',
    cvv: ''
  };

  loading = false;
  errorMsg = '';

  constructor(private http: HttpClient) {}

  submitPayment() {
    this.loading = true;
    this.errorMsg = '';

    // Generar un Idempotency-Key nuevo por cada intento
    const idempotencyKey = crypto.randomUUID();

    const payload = {
      bookingId: this.bookingId,
      amount: this.amount,
      cardNumber: this.cardData.cardNumber,
      expiryDate: this.cardData.expiryDate,
      cvv: this.cardData.cvv
    };

    this.http.post<any>(${apiBase()}/payments/authorize, payload, {
      headers: {
        'Idempotency-Key': idempotencyKey
      }
    }).subscribe({
      next: (response) => {
        this.loading = false;
        if (response.status === 'APPROVED') {
          this.paymentSuccess.emit();
        } else {
          this.errorMsg = 'Pago rechazado: ' + response.message + '. Intenta con otra tarjeta.';
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = 'Ocurrió un error técnico procesando el pago. Por favor intenta de nuevo.';
        console.error(err);
      }
    });
  }
}
