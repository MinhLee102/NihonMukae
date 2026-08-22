import { apiClient } from '@/lib/api/client';

export interface MeaningDto {
  lang: string;
  pos?: string;
  meaning: string;
}

export interface ExampleDto {
  sentence: string;
  translation: string;
}

export interface WordDto {
  id: number;
  kanji: string;
  kana: string;
  hanViet?: string;
  pronunciation?: string;
  pos?: string;
  jlptLevel?: number;
  meanings: MeaningDto[];
  examples: ExampleDto[];
}

export interface PageData<T> {
  content: T[];
  pageable?: unknown;
  totalElements: number;
  totalPages: number;
  last?: boolean;
  size: number;
  number: number;
  sort?: unknown;
  numberOfElements?: number;
  first?: boolean;
  empty?: boolean;
}

export interface SearchResultResponse {
  results: PageData<WordDto>;
  fuzzyFallback: boolean;
}

export interface HistoryItemDto {
  id: number;
  rawQuery: string;
  searchedAt: string;
  word?: WordDto;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

export const searchDictionary = async (
  q: string,
  lang: string = 'vi',
  page: number = 0,
  size: number = 20
): Promise<ApiResponse<SearchResultResponse>> => {
  const { data } = await apiClient.get<ApiResponse<SearchResultResponse>>('/dictionary/search', {
    params: { q, lang, page, size },
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

// Lookup a word by its kana and optional kanji. Returns the word details if found.
// Later use for the "lookup" feature, where users can click on a word to see its full details.
export const lookupWord = async (
  kana: string,
  kanji?: string
): Promise<ApiResponse<WordDto>> => {
  const { data } = await apiClient.get<ApiResponse<WordDto>>('/dictionary/lookup', {
    params: { kana, kanji },
  });
  return data;
};