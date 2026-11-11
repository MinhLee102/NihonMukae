import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface ShadowingSegment {
  start: number;
  end: number;
  text: string;
}

export interface ShadowingSummary {
  id: string;
  videoId: string;
  title: string;
  segmentCount: number;
  markedCount: number;
  updatedAt: string;
  lastPracticedAt?: string | null;
}

export interface ShadowingDetail {
  id: string;
  videoId: string;
  title: string;
  segments: ShadowingSegment[];
  /** Chỉ số các câu đã đánh dấu "cần luyện" */
  marked: number[];
  lastPracticedAt?: string | null;
}

export interface ShadowingInput {
  videoId: string;
  title: string;
  segments: ShadowingSegment[];
}

export async function listShadowing() {
  const { data } = await apiClient.get<ApiResponse<ShadowingSummary[]>>('/shadowing/materials');
  return data.data;
}

export async function getShadowing(id: string) {
  const { data } = await apiClient.get<ApiResponse<ShadowingDetail>>(`/shadowing/materials/${id}`);
  return data.data;
}

export async function createShadowing(input: ShadowingInput) {
  const { data } = await apiClient.post<ApiResponse<ShadowingDetail>>('/shadowing/materials', input);
  return data.data;
}

export async function updateShadowing(id: string, input: ShadowingInput) {
  const { data } = await apiClient.put<ApiResponse<ShadowingDetail>>(`/shadowing/materials/${id}`, input);
  return data.data;
}

export async function deleteShadowing(id: string) {
  await apiClient.delete(`/shadowing/materials/${id}`);
}

export async function saveShadowingMarks(id: string, marked: number[]) {
  const { data } = await apiClient.put<ApiResponse<number[]>>(`/shadowing/materials/${id}/marks`, { marked });
  return data.data;
}
