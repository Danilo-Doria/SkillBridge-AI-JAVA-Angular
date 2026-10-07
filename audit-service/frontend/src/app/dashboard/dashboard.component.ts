import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuditService, BusinessEvent, AuditStats } from '../audit.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-container">
      <header>
        <h1>Audit Dashboard</h1>
        <button (click)="refresh()">Refresh</button>
      </header>

      <section class="stats" *ngIf="stats() as s">
        <div class="stat-card">
          <h3>Total Events</h3>
          <p>{{ s.totalEvents }}</p>
        </div>
        <div class="stat-card">
          <h3>Bookings Created</h3>
          <p>{{ s.bookingCreated }}</p>
        </div>
        <div class="stat-card">
          <h3>Bookings Cancelled</h3>
          <p>{{ s.bookingCancelled }}</p>
        </div>
        <div class="stat-card">
          <h3>Other Events</h3>
          <p>{{ s.otherEvents }}</p>
        </div>
      </section>

      <section class="events">
        <h2>Recent Events</h2>
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Type</th>
              <th>Aggregate ID</th>
              <th>Correlation ID</th>
              <th>Payload</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let event of events()">
              <td>{{ event.occurredAt | date:'medium' }}</td>
              <td><span class="badge" [ngClass]="event.eventType">{{ event.eventType }}</span></td>
              <td>{{ event.aggregateId }}</td>
              <td>{{ event.correlationId }}</td>
              <td><pre>{{ event.payload | json }}</pre></td>
            </tr>
            <tr *ngIf="events().length === 0">
              <td colspan="5" class="empty-state">No events found.</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>
  `,
  styles: [`
    .dashboard-container {
      padding: 20px;
      font-family: sans-serif;
      max-width: 1200px;
      margin: 0 auto;
    }
    header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }
    button {
      padding: 8px 16px;
      background-color: #007bff;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
    }
    button:hover { background-color: #0056b3; }
    
    .stats {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 32px;
    }
    .stat-card {
      background: #f8f9fa;
      border: 1px solid #dee2e6;
      border-radius: 8px;
      padding: 16px;
      text-align: center;
    }
    .stat-card h3 {
      margin: 0 0 8px 0;
      font-size: 1rem;
      color: #6c757d;
    }
    .stat-card p {
      margin: 0;
      font-size: 2rem;
      font-weight: bold;
      color: #212529;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      background: white;
    }
    th, td {
      border: 1px solid #dee2e6;
      padding: 12px;
      text-align: left;
    }
    th {
      background-color: #f8f9fa;
    }
    .badge {
      padding: 4px 8px;
      border-radius: 12px;
      font-size: 0.85em;
      font-weight: bold;
      background-color: #e9ecef;
    }
    .BookingCreated { background-color: #d4edda; color: #155724; }
    .BookingCancelled { background-color: #f8d7da; color: #721c24; }
    pre {
      margin: 0;
      white-space: pre-wrap;
      word-wrap: break-word;
      max-width: 300px;
      font-size: 0.85em;
    }
    .empty-state {
      text-align: center;
      color: #6c757d;
      padding: 24px;
    }
  `]
})
export class DashboardComponent implements OnInit {
  private auditService = inject(AuditService);

  events = signal<BusinessEvent[]>([]);
  stats = signal<AuditStats | null>(null);

  ngOnInit() {
    this.refresh();
  }

  refresh() {
    this.auditService.getEvents().subscribe({
      next: (data) => this.events.set(data),
      error: (err) => console.error('Failed to load events', err)
    });

    this.auditService.getStats().subscribe({
      next: (data) => this.stats.set(data),
      error: (err) => console.error('Failed to load stats', err)
    });
  }
}
