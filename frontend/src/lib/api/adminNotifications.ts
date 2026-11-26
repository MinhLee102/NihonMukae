import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export type NotificationKind = 'feedback' | 'contributions' | 'users';

export interface NotificationItem {
  id: string;
  title: string;
  detail: string;
  createdAt: string | null;
}

/** Things waiting for an admin; `href` opens the page already filtered to them. */
export interface NotificationGroup {
  kind: NotificationKind;
  label: string;
  href: string;
  count: number;
  latest: NotificationItem[];
}

export interface AdminNotifications {
  total: number;
  groups: NotificationGroup[];
}

export async function fetchAdminNotifications(): Promise<AdminNotifications> {
  const { data } = await apiClient.get<ApiResponse<AdminNotifications>>('/admin/notifications');
  return data.data;
}
