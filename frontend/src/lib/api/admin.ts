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
  approved: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface UserSummary {
  total: number;
  active: number;
  locked: number;
  newLast30Days: number;
  pending: number;
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

export async function setUserApproved(id: string, approved: boolean) {
  const { data } = await apiClient.put<ApiResponse<AdminUser>>(`/admin/users/${id}/approved`, { approved });
  return data.data;
}

/** Từ chối đăng ký: xoá hẳn tài khoản đang chờ duyệt. */
export async function rejectUser(id: string) {
  await apiClient.delete(`/admin/users/${id}`);
}

// ─── Từ điển ───────────────────────────────────────────────────────────

export interface DictSummary {
  entries: number;
  withVietnamese: number;
  edited: number;
  custom: number;
  pendingContributions: number;
}

export interface DictEntryRow {
  id: number;
  headword: string;
  reading: string;
  pos: string | null;
  jlptLevel: number | null;
  priorityGroup: string;
  hasVietnamese: boolean;
  preview: string | null;
  edited: boolean;
  custom: boolean;
}

export interface DictExampleInput {
  ja: string;
  vi: string;
  source?: string | null;
  sourceId?: number | null;
}

export interface DictEntryInput {
  word: string | null;
  reading: string;
  otherForms: string[];
  otherReadings: string[];
  usuallyKana: boolean;
  pos: string | null;
  jlptLevel: number | null;
  hanViet: string | null;
  meanings: string[];
  examples: DictExampleInput[];
}

export interface DictEntryDetail {
  entry: {
    id: number;
    kanji: string | null;
    kana: string;
    headword: string;
    otherForms: string[];
    otherReadings: string[];
    hanViet: string | null;
    pos: string | null;
    jlptLevel: number | null;
    hasVietnamese: boolean;
    custom: boolean;
    meanings: { lang: string; meaning: string }[];
    examples: { sentence: string; translation: string; source: string | null; sourceId: number | null }[];
  };
  usuallyKana: boolean;
  glossEn: string[];
  edited: boolean;
  priorityGroup: string;
}

export interface DictContributionRow {
  id: number;
  entryId: number;
  headword: string;
  reading: string;
  kind: 'meaning' | 'example' | 'note';
  ja: string | null;
  vi: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewNote: string | null;
  userName: string;
  userEmail: string;
  createdAt: string;
  reviewedAt: string | null;
}

export async function fetchDictSummary() {
  const { data } = await apiClient.get<ApiResponse<DictSummary>>('/admin/dictionary/summary');
  return data.data;
}

export async function fetchDictEntries(params: { q?: string; filter?: string; page: number; size: number }) {
  const { data } = await apiClient.get<ApiResponse<Page<DictEntryRow>>>('/admin/dictionary/entries', { params });
  return data.data;
}

export async function fetchDictEntry(id: number) {
  const { data } = await apiClient.get<ApiResponse<DictEntryDetail>>(`/admin/dictionary/entries/${id}`);
  return data.data;
}

export async function createDictEntry(payload: DictEntryInput) {
  const { data } = await apiClient.post<ApiResponse<DictEntryDetail>>('/admin/dictionary/entries', payload);
  return data.data;
}

export async function updateDictEntry(id: number, payload: DictEntryInput) {
  const { data } = await apiClient.put<ApiResponse<DictEntryDetail>>(`/admin/dictionary/entries/${id}`, payload);
  return data.data;
}

export async function deleteDictEntry(id: number) {
  await apiClient.delete(`/admin/dictionary/entries/${id}`);
}

export async function fetchDictContributions(params: { status?: string; page: number; size: number }) {
  const { data } = await apiClient.get<ApiResponse<Page<DictContributionRow>>>('/admin/dictionary/contributions', {
    params,
  });
  return data.data;
}

export async function reviewDictContribution(id: number, status: 'APPROVED' | 'REJECTED' | 'PENDING', note?: string) {
  await apiClient.put(`/admin/dictionary/contributions/${id}`, { status, note });
}

export async function deleteDictContribution(id: number) {
  await apiClient.delete(`/admin/dictionary/contributions/${id}`);
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
