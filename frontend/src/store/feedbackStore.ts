import { create } from 'zustand';
import type { FeedbackCategory, FeedbackContext } from '@/lib/api/feedback';

interface FeedbackState {
  isOpen: boolean;
  category: FeedbackCategory;
  context?: FeedbackContext;
  /** Text shown above the form, for example which question is being reported. */
  hint?: string;
  /** Opens the dialog; category, context and hint prefill it when the user came from a question. */
  open: (preset?: { category?: FeedbackCategory; context?: FeedbackContext; hint?: string }) => void;
  close: () => void;
}

export const useFeedbackStore = create<FeedbackState>((set) => ({
  isOpen: false,
  category: 'suggestion',
  open: (preset) =>
    set({
      isOpen: true,
      category: preset?.category ?? 'suggestion',
      context: preset?.context,
      hint: preset?.hint,
    }),
  close: () => set({ isOpen: false }),
}));
