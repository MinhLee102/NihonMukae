import { create } from 'zustand';
import type { AuthUser } from '@/types/auth';
 
interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  /** true until the initial silent refresh (on app load) has resolved */
  isInitializing: boolean;
  setAccessToken: (token: string | null) => void;
  setInitializing: (value: boolean) => void;
  clear: () => void;
}
 
/**
 * Decodes the JWT payload to read `email`/`name` claims for display purposes
 * only (never trust this for authorization — the backend already did that).
 *
 * NOTE: adjust the claim names below to match whatever JwtUtil.java actually
 * puts in the token (this assumes `email` and optionally `name`).
 */
function decodeUserFromToken(token: string): AuthUser | null {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = JSON.parse(atob(normalized)) as {
      email?: string;
      sub?: string;
      name?: string;
      role?: string;
    };
    const email = json.email ?? json.sub;
    if (!email) return null;
    return { email, name: json.name, role: json.role };
  } catch {
    return null;
  }
}
 
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,
  isInitializing: true,
  setAccessToken: (token) =>
    set({
      accessToken: token,
      user: token ? decodeUserFromToken(token) : null,
      isAuthenticated: !!token,
    }),
  setInitializing: (value) => set({ isInitializing: value }),
  clear: () => set({ accessToken: null, user: null, isAuthenticated: false }),
}));