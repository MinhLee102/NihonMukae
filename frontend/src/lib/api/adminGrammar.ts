import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface GrammarLevelSummary {
  level: number;
  total: number;
  ready: number;
  pending: number;
  invalid: number;
}

export interface GrammarPointRow {
  id: number;
  pattern: string;
  reading: string | null;
  category: string | null;
  hint: string | null;
  ready: boolean;
  invalid: boolean;
}

export interface GrammarListItem {
  pattern: string;
  reading?: string;
  category?: string;
  hint?: string;
}

export interface GrammarListResult {
  added: number;
  skipped: number;
  problems: string[];
}

/** One pattern to put in the explain prompt; id looks like "G123". */
export interface GrammarBatchItem {
  id: string;
  level: string;
  pattern: string;
  reading: string | null;
  category: string | null;
  hint: string | null;
}

export interface GrammarIssue {
  id: string;
  pattern: string;
  messages: string[];
}

export interface GrammarContentResult {
  saved: number;
  invalid: number;
  errors: GrammarIssue[];
  warnings: GrammarIssue[];
}

export async function fetchGrammarSummary(): Promise<GrammarLevelSummary[]> {
  const { data } = await apiClient.get<ApiResponse<GrammarLevelSummary[]>>('/admin/grammar/summary');
  return data.data;
}

export async function fetchGrammarPoints(level: number): Promise<GrammarPointRow[]> {
  const { data } = await apiClient.get<ApiResponse<GrammarPointRow[]>>('/admin/grammar/points', { params: { level } });
  return data.data;
}

export async function importGrammarList(level: number, items: GrammarListItem[]): Promise<GrammarListResult> {
  const { data } = await apiClient.post<ApiResponse<GrammarListResult>>('/admin/grammar/list', { level, items });
  return data.data;
}

export async function fetchGrammarBatch(level: number, size?: number): Promise<GrammarBatchItem[]> {
  const { data } = await apiClient.get<ApiResponse<GrammarBatchItem[]>>('/admin/grammar/batch', {
    params: { level, size },
  });
  return data.data;
}

export async function saveGrammarContent(entries: unknown[]): Promise<GrammarContentResult> {
  const { data } = await apiClient.post<ApiResponse<GrammarContentResult>>('/admin/grammar/content', entries);
  return data.data;
}

export async function deleteGrammarPoint(id: number): Promise<void> {
  await apiClient.delete(`/admin/grammar/points/${id}`);
}

export async function restoreGrammarPoint(id: number): Promise<void> {
  await apiClient.put(`/admin/grammar/points/${id}/restore`);
}

/** The JSON array inside an AI answer, ignoring ```json fences and text around it. */
export function parseJsonArray(text: string): unknown[] {
  const start = text.indexOf('[');
  const end = text.lastIndexOf(']');
  if (start < 0 || end <= start) throw new Error('Không thấy mảng JSON trong câu trả lời.');
  const value: unknown = JSON.parse(text.slice(start, end + 1));
  if (!Array.isArray(value)) throw new Error('Câu trả lời không phải mảng JSON.');
  return value;
}
