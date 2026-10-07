import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BusinessEvent {
  eventId: string;
  eventType: string;
  aggregateId: string;
  aggregateType: string;
  occurredAt: string;
  correlationId: string;
  version: number;
  payload: any;
}

export interface AuditStats {
  totalEvents: number;
  bookingCreated: number;
  bookingCancelled: number;
  otherEvents: number;
}

@Injectable({
  providedIn: 'root'
})
export class AuditService {
  private http = inject(HttpClient);
  // URL of the backend service (assuming CORS is configured, or we are on same host but port 8081)
  private apiUrl = '/api/audit';

  getEvents(): Observable<BusinessEvent[]> {
    return this.http.get<BusinessEvent[]>(`${this.apiUrl}/events`);
  }

  getStats(): Observable<AuditStats> {
    return this.http.get<AuditStats>(`${this.apiUrl}/stats`);
  }
}
