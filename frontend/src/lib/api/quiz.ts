import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export type QuizKind = 'meaning' | 'reading' | 'cloze';

export const QUIZ_KINDS: { kind: QuizKind; label: string; hint: string }[] = [
  { kind: 'meaning', label: 'Chọn nghĩa', hint: 'Cho từ, chọn nghĩa đúng' },
  { kind: 'reading', label: 'Chọn cách đọc', hint: 'Cho Hán tự, chọn cách đọc hiragana' },
  { kind: 'cloze', label: 'Điền vào câu', hint: 'Câu ví dụ bị khuyết một từ, chọn từ đúng' },
];

/** One question with four options; `answer` is the index of the right option. */
export interface QuizQuestionDto {
  cardId: string;
  kind: QuizKind;
  /** The word asked about, or the sentence with a blank for "cloze". */
  prompt: string;
  options: string[];
  answer: number;
  word: string;
  reading: string | null;
  meaning: string;
  translation: string | null;
}

export interface QuizAttemptDto {
  id: string;
  deckId: string | null;
  deckName: string;
  kinds: QuizKind[];
  total: number;
  correct: number;
  createdAt: string;
}

export const getQuiz = async (deckId: string, kinds: QuizKind[], count?: number): Promise<QuizQuestionDto[]> => {
  const { data } = await apiClient.get<ApiResponse<QuizQuestionDto[]>>(`/flashcard/decks/${deckId}/quiz`, {
    // Spring reads "a,b,c" into a list; axios would otherwise send kinds[]=a&kinds[]=b
    params: { kinds: kinds.join(','), count },
  });
  return data.data;
};

export const saveQuizAttempt = async (input: {
  deckId: string;
  kinds: QuizKind[];
  total: number;
  correct: number;
}): Promise<QuizAttemptDto> => {
  const { data } = await apiClient.post<ApiResponse<QuizAttemptDto>>('/flashcard/quiz/attempts', input);
  return data.data;
};

export const getQuizAttempts = async (deckId: string, limit = 10): Promise<QuizAttemptDto[]> => {
  const { data } = await apiClient.get<ApiResponse<QuizAttemptDto[]>>('/flashcard/quiz/attempts', {
    params: { deckId, limit },
  });
  return data.data;
};
