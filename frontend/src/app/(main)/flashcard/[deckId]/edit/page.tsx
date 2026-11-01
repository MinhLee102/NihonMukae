"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ChevronLeft, Pencil, Plus, Search, Trash2 } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { DeckFormModal } from "@/components/flashcard/DeckFormModal";
import { useAuthStore } from "@/store/authStore";
import {
  deleteCard,
  deleteDeck,
  getApiErrorMessage,
  getCards,
  getDecks,
  updateCard,
  type CardDto,
  type DeckDto,
} from "@/lib/api/flashcard";

/** Lowercase and strip Vietnamese diacritics so "nhat ban" matches "Nhật Bản". */
function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "d")
    .toLowerCase();
}

/** SRS label for the "next review" column. */
function formatNextReview(nextReviewAt?: string | null): { text: string; className: string } {
  if (!nextReviewAt) return { text: "Chưa học", className: "bg-neutral-100 text-neutral-500" };
  const diffMs = new Date(nextReviewAt).getTime() - Date.now();
  if (diffMs <= 0) return { text: "Hôm nay", className: "bg-amber-50 text-amber-600" };
  const days = Math.ceil(diffMs / 86_400_000);
  return { text: `Còn ${days} ngày`, className: "bg-emerald-50 text-emerald-600" };
}

function CardRow({
  card,
  onSaved,
  onDeleted,
}: {
  card: CardDto;
  onSaved: (card: CardDto) => void;
  onDeleted: (id: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [front, setFront] = useState(card.front);
  const [back, setBack] = useState(card.back);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    if (!front.trim() || !back.trim()) {
      setError("Mặt trước và mặt sau không được để trống");
      return;
    }
    try {
      setBusy(true);
      setError(null);
      const updated = await updateCard(card.id, { front, back, imageUrl: card.imageUrl });
      // The update response has no schedule info, so keep what the list already had.
      onSaved({ ...updated, nextReviewAt: card.nextReviewAt });
      setEditing(false);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!window.confirm("Xóa thẻ này?")) return;
    try {
      setBusy(true);
      await deleteCard(card.id);
      onDeleted(card.id);
    } catch (err) {
      setError(getApiErrorMessage(err));
      setBusy(false);
    }
  };

  if (editing) {
    return (
      <div className="rounded-2xl border border-black/10 bg-white p-4">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <textarea
            value={front}
            onChange={(e) => setFront(e.target.value)}
            rows={3}
            className="resize-none rounded-xl border border-black/10 p-3 font-serif text-lg outline-none focus:border-black/30"
          />
          <textarea
            value={back}
            onChange={(e) => setBack(e.target.value)}
            rows={3}
            className="resize-none rounded-xl border border-black/10 p-3 text-base outline-none focus:border-black/30"
          />
        </div>
        {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
        <div className="mt-3 flex justify-end gap-2">
          <button
            type="button"
            onClick={() => {
              setEditing(false);
              setFront(card.front);
              setBack(card.back);
              setError(null);
            }}
            className="rounded-full px-4 py-1.5 text-sm text-neutral-600"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={save}
            disabled={busy}
            className="rounded-full bg-neutral-900 px-5 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          >
            Lưu
          </button>
        </div>
      </div>
    );
  }

  const [frontMain, ...frontRest] = card.front.split("\n");
  const [backMain, ...backRest] = card.back.split("\n");
  const review = formatNextReview(card.nextReviewAt);

  return (
    <div className="flex items-center gap-4 rounded-2xl border border-black/5 bg-white px-5 py-4">
      <div className="min-w-0 flex-1">
        <p className="truncate font-serif text-lg text-neutral-900">{frontMain}</p>
        {frontRest.length > 0 && <p className="truncate text-xs text-neutral-400">{frontRest.join(" ")}</p>}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm text-neutral-800">{backMain}</p>
        {backRest.length > 0 && <p className="truncate text-xs text-neutral-400">{backRest.join(" ")}</p>}
      </div>
      <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-medium ${review.className}`}>{review.text}</span>
      <div className="flex shrink-0 gap-1">
        <button
          type="button"
          onClick={() => setEditing(true)}
          aria-label="Sửa thẻ"
          className="rounded-full p-2 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700"
        >
          <Pencil size={16} />
        </button>
        <button
          type="button"
          onClick={remove}
          disabled={busy}
          aria-label="Xóa thẻ"
          className="rounded-full p-2 text-neutral-400 hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
        >
          <Trash2 size={16} />
        </button>
      </div>
    </div>
  );
}

function DeckEditor() {
  const { deckId } = useParams<{ deckId: string }>();
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);

  const [deck, setDeck] = useState<DeckDto | null>(null);
  const [cards, setCards] = useState<CardDto[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingDeck, setEditingDeck] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    (async () => {
      try {
        const [decks, list] = await Promise.all([getDecks(), getCards(deckId)]);
        setDeck(decks.find((d) => d.id === deckId) ?? null);
        setCards(list);
      } catch (err) {
        setError(getApiErrorMessage(err, "Không tải được bộ flashcard."));
      } finally {
        setLoading(false);
      }
    })();
  }, [accessToken, deckId]);

  const filtered = useMemo(() => {
    const q = normalize(query.trim());
    if (!q) return cards;
    return cards.filter((c) => normalize(c.front).includes(q) || normalize(c.back).includes(q));
  }, [cards, query]);

  const removeDeck = async () => {
    if (!window.confirm("Xóa cả bộ và toàn bộ thẻ trong đó?")) return;
    try {
      await deleteDeck(deckId);
      router.push("/flashcard");
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <div className="mb-6 flex flex-wrap items-center gap-3 text-sm">
        <button
          type="button"
          onClick={() => router.push(`/flashcard/${deckId}`)}
          className="flex items-center gap-1 text-neutral-500 hover:text-neutral-800"
        >
          <ChevronLeft size={14} />
          Quay lại
        </button>
        <span className="text-base font-medium text-neutral-900">{deck?.name ?? ""}</span>
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={() => setEditingDeck(true)}
            disabled={!deck}
            className="flex items-center gap-1 rounded-full border border-black/10 px-4 py-1.5 text-neutral-700 hover:bg-neutral-50 disabled:opacity-50"
          >
            <Pencil size={14} />
            Sửa bộ
          </button>
          <button
            type="button"
            onClick={removeDeck}
            className="flex items-center gap-1 rounded-full border border-red-200 px-4 py-1.5 text-red-600 hover:bg-red-50"
          >
            <Trash2 size={14} />
            Xóa bộ
          </button>
          <Link
            href={`/flashcard/${deckId}/new`}
            className="flex items-center gap-1 rounded-full bg-neutral-900 px-4 py-1.5 font-medium text-white"
          >
            <Plus size={14} />
            Thêm thẻ
          </Link>
        </div>
      </div>

      <div className="relative mb-4">
        <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-neutral-400" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Tìm theo tiếng Nhật hoặc tiếng Việt..."
          className="w-full rounded-full border border-black/10 bg-white py-2.5 pl-11 pr-4 text-sm outline-none focus:border-black/30"
        />
      </div>

      {loading && <p className="py-10 text-center text-sm text-neutral-400">Đang tải...</p>}
      {error && <p className="py-4 text-center text-sm text-red-600">{error}</p>}
      {!loading && cards.length === 0 && (
        <p className="py-10 text-center text-sm text-neutral-400">Bộ này chưa có thẻ nào.</p>
      )}
      {!loading && cards.length > 0 && filtered.length === 0 && (
        <p className="py-10 text-center text-sm text-neutral-400">Không có thẻ nào khớp.</p>
      )}

      {cards.length > 0 && (
        <p className="mb-2 px-1 text-xs text-neutral-400">
          {filtered.length === cards.length ? `${cards.length} thẻ` : `${filtered.length} / ${cards.length} thẻ`}
        </p>
      )}

      <div className="flex flex-col gap-2">
        {filtered.map((card) => (
          <CardRow
            key={card.id}
            card={card}
            onSaved={(updated) => setCards((prev) => prev.map((c) => (c.id === updated.id ? updated : c)))}
            onDeleted={(id) => setCards((prev) => prev.filter((c) => c.id !== id))}
          />
        ))}
      </div>

      {editingDeck && deck && (
        <DeckFormModal
          deck={deck}
          onClose={() => setEditingDeck(false)}
          onSaved={(updated) => {
            setDeck(updated);
            setEditingDeck(false);
          }}
        />
      )}
    </div>
  );
}

export default function EditDeckPage() {
  return (
    <RequireAuth>
      <DeckEditor />
    </RequireAuth>
  );
}
