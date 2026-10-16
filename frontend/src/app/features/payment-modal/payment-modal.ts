import { Component, EventEmitter, Input, OnInit, Output, ViewChild, ElementRef, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../../core/api';
import { StripeService } from '../../core/stripe.service';
import { Stripe, StripeElements, StripePaymentElement } from '@stripe/stripe-js';

@Component({
  selector: 'app-payment-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payment-modal.html',
  styleUrls: ['./payment-modal.css']
})
export class PaymentModal implements OnInit, OnDestroy {
  @Input() bookingId!: string;
  @Input() amount: number = 0;
  
  @Output() close = new EventEmitter<void>();
  @Output() paymentSuccess = new EventEmitter<void>();

  @ViewChild('paymentElementContainer') paymentElementRef?: ElementRef<HTMLDivElement>;

  // Variables de control de Stripe
  stripe: Stripe | null = null;
  elements: StripeElements | null = null;
  paymentElement: StripePaymentElement | null = null;
  clientSecret: string = '';

  // Estado del componente
  initializingStripe = true;
  stripeAvailable = false;
  loading = false;
  errorMsg = '';

  // Datos para fallback simulado (en caso de que no haya clave de Stripe configurada)
  cardData = {
    cardNumber: '',
    expiryDate: '',
    cvv: ''
  };

  constructor(
    private http: HttpClient,
    private stripeService: StripeService
  ) {}

  async ngOnInit() {
    await this.initStripeFlow();
  }

  ngOnDestroy() {
    if (this.paymentElement) {
      try {
        this.paymentElement.destroy();
      } catch (e) {
        // Ignorar cleanup si ya fue destruido
      }
    }
  }

  /**
   * Inicializa el flujo solicitando el PaymentIntent al backend para montar Stripe Elements.
   */
  private async initStripeFlow() {
    this.initializingStripe = true;
    this.errorMsg = '';

    const publishableKey = this.stripeService.getPublishableKey();
    if (!publishableKey) {
      // Si no se proporcionó la clave pública de Stripe, caemos al modo simulado transparente
      console.info('[PaymentModal] STRIPE_PUBLISHABLE_KEY no encontrada. Activando modo simulado.');
      this.stripeAvailable = false;
      this.initializingStripe = false;
      return;
    }

    try {
      this.stripe = await this.stripeService.getStripe();
      if (!this.stripe) {
        throw new Error('No se pudo inicializar Stripe.js.');
      }

      // Solicitamos al backend la creación del PaymentIntent para obtener el client_secret
      const idempotencyKey = crypto.randomUUID();
      const payload = {
        bookingId: this.bookingId,
        amount: this.amount
      };

      const response = await this.http.post<any>(apiBase() + '/payments/authorize', payload, {
        headers: { 'Idempotency-Key': idempotencyKey }
      }).toPromise();

      if (!response?.clientSecret) {
        throw new Error('El servidor no retornó el clientSecret para la pasarela.');
      }

      this.clientSecret = response.clientSecret;

      // Configuración de apariencia de Stripe Elements acorde al diseño Notion/Clean de SkillBridge
      const appearance = {
        theme: 'stripe' as const,
        variables: {
          colorPrimary: '#111111',
          colorBackground: '#ffffff',
          colorText: '#111111',
          colorDanger: '#9F2F2D',
          fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
          spacingUnit: '4px',
          borderRadius: '6px'
        }
      };

      this.elements = this.stripe.elements({
        clientSecret: this.clientSecret,
        appearance
      });

      this.paymentElement = this.elements.create('payment', {
        layout: 'tabs'
      });

      this.stripeAvailable = true;
      this.initializingStripe = false;

      // Montamos el elemento en el DOM después de que Angular refresque la vista
      setTimeout(() => {
        if (this.paymentElementRef && this.paymentElement) {
          this.paymentElement.mount(this.paymentElementRef.nativeElement);
        }
      }, 50);

    } catch (err: any) {
      console.warn('[PaymentModal] No se pudo inicializar Stripe Elements:', err.message || err);
      // Fallback elegante al formulario simulado si falla la API de Stripe
      this.stripeAvailable = false;
      this.initializingStripe = false;
    }
  }

  /**
   * Ejecuta el pago mediante Stripe Elements o mediante el modo simulado.
   */
  async submitPayment() {
    this.loading = true;
    this.errorMsg = '';

    if (this.stripeAvailable && this.stripe && this.elements) {
      // Flujo Real con Stripe Elements
      try {
        const { error, paymentIntent } = await this.stripe.confirmPayment({
          elements: this.elements,
          confirmParams: {
            // En caso de redirección (ej. 3D Secure) Stripe redirige al origen actual
            return_url: window.location.href
          },
          redirect: 'if_required'
        });

        if (error) {
          this.loading = false;
          this.errorMsg = error.message || 'El pago no pudo ser procesado. Intenta de nuevo.';
          return;
        }

        if (paymentIntent && (paymentIntent.status === 'succeeded' || paymentIntent.status === 'processing')) {
          this.loading = false;
          this.paymentSuccess.emit();
        } else {
          this.loading = false;
          this.errorMsg = 'El estado de la transacción es: ' + (paymentIntent?.status || 'desconocido');
        }
      } catch (err: any) {
        this.loading = false;
        this.errorMsg = 'Error al comunicar con la pasarela: ' + (err.message || 'desconocido');
      }

    } else {
      // Flujo de Respaldo Simulado
      this.submitSimulatedPayment();
    }
  }

  /**
   * Lógica de respaldo para el modo de prueba simulado.
   */
  private submitSimulatedPayment() {
    if (this.cardData.expiryDate.length === 5) {
      const parts = this.cardData.expiryDate.split('/');
      const month = parseInt(parts[0], 10);
      const year = parseInt('20' + parts[1], 10);
      const now = new Date();
      const currentYear = now.getFullYear();
      const currentMonth = now.getMonth() + 1;

      if (month < 1 || month > 12) {
        this.errorMsg = 'El mes de vencimiento es inválido.';
        this.loading = false;
        return;
      }

      if (year < currentYear || (year === currentYear && month < currentMonth)) {
        this.errorMsg = 'La tarjeta ingresada está vencida.';
        this.loading = false;
        return;
      }
    }

    if (this.cardData.cvv === '000') {
      setTimeout(() => {
        this.errorMsg = "Pago rechazado: Por políticas de seguridad, los códigos de verificación (CVV) con valor '000' no son admitidos.";
        this.loading = false;
      }, 600);
      return;
    }

    const idempotencyKey = crypto.randomUUID();
    const payload = {
      bookingId: this.bookingId,
      amount: this.amount,
      cardNumber: this.cardData.cardNumber.replace(/\s/g, ''),
      expiryDate: this.cardData.expiryDate,
      cvv: this.cardData.cvv
    };

    this.http.post<any>(apiBase() + '/payments/authorize', payload, {
      headers: { 'Idempotency-Key': idempotencyKey }
    }).subscribe({
      next: (response) => {
        this.loading = false;
        if (response['status'] === 'APPROVED') {
          this.paymentSuccess.emit();
        } else {
          let msg = response['message'];
          if (msg === 'Card declined deterministically' || this.cardData.cardNumber.endsWith('0000')) {
            this.errorMsg = "Pago rechazado: Por políticas de seguridad, las tarjetas con terminación '0000' no están habilitadas para realizar transacciones.";
          } else {
            this.errorMsg = 'Pago rechazado: ' + msg + '. Intenta con otra tarjeta.';
          }
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = 'Ocurrió un error técnico procesando el pago. Por favor intenta de nuevo.';
        console.error(err);
      }
    });
  }

  // Métodos auxiliares para formateo en modo simulado
  formatCardNumber(value: string) {
    const isOnlyNumbersAndSpaces = /^[0-9\s]*$/.test(value);
    let input = value.replace(/\s/g, '').substring(0, 16);
    if (isOnlyNumbersAndSpaces) {
      let formatted = '';
      for (let i = 0; i < input.length; i++) {
        if (i > 0 && i % 4 === 0) formatted += ' ';
        formatted += input[i];
      }
      setTimeout(() => this.cardData.cardNumber = formatted);
    } else {
      setTimeout(() => this.cardData.cardNumber = value.substring(0, 19));
    }
  }

  formatExpiry(value: string) {
    const isOnlyNumbersAndSlash = /^[0-9\/]*$/.test(value);
    let input = value.replace(/\//g, '').substring(0, 4);
    if (isOnlyNumbersAndSlash) {
      if (input.length > 2) input = input.substring(0, 2) + '/' + input.substring(2);
      setTimeout(() => this.cardData.expiryDate = input);
    } else {
      setTimeout(() => this.cardData.expiryDate = value.substring(0, 5));
    }
  }

  formatCvv(value: string) {
    let input = value.substring(0, 3);
    setTimeout(() => this.cardData.cvv = input);
  }
}
