"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { createCard, getApiErrorMessage } from "@/lib/api/flashcard";

function NewCardForm() {
  const { deckId } = useParams<{ deckId: string }>();
  const router = useRouter();
  const [front, setFront] = useState("");
  const [back, setBack] = useState("");
  const [saving, setSaving] = useState(false);
  const [savedCount, setSavedCount] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    if (!front.trim() || !back.trim()) {
      setError("Cần nhập cả mặt trước và mặt sau");
      return;
    }
    try {
      setSaving(true);
      setError(null);
      await createCard(deckId, { front, back });
      setSavedCount((n) => n + 1);
      setFront("");
      setBack("");
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="-mx-8 -my-8 flex h-full flex-col">
      <div className="flex items-center gap-3 border-b border-black/5 px-6 py-4 text-sm">
        <button
          type="button"
          onClick={() => router.push(`/flashcard/${deckId}`)}
          className="flex items-center gap-1 text-neutral-500 hover:text-neutral-800"
        >
          <ChevronLeft size={14} />
          Quay lại
        </button>
        <span className="font-medium text-neutral-900">Tạo flashcard mới</span>
        {savedCount > 0 && <span className="text-xs text-emerald-600">Đã lưu {savedCount} thẻ</span>}
        <button
          type="button"
          onClick={save}
          disabled={saving}
          className="ml-auto rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {saving ? "Đang lưu..." : "Lưu thẻ"}
        </button>
      </div>

      {error && <p className="px-6 pt-3 text-sm text-red-600">{error}</p>}

      <div className="grid flex-1 grid-cols-1 md:grid-cols-2 md:divide-x md:divide-black/5">
        <div className="flex flex-col items-center justify-center gap-3 p-8">
          <span className="text-[10px] tracking-widest text-neutral-400">MẶT TRƯỚC</span>
          <textarea
            value={front}
            onChange={(e) => setFront(e.target.value)}
            placeholder="日本語..."
            className="h-52 w-80 resize-none rounded-2xl bg-white p-5 font-serif text-2xl text-neutral-900 shadow-md outline-none placeholder:text-neutral-300"
          />
          <span className="text-xs text-neutral-500">Từ vựng, kanji hoặc mẫu câu</span>
        </div>
        <div className="flex flex-col items-center justify-center gap-3 p-8">
          <span className="text-[10px] tracking-widest text-neutral-400">MẶT SAU</span>
          <textarea
            value={back}
            onChange={(e) => setBack(e.target.value)}
            placeholder="Nghĩa, phiên âm, ví dụ..."
            className="h-52 w-80 resize-none rounded-2xl bg-[#1A1A1A] p-5 text-xl text-white shadow-md outline-none placeholder:text-neutral-500"
          />
          <span className="text-xs text-neutral-500">Nghĩa, phiên âm hoặc ví dụ</span>
        </div>
      </div>
    </div>
  );
}

export default function NewCardPage() {
  return (
    <RequireAuth>
      <NewCardForm />
    </RequireAuth>
  );
}
