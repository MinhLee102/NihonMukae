"use client";

import { useEffect } from "react";
import { ArrowRight, Library, SlidersHorizontal } from "lucide-react";

export type JlptMode = "exam" | "practice";

const OPTIONS: { mode: JlptMode; eyebrow: string; title: string; description: string; icon: typeof Library }[] = [
  {
    mode: "exam",
    eyebrow: "01 · 模擬試験",
    title: "Làm đề JLPT",
    description: "Chọn đề theo N1–N5, năm thi hoặc giáo trình.",
    icon: Library,
  },
  {
    mode: "practice",
    eyebrow: "02 · カスタム",
    title: "Luyện theo dạng bài",
    description: "Phối hợp đọc, nghe, từ vựng và ngữ pháp.",
    icon: SlidersHorizontal,
  },
];

/** The popup shown when opening JLPT from the sidebar: pick a mode before anything else. */
export function ModeDialog({ onSelect, onClose }: { onSelect: (mode: JlptMode) => void; onClose?: () => void }) {
  useEffect(() => {
    if (!onClose) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/25 px-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-[680px] overflow-hidden rounded-3xl bg-white shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="border-b border-black/5 px-6 py-6">
          <p className="text-[10px] font-medium tracking-[0.2em] text-neutral-400">JLPT PRACTICE</p>
          <h2 className="mt-3 text-xl font-semibold text-neutral-900">Hôm nay bạn muốn luyện gì?</h2>
          <p className="mt-1 text-xs text-neutral-400">Bạn có thể đổi chế độ bất cứ lúc nào.</p>
        </div>
        <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2">
          {OPTIONS.map(({ mode, eyebrow, title, description, icon: Icon }) => (
            <button
              key={mode}
              type="button"
              onClick={() => onSelect(mode)}
              className="group relative flex flex-col rounded-2xl border border-black/5 bg-[#F5F3EE] p-5 text-left transition hover:border-black/20"
            >
              <ArrowRight
                size={16}
                className="absolute right-5 top-5 text-neutral-400 transition group-hover:translate-x-0.5 group-hover:text-neutral-800"
              />
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white shadow-sm">
                <Icon size={18} />
              </span>
              <span className="mt-6 text-[10px] tracking-widest text-neutral-400">{eyebrow}</span>
              <span className="mt-1 text-base font-semibold text-neutral-900">{title}</span>
              <span className="mt-1 text-xs text-neutral-500">{description}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
