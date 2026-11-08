import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/authStore';
import type { AuthResponse } from '@/types/auth';
 
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
 
/** Main client — use this for every API call in the app. */
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  // Required so the browser sends/receives the HttpOnly refresh_token cookie.
  withCredentials: true,
});
 
/** Seconds until the token's exp claim; null when it cannot be read. */
function secondsLeft(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: number };
    return typeof payload.exp === 'number' ? payload.exp - Date.now() / 1000 : null;
  } catch {
    return null;
  }
}

// Attach the in-memory access token to every outgoing request. A token about to expire is renewed first,
// so a long exam session never sends a stale token and waits on a failed request.
apiClient.interceptors.request.use(async (config) => {
  let token = useAuthStore.getState().accessToken;
  const left = token ? secondsLeft(token) : null;
  if (token && left !== null && left < 30) token = await refreshAccessToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
 
let refreshPromise: Promise<string | null> | null = null;
 
// A separate plain axios call (bypasses apiClient's interceptors) so calling
// /auth/refresh from within the response interceptor below can't recurse.
export function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise;
  }
 
  refreshPromise = (async () => {
    try {
      const { data } = await axios.post<AuthResponse>(
        `${API_BASE_URL}/auth/refresh`,
        {},
        { withCredentials: true }
      );
      useAuthStore.getState().setAccessToken(data.accessToken);
      return data.accessToken;
    } catch {
      useAuthStore.getState().clear();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();
 
  return refreshPromise;
}
 
type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean };
 
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryableConfig | undefined;
 
    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/refresh')
    ) {
      originalRequest._retry = true;
 
      const newToken = await refreshAccessToken();
 
      if (newToken) {
        originalRequest.headers = originalRequest.headers ?? {};
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      }
    }
 
    return Promise.reject(error);
  }
);