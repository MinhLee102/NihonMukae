import axios from 'axios';
import { API_BASE_URL } from '@/lib/api/client';
import { useAuthStore } from '@/store/authStore';

/**
 * Audio and images go straight to the API domain: Vercel's proxy caps request
 * bodies, and audio needs Range requests that are cheaper without a hop.
 */
export const MEDIA_BASE_URL = process.env.NEXT_PUBLIC_MEDIA_URL ?? API_BASE_URL;

/** Turns a stored "/media/{id}" path into a playable address. Other URLs pass through. */
export function resolveMediaUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined;
  return url.startsWith('/media/') ? `${MEDIA_BASE_URL}${url}` : url;
}

export interface UploadedMedia {
  id: string;
  url: string;
  kind: 'audio' | 'image';
  mimeType: string;
  sizeBytes: number;
  originalName: string | null;
  duplicate: boolean;
}

export async function uploadMedia(file: File): Promise<UploadedMedia> {
  const form = new FormData();
  form.append('file', file);
  const token = useAuthStore.getState().accessToken;
  const { data } = await axios.post<{ data: UploadedMedia }>(`${MEDIA_BASE_URL}/admin/media`, form, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    withCredentials: true,
  });
  return data.data;
}
