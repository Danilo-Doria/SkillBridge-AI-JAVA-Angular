import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export type MentorshipMode = 'ASYNCHRONOUS' | 'LIVE';
export type MentorshipStatus = 'PENDING_PAYMENT' | 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED' | 'NO_SHOW';
export interface Mentorship { id: string; mode: MentorshipMode; status: MentorshipStatus; accessStartsAt: string; accessEndsAt: string; progressPercent: number; }
export interface MentorshipAccess { mode: MentorshipMode; accessEndsAt: string; liveRoomUrl: string; }
@Injectable({providedIn: 'root'})
export class MentorshipService {
  constructor(private http: HttpClient) {}
  mine(): Observable<Mentorship[]> { return this.http.get<Mentorship[]>(`${apiBase()}/mentorships/me`); }
  access(id: string): Observable<MentorshipAccess> { return this.http.get<MentorshipAccess>(`${apiBase()}/mentorships/${id}/access`); }
  updateProgress(id: string, progressPercent: number): Observable<Mentorship> { return this.http.patch<Mentorship>(`${apiBase()}/mentorships/${id}/progress`, {progressPercent}); }
}
