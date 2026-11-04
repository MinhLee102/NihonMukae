"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { X } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useAuthModalStore } from "@/store/authModalStore";
import { useFeedbackStore } from "@/store/feedbackStore";
import { CATEGORY_LABELS, sendFeedback, type FeedbackCategory } from "@/lib/api/feedback";
import { getApiErrorMessage } from "@/lib/api/flashcard";

const MAX_LENGTH = 2000;
const CATEGORIES = Object.keys(CATEGORY_LABELS) as FeedbackCategory[];

/** Feedback form opened from the sidebar button or from "Báo lỗi" on a question. Text only. */
export function FeedbackDialog() {
  const pathname = usePathname();
  const { isOpen, category: presetCategory, context, hint, close } = useFeedbackStore();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const openAuthModal = useAuthModalStore((s) => s.open);

  const [category, setCategory] = useState<FeedbackCategory>("suggestion");
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setCategory(presetCategory);
      setMessage("");
      setSent(false);
      setError(null);
    }
  }, [isOpen, presetCategory]);

  useEffect(() => {
    if (!isOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && close();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [isOpen, close]);

  if (!isOpen) return null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (message.trim().length < 5) {
      setError("Nội dung quá ngắn.");
      return;
    }
    try {
      setSending(true);
      setError(null);
      await sendFeedback({ category, message: message.trim(), pageUrl: pathname, context });
      setSent(true);
    } catch (err) {
      setError(getApiErrorMessage(err, "Không gửi được góp ý, vui lòng thử lại."));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={close}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Góp ý"
        className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between">
          <h2 className="font-serif text-lg font-semibold text-neutral-900">Góp ý</h2>
          <button type="button" onClick={close} aria-label="Đóng" className="rounded-full p-1.5 text-neutral-400 hover:bg-neutral-100">
            <X size={16} />
          </button>
        </div>

        {!isAuthenticated ? (
          <div className="mt-4 text-sm text-neutral-600">
            <p>Bạn cần đăng nhập để gửi góp ý.</p>
            <button
              type="button"
              onClick={() => {
                close();
                openAuthModal("login");
              }}
              className="mt-4 rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
            >
              Đăng nhập
            </button>
          </div>
        ) : sent ? (
          <div className="mt-4 text-sm text-neutral-600">
            <p>Cảm ơn bạn, góp ý đã được gửi tới quản trị viên.</p>
            <button type="button" onClick={close} className="mt-4 rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white">
              Đóng
            </button>
          </div>
        ) : (
          <form onSubmit={submit} className="mt-4 flex flex-col gap-4">
            {hint && <p className="rounded-xl bg-[#F5F3EE] px-3 py-2 text-xs text-neutral-500">{hint}</p>}
            <div className="flex flex-wrap gap-2">
              {CATEGORIES.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setCategory(c)}
                  className={`rounded-full px-3.5 py-1.5 text-xs transition ${
                    category === c ? "bg-neutral-900 text-white" : "border border-black/10 text-neutral-500 hover:border-black/30"
                  }`}
                >
                  {CATEGORY_LABELS[c]}
                </button>
              ))}
            </div>
            <div>
              <textarea
                value={message}
                onChange={(e) => setMessage(e.target.value.slice(0, MAX_LENGTH))}
                rows={6}
                placeholder={
                  category === "transcript"
                    ? "Gõ lại nội dung bài nghe (ví dụ: 男：... 女：...)"
                    : "Bạn thấy gì chưa ổn? Ví dụ: đáp án câu này sai, nút nào đó không bấm được..."
                }
                className="w-full resize-none rounded-xl border border-black/10 bg-white px-4 py-3 text-sm outline-none focus:border-black/30"
              />
              <p className="mt-1 text-right text-[11px] text-neutral-400">
                {message.length}/{MAX_LENGTH}
              </p>
            </div>
            {error && <p className="text-xs text-red-600">{error}</p>}
            <button
              type="submit"
              disabled={sending}
              className="rounded-full bg-neutral-900 py-2.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {sending ? "Đang gửi..." : "Gửi góp ý"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
