import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface ConsentStatus {
  kind: string;
  /** Phiên bản nội dung đồng ý hiện hành, do server giữ */
  currentVersion: number;
  accepted: boolean;
  acceptedAt?: string | null;
}

export async function getConsent(kind: string) {
  const { data } = await apiClient.get<ApiResponse<ConsentStatus>>(`/consents/${kind}`);
  return data.data;
}

export async function acceptConsent(kind: string, version: number) {
  const { data } = await apiClient.post<ApiResponse<ConsentStatus>>(`/consents/${kind}`, { version });
  return data.data;
}

export async function withdrawConsent(kind: string) {
  const { data } = await apiClient.delete<ApiResponse<ConsentStatus>>(`/consents/${kind}`);
  return data.data;
}
