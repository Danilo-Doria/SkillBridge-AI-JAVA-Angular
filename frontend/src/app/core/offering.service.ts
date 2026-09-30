import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export interface Offering {
  id: string;
  title: string;
  description: string;
  category: string;
  price: number;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class OfferingService {
  constructor(private http: HttpClient) {}
  list() { return this.http.get<Offering[]>(`${apiBase()}/offerings`); }
}
