import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface KaiwaSessionDto {
  id: string;
  level: number;
  topic: string;
  summary: string;
  turns: number;
  startedAt: string;
  endedAt: string;
}

export async function listKaiwaSessions() {
  const { data } = await apiClient.get<ApiResponse<KaiwaSessionDto[]>>('/kaiwa/sessions');
  return data.data;
}

export async function saveKaiwaSession(input: {
  level: number;
  topic: string;
  summary: string;
  turns: number;
  startedAt: string;
}) {
  const { data } = await apiClient.post<ApiResponse<KaiwaSessionDto>>('/kaiwa/sessions', input);
  return data.data;
}

export async function deleteKaiwaSession(id: string) {
  await apiClient.delete(`/kaiwa/sessions/${id}`);
}
