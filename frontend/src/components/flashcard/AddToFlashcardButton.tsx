"use client";

import { useState } from "react";
import Link from "next/link";
import { Plus, PlusCircle } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useAuthModalStore } from "@/store/authModalStore";
import { createCard, getApiErrorMessage, getDecks, type DeckDto } from "@/lib/api/flashcard";

/**
 * Button on a dictionary word: pick a deck and add the word as a card.
 * "icon" is the round ⊕ used in the dictionary, "pill" is the labelled button
 * used inside the My Shelf word popup (its menu opens upward).
 */
export function AddToFlashcardButton({
  wordId,
  variant = "icon",
}: {
  wordId: number;
  variant?: "icon" | "pill";
}) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const openAuthModal = useAuthModalStore((s) => s.open);

  const [open, setOpen] = useState(false);
  const [decks, setDecks] = useState<DeckDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const toggle = async () => {
    if (!isAuthenticated) {
      openAuthModal("login");
      return;
    }
    setMessage(null);
    if (open) {
      setOpen(false);
      return;
    }
    setOpen(true);
    try {
      setLoading(true);
      setDecks(await getDecks());
    } catch (err) {
      setMessage(getApiErrorMessage(err, "Không tải được danh sách bộ flashcard."));
    } finally {
      setLoading(false);
    }
  };

  const add = async (deck: DeckDto) => {
    try {
      await createCard(deck.id, { wordId });
      setMessage(`Đã thêm vào "${deck.name}"`);
    } catch (err) {
      setMessage(getApiErrorMessage(err));
    }
  };

  return (
    <div className="relative">
      {variant === "pill" ? (
        <button
          type="button"
          onClick={toggle}
          className="flex items-center gap-1.5 rounded-full bg-neutral-100 px-3 py-1.5 text-xs font-medium text-neutral-600 transition hover:bg-neutral-200"
        >
          <Plus size={13} />
          Flashcard
        </button>
      ) : (
        <button
          type="button"
          onClick={toggle}
          className="rounded-full p-2 text-neutral-400 transition hover:bg-neutral-100 hover:text-neutral-700"
          title="Thêm vào flashcard"
        >
          <PlusCircle size={22} strokeWidth={1.75} />
        </button>
      )}

      {open && (
        <div className={`absolute z-20 w-64 ${variant === "pill" ? "bottom-full left-0 mb-1" : "right-0 mt-1"} rounded-xl border border-black/10 bg-white p-2 shadow-lg`}>
          {loading && <p className="p-2 text-xs text-neutral-400">Đang tải...</p>}
          {!loading && decks.length === 0 && (
            <p className="p-2 text-xs text-neutral-500">
              Bạn chưa có bộ nào.{" "}
              <Link href="/flashcard" className="font-medium text-sky-600">
                Tạo bộ mới
              </Link>
            </p>
          )}
          {decks.map((deck) => (
            <button
              key={deck.id}
              type="button"
              onClick={() => add(deck)}
              className="block w-full truncate rounded-lg px-3 py-2 text-left text-sm text-neutral-800 hover:bg-neutral-50"
            >
              {deck.name}
            </button>
          ))}
          {message && <p className="border-t border-black/5 p-2 text-xs text-neutral-600">{message}</p>}
        </div>
      )}
    </div>
  );
}
