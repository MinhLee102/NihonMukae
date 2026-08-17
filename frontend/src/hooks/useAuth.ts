import { useAuthStore } from '@/store/authStore';
import { logoutRequest } from '@/lib/api/auth';
 
export function useAuth() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const clear = useAuthStore((s) => s.clear);
 
  async function logout() {
    try {
      await logoutRequest();
    } finally {
      // Clear client state even if the network call fails — the user
      // clicked logout, so the UI should reflect that regardless.
      clear();
    }
  }
 
  return { user, isAuthenticated, isInitializing, logout };
}