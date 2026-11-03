import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';
import type { Page } from '@/lib/api/admin';

export type FeedbackCategory = 'exam_error' | 'bug' | 'suggestion' | 'transcript' | 'other';
export type FeedbackStatus = 'new' | 'seen' | 'fixed' | 'rejected';

export const CATEGORY_LABELS: Record<FeedbackCategory, string> = {
  exam_error: 'Đề thi sai',
  bug: 'Lỗi hệ thống',
  suggestion: 'Đề xuất',
  transcript: 'Transcript câu nghe',
  other: 'Khác',
};

export const STATUS_LABELS: Record<FeedbackStatus, string> = {
  new: 'Mới',
  seen: 'Đã xem',
  fixed: 'Đã xử lý',
  rejected: 'Từ chối',
};

/** Ties a piece of feedback to the question the user was looking at. */
export interface FeedbackContext {
  questionId?: number;
  sessionId?: string;
  testId?: number;
}

export async function sendFeedback(payload: {
  category: FeedbackCategory;
  message: string;
  pageUrl: string;
  context?: FeedbackContext;
}) {
  await apiClient.post('/feedback', payload);
}

// ─── Admin ─────────────────────────────────────────────────────────────

export interface FeedbackRow {
  id: number;
  userId: string | null;
  userName: string | null;
  userEmail: string | null;
  category: FeedbackCategory;
  message: string;
  pageUrl: string | null;
  context: FeedbackContext | null;
  status: FeedbackStatus;
  adminNote: string | null;
  createdAt: string;
}

export async function fetchFeedback(params: { status?: string; category?: string; page: number; size: number }) {
  const { data } = await apiClient.get<ApiResponse<Page<FeedbackRow>>>('/admin/feedback', { params });
  return data.data;
}

export async function fetchFeedbackSummary() {
  const { data } = await apiClient.get<ApiResponse<{ byStatus: Record<FeedbackStatus, number> }>>(
    '/admin/feedback/summary'
  );
  return data.data.byStatus;
}

export async function updateFeedback(id: number, payload: { status: FeedbackStatus; adminNote: string | null }) {
  await apiClient.put(`/admin/feedback/${id}`, payload);
}
