import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ─── Ngân hàng đề ──────────────────────────────────────────────────────

export interface AdminQuestion {
  id: number;
  level: number;
  typeCode: string;
  typeName: string;
  section: string;
  question: string;
  optionCount: number;
  correct: string;
  hasAudio: boolean;
  groupId: number | null;
  testId: number | null;
  source: string | null;
  createdAt: string;
}

export interface BankSummary {
  bySection: Record<string, number>;
  byLevel: Record<string, number>;
  total: number;
}

export async function fetchBankSummary() {
  const { data } = await apiClient.get<ApiResponse<BankSummary>>('/admin/questions/summary');
  return data.data;
}

export async function fetchBankQuestions(params: {
  level?: number;
  section?: string;
  q?: string;
  page: number;
  size: number;
}) {
  const { data } = await apiClient.get<ApiResponse<Page<AdminQuestion>>>('/admin/questions', { params });
  return data.data;
}

export async function deleteBankQuestion(id: number) {
  await apiClient.delete(`/admin/questions/${id}`);
}

export interface AdminQuestionDetail {
  id: number;
  level: number;
  typeCode: string;
  typeName: string;
  section: string;
  question: string;
  options: string[];
  correct: string;
  explanation: string | null;
  transcript: string | null;
  audioUrl: string | null;
  groupId: number | null;
  testId: number | null;
}

export async function fetchAdminQuestion(id: number) {
  const { data } = await apiClient.get<ApiResponse<AdminQuestionDetail>>(`/admin/questions/${id}`);
  return data.data;
}

/** Fields left undefined stay unchanged; an empty explanation or transcript clears it. */
export async function updateAdminQuestion(
  id: number,
  payload: { question?: string; options?: string[]; correct?: string; explanation?: string; transcript?: string }
) {
  const { data } = await apiClient.put<ApiResponse<AdminQuestionDetail>>(`/admin/questions/${id}`, payload);
  return data.data;
}

export interface AdminTest {
  id: number;
  level: number;
  title: string | null;
  year: number | null;
  month: number | null;
  hasAudio: boolean;
  questionCount: number;
  activeCount: number;
}

export async function fetchAdminTests() {
  const { data } = await apiClient.get<ApiResponse<AdminTest[]>>('/admin/tests');
  return data.data;
}

/** Hard delete: the test, all its questions and groups. Practice history is kept. */
export async function deleteAdminTest(id: number) {
  await apiClient.delete(`/admin/tests/${id}`);
}

/** Hard delete of every question and group that was hidden earlier. */
export async function purgeHiddenQuestions() {
  const { data } = await apiClient.delete<ApiResponse<{ deleted: number }>>('/admin/questions/hidden');
  return data.data.deleted;
}

// ─── Import ────────────────────────────────────────────────────────────

export interface ImportPreview {
  previewId: string;
  questionCount: number;
  groupCount: number;
  testTitle: string | null;
  errors: { path: string; message: string }[];
  duplicates: { path: string; existing: string }[];
  canCommit: boolean;
}

export interface ImportCommit {
  createdQuestions: number;
  createdGroups: number;
  skipped: number;
  overwritten: number;
  testId: number | null;
}

export async function previewImport(file: unknown) {
  const { data } = await apiClient.post<ApiResponse<ImportPreview>>('/questions/import/preview', file);
  return data.data;
}

export async function commitImport(previewId: string, onDuplicate: 'skip' | 'overwrite') {
  const { data } = await apiClient.post<ApiResponse<ImportCommit>>('/questions/import/commit', {
    previewId,
    onDuplicate,
  });
  return data.data;
}

// ─── Người dùng ────────────────────────────────────────────────────────

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: string;
  targetLevel: number | null;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface UserSummary {
  total: number;
  active: number;
  locked: number;
  newLast30Days: number;
}

export async function fetchUserSummary() {
  const { data } = await apiClient.get<ApiResponse<UserSummary>>('/admin/users/summary');
  return data.data;
}

export async function fetchUsers(params: { q?: string; status?: string; page: number; size: number }) {
  const { data } = await apiClient.get<ApiResponse<Page<AdminUser>>>('/admin/users', { params });
  return data.data;
}

export async function setUserEnabled(id: string, enabled: boolean) {
  const { data } = await apiClient.put<ApiResponse<AdminUser>>(`/admin/users/${id}/enabled`, { enabled });
  return data.data;
}

// ─── Trạng thái server ─────────────────────────────────────────────────

export interface ServerStatus {
  time: string;
  memory: { totalBytes: number; usedBytes: number; freeBytes: number };
  jvm: { heapUsedBytes: number; heapMaxBytes: number; threads: number; uptimeMs: number; javaVersion: string };
  cpuLoadPercent: number | null;
  loadAverage: number | null;
  cpuCores: number;
  disk: { totalBytes: number; usedBytes: number; freeBytes: number };
  database: { up: boolean; sizeBytes: number | null; activeConnections: number | null; maxConnections: number | null };
  cache: { up: boolean; usedMemoryBytes: number | null; pingMs: number | null };
  traffic: { requests: number; serverErrors: number; clientErrors: number };
  counts: { users: number; words: number; activeQuestions: number; sessions: number; mediaFiles: number; mediaBytes: number };
  mediaProvider: string;
}

export async function fetchServerStatus() {
  const { data } = await apiClient.get<ApiResponse<ServerStatus>>('/admin/server');
  return data.data;
}
