import { apiClient } from './client';
import type { AuthResponse, LoginPayload, RegisterPayload, RegisterResponse } from '@/types/auth';
 
export async function registerRequest(payload: RegisterPayload) {
  const { data } = await apiClient.post<RegisterResponse>('/auth/register', payload);
  return data;
}
 
export async function loginRequest(payload: LoginPayload) {
  const { data } = await apiClient.post<AuthResponse>('/auth/login', payload);
  return data;
}
 
export async function logoutRequest() {
  const { data } = await apiClient.post<{ ok: boolean }>('/auth/logout');
  return data;
}
 