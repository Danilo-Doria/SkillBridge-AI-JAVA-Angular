import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { apiBase } from './api';
import { Observable } from 'rxjs';

export type BookingStatus = 'CREATED' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

export interface Booking {
  id: string;
  offeringId: string;
  customerId: string;
  scheduledAt: string;
  status: BookingStatus;
}

export interface CancelBookingCommand {
  bookingId: string;
  customerEmail: string;
}

@Injectable({ providedIn: 'root' })
export class BookingService {
  constructor(private http: HttpClient) {}

  getMyBookings(): Observable<Booking[]> {
    return this.http.get<Booking[]>(apiBase() + '/bookings/me');
  }

  // Envía la petición al backend para cancelar una reserva
  cancelBooking(command: CancelBookingCommand): Observable<Booking> {
    return this.http.post<Booking>(apiBase() + '/bookings/cancel', command);
  }
}

