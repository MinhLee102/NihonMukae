"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ChevronLeft, Pencil, Play, Plus } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage, getDecks, type DeckDto } from "@/lib/api/flashcard";

function DeckOverview() {
  const { deckId } = useParams<{ deckId: string }>();
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);
  const [deck, setDeck] = useState<DeckDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    (async () => {
      try {
        const decks = await getDecks();
        const found = decks.find((d) => d.id === deckId);
        if (found) setDeck(found);
        else setError("Không tìm thấy bộ flashcard.");
      } catch (err) {
        setError(getApiErrorMessage(err, "Không tải được bộ flashcard."));
      } finally {
        setLoading(false);
      }
    })();
  }, [accessToken, deckId]);

  return (
    <div className="mx-auto max-w-[625px] pt-8">
      <button
        type="button"
        onClick={() => router.push("/flashcard")}
        className="mb-6 flex items-center gap-1 text-sm text-neutral-500 hover:text-neutral-800"
      >
        <ChevronLeft size={14} />
        Quay lại
      </button>

      {loading && <p className="py-20 text-center text-sm text-neutral-400">Đang tải...</p>}
      {error && <p className="py-20 text-center text-sm text-red-600">{error}</p>}

      {deck && (
        <div className="rounded-3xl border border-black/5 bg-white p-8 shadow-sm">
          <h1 className="text-2xl font-semibold text-neutral-900">{deck.name}</h1>
          {deck.description && <p className="mt-2 text-sm text-neutral-500">{deck.description}</p>}

          <div className="mt-6 grid grid-cols-3 gap-3 text-center">
            <div className="rounded-2xl bg-[#F5F3EE] py-4">
              <p className="text-2xl font-semibold text-neutral-900">{deck.cardCount}</p>
              <p className="text-xs text-neutral-500">Tổng số thẻ</p>
            </div>
            <div className="rounded-2xl bg-[#F5F3EE] py-4">
              <p className="text-2xl font-semibold text-neutral-900">{deck.learnedCount}</p>
              <p className="text-xs text-neutral-500">Đã học</p>
            </div>
            <div className="rounded-2xl bg-[#F5F3EE] py-4">
              <p className="text-2xl font-semibold text-neutral-900">{deck.dueCount}</p>
              <p className="text-xs text-neutral-500">Cần ôn</p>
            </div>
          </div>

          <div className="mt-6 grid grid-cols-2 gap-3">
            <Link
              href={`/flashcard/${deckId}/study`}
              className="flex items-center justify-center gap-2 rounded-full bg-neutral-900 py-3 text-sm font-medium text-white"
            >
              <Play size={16} />
              Học
            </Link>
            <Link
              href={`/flashcard/${deckId}/edit`}
              className="flex items-center justify-center gap-2 rounded-full border border-black/10 py-3 text-sm font-medium text-neutral-800 hover:bg-neutral-50"
            >
              <Pencil size={16} />
              Chỉnh sửa
            </Link>
          </div>

          <Link
            href={`/flashcard/${deckId}/new`}
            className="mt-4 flex items-center justify-center gap-1 text-sm text-neutral-500 hover:text-neutral-800"
          >
            <Plus size={14} />
            Thêm thẻ mới
          </Link>
        </div>
      )}
    </div>
  );
}

export default function DeckPage() {
  return (
    <RequireAuth>
      <DeckOverview />
    </RequireAuth>
  );
}
