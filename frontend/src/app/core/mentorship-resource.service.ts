import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export type ResourceType = 'VIDEO' | 'DOCUMENT' | 'LINK';
export interface MentorshipResource { id: string; offeringId: string; title: string; resourceType: ResourceType; url: string; fileName?: string; }
export interface ResourcePayload { title: string; resourceType: ResourceType; url: string; }

@Injectable({ providedIn: 'root' })
export class MentorshipResourceService {
  constructor(private http: HttpClient) { }
  listForOffering(offeringId: string) { return this.http.get<MentorshipResource[]>(`${apiBase()}/mentorship-resources/offerings/${offeringId}`); }
  create(offeringId: string, payload: ResourcePayload) { return this.http.post<MentorshipResource>(`${apiBase()}/mentorship-resources/offerings/${offeringId}`, payload); }
  save(offeringId: string, payload: ResourcePayload, id?: string, file?: File) {
    if (file) {
      const body = new FormData();
      body.append('title', payload.title.trim());
      body.append('file', file);
      return id
        ? this.http.put<MentorshipResource>(`${apiBase()}/mentorship-resources/${id}/file`, body)
        : this.http.post<MentorshipResource>(`${apiBase()}/mentorship-resources/offerings/${offeringId}/file`, body);
    }
    const body = { title: payload.title.trim(), resourceType: payload.resourceType, url: payload.url.trim() };
    return id ? this.http.put<MentorshipResource>(`${apiBase()}/mentorship-resources/${id}`, body) : this.create(offeringId, body);
  }
  fileUrl(resource: MentorshipResource): string {
    return resource.fileName ? `${apiBase()}/mentorship-resources/${resource.id}/file` : resource.url;
  }
  delete(id: string) { return this.http.delete<void>(`${apiBase()}/mentorship-resources/${id}`); }
}
