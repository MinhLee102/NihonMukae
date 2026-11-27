import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface GrammarSummary {
  id: number;
  /** Main JLPT level (the lowest one the pattern belongs to). */
  level: number;
  /** Other levels that also list this pattern. */
  extraLevels: number[];
  pattern: string;
  reading: string | null;
  meaning: string;
  category: string | null;
}

export interface GrammarDetail extends GrammarSummary {
  /** How to attach it, e.g. "Vない(bỏ ない) + ざるを得ない". */
  connection: string[];
  explanation: string;
  notes: string | null;
  similar: { pattern: string; diff: string }[];
  examples: { ja: string; vi: string }[];
}

/** A pattern found in a sentence at [start, end). */
export interface GrammarFound {
  start: number;
  end: number;
  point: GrammarSummary;
}

/** Longest text the detect endpoint accepts. */
export const GRAMMAR_DETECT_MAX = 300;

export async function searchGrammar(q: string, level?: number): Promise<GrammarSummary[]> {
  const { data } = await apiClient.get<ApiResponse<GrammarSummary[]>>('/grammar/search', { params: { q, level } });
  return data.data;
}

export async function fetchGrammarLevel(level: number): Promise<GrammarSummary[]> {
  const { data } = await apiClient.get<ApiResponse<GrammarSummary[]>>(`/grammar/levels/${level}`);
  return data.data;
}

export async function fetchGrammarDetail(id: number): Promise<GrammarDetail> {
  const { data } = await apiClient.get<ApiResponse<GrammarDetail>>(`/grammar/${id}`);
  return data.data;
}

export async function detectGrammar(text: string): Promise<GrammarFound[]> {
  const { data } = await apiClient.post<ApiResponse<GrammarFound[]>>('/grammar/detect', { text });
  return data.data;
}

/** "N3", or "N4 · N3" when the pattern is also listed at other levels. */
export function grammarLevels(p: Pick<GrammarSummary, 'level' | 'extraLevels'>): string {
  return [p.level, ...p.extraLevels].map((l) => `N${l}`).join(' · ');
}
