import { apiClient } from '@/lib/api/client';
import type { ApiResponse } from '@/lib/api/dictionary';

export type Section = 'moji' | 'goi' | 'bunpou' | 'dokkai' | 'chokai';

export const SECTION_ORDER: Section[] = ['moji', 'goi', 'bunpou', 'dokkai', 'chokai'];

export const SECTION_LABELS: Record<Section, { vi: string; ja: string }> = {
  moji: { vi: 'Chữ Hán', ja: '文字' },
  goi: { vi: 'Từ vựng', ja: '語彙' },
  bunpou: { vi: 'Ngữ pháp', ja: '文法' },
  dokkai: { vi: 'Đọc hiểu', ja: '読解' },
  chokai: { vi: 'Nghe hiểu', ja: '聴解' },
};

export interface QuestionTypeDto {
  id: number;
  code: string;
  nameVi: string;
  nameJa?: string;
  section: Section;
  groupBased: boolean;
  /** Number of questions currently in the bank for the requested level. */
  available: number;
}

export interface TestSummaryDto {
  id: number;
  title: string;
  sourceNote?: string;
  year?: number;
  month?: number;
  questionCount: number;
  durationMin: number;
  sections: Section[];
}

export interface BlueprintTypeDto {
  typeId: number;
  code: string;
  nameVi: string;
  nameJa?: string;
  quantity: number;
  pointsPerQuestion: number;
  available: number;
}

export interface BlueprintBlockDto {
  blockNo: number;
  name: string;
  durationMin: number;
  types: BlueprintTypeDto[];
}

export interface BlueprintDto {
  level: number;
  blocks: BlueprintBlockDto[];
  shortages: string[];
}

export type FeedbackMode = 'instant' | 'after';

export interface StartPracticeInput {
  mode: 'practice';
  level: number;
  typeIds: number[];
  questionCount: number;
  timeLimitMin: number | null;
  feedbackMode: FeedbackMode;
  /** Practice one whole listening set (random) instead of picking questions by type. */
  listeningSet?: boolean;
}

export interface ListeningSetDto {
  id: number;
  title: string;
  questionCount: number;
}

export interface StartExamInput {
  mode: 'exam';
  level: number;
  testId?: number;
  random?: boolean;
}

export type BlockStatus = 'pending' | 'active' | 'closed';

export interface BlockViewDto {
  blockNo: number;
  name: string;
  durationMin?: number | null;
  questionCount: number;
  status: BlockStatus;
  startedAt?: string | null;
  /** null for a block without a time limit (unlimited practice). */
  deadline?: string | null;
  /** Audio of the whole listening section, only on the block that holds the listening questions. */
  audioUrl?: string;
}

export interface GroupViewDto {
  id: number;
  title?: string;
  passage?: string;
  audioUrl?: string;
  imageUrl?: string;
}

export interface QuestionViewDto {
  position: number;
  blockNo: number;
  /** true for questions in blocks that are not open: only the number and the chosen answer are known. */
  hidden: boolean;
  questionId?: number;
  typeCode?: string;
  typeName?: string;
  section?: Section;
  groupId?: number;
  question?: string;
  options?: string[];
  audioUrl?: string;
  selected?: string;
  locked: boolean;
  correct?: boolean;
  correctAnswer?: string;
  explanation?: string;
  /** "問題 N": position of this question's type within its exam block. */
  mondaiNo?: number;
  /** Shared instruction of the question type, shown above the question. */
  instructionJa?: string;
  instructionVi?: string;
}

export interface SessionViewDto {
  id: string;
  mode: 'practice' | 'exam';
  level: number;
  title: string;
  status: 'in_progress' | 'submitted';
  feedbackMode: FeedbackMode;
  serverNow: string;
  startedAt: string;
  currentBlockNo?: number;
  blocks: BlockViewDto[];
  questions: QuestionViewDto[];
  groups: GroupViewDto[];
  warnings?: string[];
}

export interface AnswerResultDto {
  saved: boolean;
  locked: boolean;
  correct?: boolean;
  correctAnswer?: string;
  explanation?: string;
}

export interface SectionScoreDto {
  code: string;
  score: number;
  max: number;
  min: number;
  belowMin: boolean;
}

export interface JlptScoreDto {
  total: number;
  maxTotal: number;
  passScore?: number;
  passed?: boolean;
  complete: boolean;
  sections: SectionScoreDto[];
}

export interface TypeStatDto {
  typeCode: string;
  typeName: string;
  section: Section;
  correct: number;
  total: number;
}

export interface ResultQuestionDto {
  position: number;
  blockNo: number;
  questionId?: number;
  typeCode?: string;
  typeName?: string;
  section?: Section;
  groupId?: number;
  question: string;
  options: string[];
  audioUrl?: string;
  selected?: string;
  correctAnswer: string;
  correct: boolean;
  explanation?: string;
  points: number;
  mondaiNo?: number;
  instructionJa?: string;
  instructionVi?: string;
  /** Listening script (Japanese), added by admins or approved contributions. */
  transcript?: string;
}

export interface ResultDto {
  sessionId: string;
  mode: 'practice' | 'exam';
  level: number;
  title: string;
  startedAt: string;
  finishedAt: string;
  correct: number;
  total: number;
  unanswered: number;
  jlpt?: JlptScoreDto;
  byType: TypeStatDto[];
  questions: ResultQuestionDto[];
  groups: GroupViewDto[];
  listeningAudioUrl?: string;
}

export interface SessionSummaryDto {
  id: string;
  mode: 'practice' | 'exam';
  level: number;
  title: string;
  status: 'in_progress' | 'submitted';
  score?: number;
  maxScore?: number;
  correct: number;
  total: number;
  startedAt: string;
  finishedAt?: string;
}

export const getQuestionTypes = async (level: number): Promise<QuestionTypeDto[]> => {
  const { data } = await apiClient.get<ApiResponse<QuestionTypeDto[]>>('/question-types', { params: { level } });
  return data.data;
};

export const getTests = async (level: number): Promise<TestSummaryDto[]> => {
  const { data } = await apiClient.get<ApiResponse<TestSummaryDto[]>>('/jlpt/tests', { params: { level } });
  return data.data;
};

export const getBlueprint = async (level: number): Promise<BlueprintDto> => {
  const { data } = await apiClient.get<ApiResponse<BlueprintDto>>('/jlpt/blueprint', { params: { level } });
  return data.data;
};

export const getListeningSets = async (level: number): Promise<ListeningSetDto[]> => {
  const { data } = await apiClient.get<ApiResponse<ListeningSetDto[]>>('/jlpt/listening-sets', { params: { level } });
  return data.data;
};

export const startSession = async (input: StartPracticeInput | StartExamInput): Promise<SessionViewDto> => {
  const { data } = await apiClient.post<ApiResponse<SessionViewDto>>('/practice/sessions', input);
  return data.data;
};

export const getSession = async (id: string): Promise<SessionViewDto> => {
  const { data } = await apiClient.get<ApiResponse<SessionViewDto>>(`/practice/sessions/${id}`);
  return data.data;
};

export const saveAnswer = async (
  sessionId: string,
  questionId: number,
  selected: string | null
): Promise<AnswerResultDto> => {
  const { data } = await apiClient.put<ApiResponse<AnswerResultDto>>(
    `/practice/sessions/${sessionId}/answers/${questionId}`,
    { selected }
  );
  return data.data;
};

export const startBlock = async (sessionId: string, blockNo: number): Promise<SessionViewDto> => {
  const { data } = await apiClient.post<ApiResponse<SessionViewDto>>(
    `/practice/sessions/${sessionId}/blocks/${blockNo}/start`
  );
  return data.data;
};

export const submitBlock = async (sessionId: string, blockNo: number): Promise<SessionViewDto> => {
  const { data } = await apiClient.post<ApiResponse<SessionViewDto>>(
    `/practice/sessions/${sessionId}/blocks/${blockNo}/submit`
  );
  return data.data;
};

export const submitSession = async (sessionId: string): Promise<ResultDto> => {
  const { data } = await apiClient.post<ApiResponse<ResultDto>>(`/practice/sessions/${sessionId}/submit`);
  return data.data;
};

export type ShelfSaveScope = 'wrong' | 'all' | 'question';

/** Turns the session's snapshot into My Shelf items (Japanese text only); a long selection becomes one item per part. */
export const saveSessionToShelf = async (
  sessionId: string,
  scope: ShelfSaveScope,
  position?: number
): Promise<{ id: string; title: string }[]> => {
  const { data } = await apiClient.post<ApiResponse<{ id: string; title: string }[]>>(
    `/practice/sessions/${sessionId}/save-to-shelf`,
    { scope, position }
  );
  return data.data;
};

export const getResult = async (sessionId: string): Promise<ResultDto> => {
  const { data } = await apiClient.get<ApiResponse<ResultDto>>(`/practice/sessions/${sessionId}/result`);
  return data.data;
};

export const retrySession = async (sessionId: string): Promise<SessionViewDto> => {
  const { data } = await apiClient.post<ApiResponse<SessionViewDto>>(`/practice/sessions/${sessionId}/retry`);
  return data.data;
};

export const getSessions = async (): Promise<SessionSummaryDto[]> => {
  const { data } = await apiClient.get<ApiResponse<SessionSummaryDto[]>>('/practice/sessions');
  return data.data;
};

/** Remember warnings (e.g. the bank has fewer questions than requested) until the session screen shows them. */
export function stashSessionWarnings(sessionId: string, warnings?: string[]): void {
  try {
    if (warnings && warnings.length > 0) {
      sessionStorage.setItem(`jlpt:warnings:${sessionId}`, JSON.stringify(warnings));
    }
  } catch {
    // storage unavailable: the warning is just not shown
  }
}

export function takeSessionWarnings(sessionId: string): string[] {
  try {
    const raw = sessionStorage.getItem(`jlpt:warnings:${sessionId}`);
    sessionStorage.removeItem(`jlpt:warnings:${sessionId}`);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}
