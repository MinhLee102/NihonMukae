"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronRight, Layers, Plus } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { DeckFormModal } from "@/components/flashcard/DeckFormModal";
import { useAuthStore } from "@/store/authStore";
import { getDecks, type DeckDto } from "@/lib/api/flashcard";

function DeckList() {
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);
  const [decks, setDecks] = useState<DeckDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setDecks(await getDecks());
    } catch {
      setError("Không tải được danh sách bộ flashcard.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (accessToken) load();
  }, [accessToken, load]);

  return (
    <div className="mx-auto max-w-[625px]">
      <div className="mb-8 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-neutral-900">Bộ flashcard</h1>
          <p className="mt-1 text-sm text-neutral-400">Chọn một bộ để ôn tập hoặc tạo mới</p>
        </div>
        <button
          type="button"
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 rounded-full bg-neutral-900 px-5 py-2.5 text-sm font-medium text-white"
        >
          <Plus size={16} />
          Tạo bộ mới
        </button>
      </div>

      {loading && <p className="py-10 text-center text-sm text-neutral-400">Đang tải...</p>}
      {error && <p className="py-10 text-center text-sm text-red-600">{error}</p>}
      {!loading && !error && decks.length === 0 && (
        <p className="py-10 text-center text-sm text-neutral-400">Bạn chưa có bộ flashcard nào.</p>
      )}

      <div className="flex flex-col gap-3">
        {decks.map((deck) => {
          const percent = deck.cardCount === 0 ? 0 : Math.round((deck.learnedCount / deck.cardCount) * 100);
          return (
            <button
              key={deck.id}
              type="button"
              onClick={() => router.push(`/flashcard/${deck.id}`)}
              className="flex items-center gap-4 rounded-2xl border border-black/5 bg-white px-4 py-5 text-left transition hover:border-black/15 hover:shadow-sm"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#EFE9DD] text-neutral-700">
                <Layers size={20} />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-neutral-900">{deck.name}</p>
                <div className="mt-2 flex items-center gap-3">
                  <div className="h-1 flex-1 rounded-full bg-[#E8E4DA]">
                    <div
                      className="h-1 rounded-full"
                      style={{ width: `${percent}%`, backgroundColor: deck.color ?? "#1A1A1A" }}
                    />
                  </div>
                  <span className="text-xs text-neutral-500">
                    {deck.learnedCount}/{deck.cardCount}
                  </span>
                </div>
              </div>
              <ChevronRight size={16} className="text-neutral-400" />
            </button>
          );
        })}
      </div>

      {showCreate && (
        <DeckFormModal
          onClose={() => setShowCreate(false)}
          onCreated={(deck) => {
            setShowCreate(false);
            setDecks((prev) => [deck, ...prev]);
          }}
        />
      )}
    </div>
  );
}

export default function FlashcardPage() {
  return (
    <RequireAuth>
      <DeckList />
    </RequireAuth>
  );
}
