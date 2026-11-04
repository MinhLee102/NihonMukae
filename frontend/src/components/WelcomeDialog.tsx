"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

const SEEN_KEY = "nm_welcome_v1";

/** First-visit welcome that points new users to the guide. Remembered per browser; storage may be unavailable. */
export function WelcomeDialog() {
  const router = useRouter();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    try {
      if (!window.localStorage.getItem(SEEN_KEY)) setOpen(true);
    } catch {
      // storage blocked: skip the welcome rather than showing it on every visit
    }
  }, []);

  const dismiss = (goToGuide: boolean) => {
    try {
      window.localStorage.setItem(SEEN_KEY, "1");
    } catch {
      // ignore
    }
    setOpen(false);
    if (goToGuide) router.push("/guide");
  };

  if (!open) return null;

  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={() => dismiss(false)}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Chào mừng"
        className="w-full max-w-md rounded-2xl bg-white p-7 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <p className="font-serif text-2xl font-semibold text-neutral-900">ようこそ</p>
        <p className="mt-1 text-sm text-neutral-500">Chào mừng bạn đến với trang học tiếng Nhật</p>
        <ul className="mt-5 space-y-2 text-sm leading-6 text-neutral-700">
          <li>Tra từ điển nhanh, không cần đăng nhập.</li>
          <li>Học từ bằng Flashcard, đọc bài có furigana ở My Shelf.</li>
          <li>Luyện đề JLPT theo dạng bài hoặc thi thử có bấm giờ, xem biểu đồ phần mình còn yếu.</li>
        </ul>
        <div className="mt-6 flex gap-2">
          <button
            type="button"
            onClick={() => dismiss(true)}
            className="flex-1 rounded-full bg-neutral-900 py-2.5 text-sm font-medium text-white"
          >
            Xem hướng dẫn
          </button>
          <button
            type="button"
            onClick={() => dismiss(false)}
            className="rounded-full border border-black/10 px-5 py-2.5 text-sm text-neutral-600"
          >
            Để sau
          </button>
        </div>
        <p className="mt-3 text-center text-[11px] text-neutral-400">Có thể mở lại bất cứ lúc nào ở mục Hướng dẫn.</p>
      </div>
    </div>
  );
}
