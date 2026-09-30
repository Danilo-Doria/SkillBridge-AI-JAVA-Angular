import { Routes } from '@angular/router';
import { HomeComponent } from './features/home.component';
import { LoginComponent } from './features/login.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'ai', component: AiComponent },
  { path: 'book', component: BookingComponent },
  { path: '**', redirectTo: '' }
];
