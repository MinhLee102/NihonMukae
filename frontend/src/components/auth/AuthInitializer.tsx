'use client';
 
import { useEffect } from 'react';
import { refreshAccessToken } from '@/lib/api/client';
import { useAuthStore } from '@/store/authStore';
 
export function AuthInitializer() {
  const setInitializing = useAuthStore((s) => s.setInitializing);
 
  useEffect(() => {
    let cancelled = false;
    refreshAccessToken().finally(() => {
      if (!cancelled) setInitializing(false);
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
 
  return null;
}