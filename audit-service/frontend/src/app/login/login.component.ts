import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  private http = inject(HttpClient);
  private router = inject(Router);

  email = signal('');
  password = signal('');
  error = signal('');
  loading = signal(false);

  login() {
    this.error.set('');
    this.loading.set(true);

    // Call the main backend Auth API (running on port 8080)
    // We assume the main backend is at localhost:8080 in dev/docker.
    // In production, this would be an environment variable.
    const authUrl = window.location.hostname === 'localhost' 
      ? 'http://localhost:8080/api/auth/login'
      : '/api/auth/login'; // fallback if they are on same origin via gateway

    this.http.post<{token: string}>(authUrl, {
      email: this.email(),
      password: this.password()
    }).subscribe({
      next: (res) => {
        localStorage.setItem('skillbridge_token', res.token);
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Invalid credentials or server unavailable.');
      }
    });
  }
}
