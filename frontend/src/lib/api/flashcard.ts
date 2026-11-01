import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export interface DeckDto {
  id: string;
  name: string;
  description?: string;
  color?: string;
  icon?: string;
  cardCount: number;
  learnedCount: number;
  dueCount: number;
  createdAt: string;
}

export interface DeckInput {
  name: string;
  description?: string;
  color?: string;
  icon?: string;
}

export interface CardDto {
  id: string;
  deckId: string;
  front: string;
  back: string;
  imageUrl?: string;
  wordId?: number;
  createdAt: string;
  /** Only set by the deck card list; null/undefined means not studied yet. */
  nextReviewAt?: string | null;
}

export interface CardInput {
  front?: string;
  back?: string;
  imageUrl?: string;
  wordId?: number;
}

export type ReviewRating = 'hard' | 'normal' | 'easy';

export interface ReviewResultDto {
  intervalDays: number;
  repetitions: number;
  easeFactor: number;
  nextReviewAt: string;
}

export interface FlashcardStatsDto {
  totalCards: number;
  learnedCards: number;
  dueToday: number;
  streak: number;
}

export const getDecks = async (): Promise<DeckDto[]> => {
  const { data } = await apiClient.get<ApiResponse<DeckDto[]>>('/flashcard/decks');
  return data.data;
};

export const createDeck = async (input: DeckInput): Promise<DeckDto> => {
  const { data } = await apiClient.post<ApiResponse<DeckDto>>('/flashcard/decks', input);
  return data.data;
};

export const updateDeck = async (id: string, input: DeckInput): Promise<DeckDto> => {
  const { data } = await apiClient.put<ApiResponse<DeckDto>>(`/flashcard/decks/${id}`, input);
  return data.data;
};

export const deleteDeck = async (id: string): Promise<void> => {
  await apiClient.delete(`/flashcard/decks/${id}`);
};

export const getCards = async (deckId: string): Promise<CardDto[]> => {
  const { data } = await apiClient.get<ApiResponse<CardDto[]>>(`/flashcard/decks/${deckId}/cards`);
  return data.data;
};

export const createCard = async (deckId: string, input: CardInput): Promise<CardDto> => {
  const { data } = await apiClient.post<ApiResponse<CardDto>>(`/flashcard/decks/${deckId}/cards`, input);
  return data.data;
};

export const updateCard = async (id: string, input: CardInput): Promise<CardDto> => {
  const { data } = await apiClient.put<ApiResponse<CardDto>>(`/flashcard/cards/${id}`, input);
  return data.data;
};

export const deleteCard = async (id: string): Promise<void> => {
  await apiClient.delete(`/flashcard/cards/${id}`);
};

export const getStudyCards = async (deckId: string): Promise<CardDto[]> => {
  const { data } = await apiClient.get<ApiResponse<CardDto[]>>(`/flashcard/decks/${deckId}/study`);
  return data.data;
};

export const reviewCard = async (cardId: string, rating: ReviewRating): Promise<ReviewResultDto> => {
  const { data } = await apiClient.post<ApiResponse<ReviewResultDto>>(`/flashcard/cards/${cardId}/review`, {
    rating,
  });
  return data.data;
};

export const getFlashcardStats = async (): Promise<FlashcardStatsDto> => {
  const { data } = await apiClient.get<ApiResponse<FlashcardStatsDto>>('/flashcard/stats');
  return data.data;
};

/** Message from the backend ApiResponse envelope, or a fallback. */
export function getApiErrorMessage(error: unknown, fallback = 'Có lỗi xảy ra, vui lòng thử lại.'): string {
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return message ?? fallback;
}
