import { apiClient } from '@/lib/api/client';

export interface MeaningDto {
  /** "vi" = nghĩa tiếng Việt, "en" = nghĩa JMdict (từ chưa có bản dịch Việt) */
  lang: string;
  pos?: string | null;
  meaning: string;
}

export interface ExampleDto {
  sentence: string;
  translation: string;
  /** "ai" | "tatoeba" | "admin" */
  source?: string | null;
  /** Mã câu Tatoeba, dùng để dẫn link ghi nguồn */
  sourceId?: number | null;
}

export type ContributionKind = 'meaning' | 'example' | 'note';

/** Đóng góp đã được admin duyệt. */
export interface ContributionDto {
  id: number;
  kind: ContributionKind;
  ja?: string | null;
  vi: string;
  contributor: string;
  createdAt: string;
}

export interface WordDto {
  id: number;
  /** null với từ thuần kana */
  kanji: string | null;
  kana: string;
  headword: string;
  otherForms: string[];
  otherReadings: string[];
  hanViet?: string | null;
  pos?: string | null;
  jlptLevel?: number | null;
  hasVietnamese: boolean;
  custom: boolean;
  meanings: MeaningDto[];
  examples: ExampleDto[];
  contributions: ContributionDto[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type MatchType = 'word_form' | 'meaning' | 'fuzzy' | 'none';

export interface SearchResultResponse {
  results: PageResponse<WordDto>;
  matchType: MatchType;
}

export interface HistoryItemDto {
  id: number;
  wordId: number | null;
  kanji: string | null;
  kana: string | null;
  meaning: string | null;
  rawQuery: string;
  searchedAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

export const searchDictionary = async (
  q: string,
  page: number = 0,
  size: number = 20
): Promise<ApiResponse<SearchResultResponse>> => {
  const { data } = await apiClient.get<ApiResponse<SearchResultResponse>>('/dictionary/search', {
    params: { q, page, size },
  });
  return data;
};

export const getSearchHistory = async (
  page: number = 0,
  size: number = 10
): Promise<ApiResponse<PageResponse<HistoryItemDto>>> => {
  const { data } = await apiClient.get<ApiResponse<PageResponse<HistoryItemDto>>>('/dictionary/history', {
    params: { page, size },
  });
  return data;
};

// Lookup a word for inline popups (My Shelf, JLPT). Pass the kanji form and/or the
// kana reading; kana is optional when the kanji form is known (e.g. inflected verbs).
export const lookupWord = async (params: {
  kanji?: string;
  kana?: string;
}): Promise<ApiResponse<WordDto>> => {
  const { data } = await apiClient.get<ApiResponse<WordDto>>('/dictionary/lookup', { params });
  return data;
};

// ─── Đóng góp của người dùng ───────────────────────────────────────────

export interface MyContribution {
  id: number;
  entryId: number;
  headword: string;
  kind: ContributionKind;
  ja?: string | null;
  vi: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewNote?: string | null;
  createdAt: string;
}

export async function submitContribution(
  entryId: number,
  payload: { kind: ContributionKind; ja?: string; vi: string }
) {
  const { data } = await apiClient.post<ApiResponse<MyContribution>>(
    `/dictionary/entries/${entryId}/contributions`,
    payload
  );
  return data;
}

export async function fetchMyContributions(page = 0, size = 20) {
  const { data } = await apiClient.get<ApiResponse<PageResponse<MyContribution>>>('/dictionary/contributions/mine', {
    params: { page, size },
  });
  return data.data;
}
