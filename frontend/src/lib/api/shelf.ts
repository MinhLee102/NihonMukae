import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface ShelfTokenPart {
  text: string;
  /** Furigana for this part; absent for kana and okurigana. */
  ruby?: string;
}

export interface ShelfToken {
  parts: ShelfTokenPart[];
  /** Dictionary form of the word, used for lookup. */
  base: string;
  /** Hiragana reading of the whole token (only when it contains kanji). */
  reading?: string;
  /** false for particles and punctuation, which are not clickable. */
  lookup: boolean;
}

export interface ShelfItemDto {
  id: string;
  title: string;
  /** Only in the list response. */
  excerpt?: string;
  /** Only in the detail response. */
  content?: string;
  tokens?: ShelfToken[];
  createdAt: string;
  updatedAt: string;
}

export interface ShelfItemInput {
  title?: string;
  content: string;
}

export const getShelfItems = async (): Promise<ShelfItemDto[]> => {
  const { data } = await apiClient.get<ApiResponse<ShelfItemDto[]>>('/shelf');
  return data.data;
};

export const getShelfItem = async (id: string): Promise<ShelfItemDto> => {
  const { data } = await apiClient.get<ApiResponse<ShelfItemDto>>(`/shelf/${id}`);
  return data.data;
};

export const createShelfItem = async (input: ShelfItemInput): Promise<ShelfItemDto> => {
  const { data } = await apiClient.post<ApiResponse<ShelfItemDto>>('/shelf', input);
  return data.data;
};

export const updateShelfItem = async (id: string, input: ShelfItemInput): Promise<ShelfItemDto> => {
  const { data } = await apiClient.put<ApiResponse<ShelfItemDto>>(`/shelf/${id}`, input);
  return data.data;
};

export const deleteShelfItem = async (id: string): Promise<void> => {
  await apiClient.delete(`/shelf/${id}`);
};
