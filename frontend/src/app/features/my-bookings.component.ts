import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BookingService, Booking } from '../core/booking.service';
import { OfferingService, Offering } from '../core/offering.service';
import { AuthService } from '../core/auth.service';
import { PaymentModal } from './payment-modal/payment-modal';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule, PaymentModal],
  templateUrl: './my-bookings.component.html'
})
export class MyBookingsComponent implements OnInit {
  private bookingService = inject(BookingService);
  private authService = inject(AuthService);
  private offeringService = inject(OfferingService);

  bookings: Booking[] = [];
  offerings: Offering[] = [];
  isLoading = true;
  errorMessage: string | null = null;
  
  selectedBookingForPayment: Booking | null = null;
  
  isCancelingId: string | null = null;
  cancelSuccessMessage = '';
  cancelErrorMessage = '';

  ngOnInit(): void {
    this.fetchMyBookings();
    this.offeringService.list().subscribe(data => this.offerings = data);
  }

  fetchMyBookings(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.bookingService.getMyBookings().subscribe({
      next: (data) => {
        this.bookings = data;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error al obtener reservas:', err);
        this.errorMessage = 'No se pudieron cargar tus reservas. Intenta nuevamente.';
        this.isLoading = false;
      },
    });
  }

  // Verifica si faltan mǭs de 24 horas para la sesin para permitir la cancelacin
  canCancel(scheduledAt: string): boolean {
    const bookingDate = new Date(scheduledAt);
    const now = new Date();
    const diffHours = (bookingDate.getTime() - now.getTime()) / (1000 * 60 * 60);
    return diffHours > 24;
  }

  // Llama al servicio para cancelar la reserva y actualiza la lista localmente
  cancelBooking(bookingId: string): void {
    if (!confirm('Estǭs seguro de que deseas cancelar esta reserva?')) {
      return;
    }

    this.isCancelingId = bookingId;
    this.cancelSuccessMessage = '';
    this.cancelErrorMessage = '';

    this.bookingService.cancelBooking(bookingId).subscribe({
      next: (updatedBooking) => {
        this.isCancelingId = null;
        this.cancelSuccessMessage = 'Reserva cancelada exitosamente. Se ha procesado el reembolso del 100% a tu mǸtodo de pago.';
        const index = this.bookings.findIndex(b => b.id === bookingId);
        if (index !== -1) {
          this.bookings[index] = updatedBooking;
        }
      },
      error: (err) => {
        this.isCancelingId = null;
        this.cancelErrorMessage = 'No se pudo cancelar la reserva. Tal vez expir el tiempo de cancelacin.';
        console.error(err);
      }
    });
  }

  getOfferingPrice(offeringId: string): number {
    const offering = this.offerings.find(o => o.id === offeringId);
    return offering ? offering.price : 50000;
  }

  // Abre el modal de pago guardando la reserva seleccionada
  openPaymentModal(booking: Booking): void {
    this.selectedBookingForPayment = booking;
  }

  closePaymentModal(): void {
    this.selectedBookingForPayment = null;
  }

  // Se ejecuta al aprobarse el pago, marcando la reserva como confirmada sin recargar la pǭgina
  onPaymentSuccess(): void {
    if (this.selectedBookingForPayment) {
      this.selectedBookingForPayment.status = 'CONFIRMED';
    }
    this.selectedBookingForPayment = null;
  }
}
