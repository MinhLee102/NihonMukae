import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface Profile {
  id: string;
  email: string;
  name: string;
  role: string;
  /** 1..5 meaning N1..N5, null when not chosen. */
  targetLevel: number | null;
  createdAt: string;
}

export async function fetchProfile() {
  const { data } = await apiClient.get<ApiResponse<Profile>>('/users/me');
  return data.data;
}

export async function updateProfile(payload: { name: string; targetLevel: number | null }) {
  const { data } = await apiClient.put<ApiResponse<Profile>>('/users/me', payload);
  return data.data;
}
