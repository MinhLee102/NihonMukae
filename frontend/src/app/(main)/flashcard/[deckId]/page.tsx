"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ChevronLeft, Plus } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { useAuthStore } from "@/store/authStore";
import {
  getDecks,
  getStudyCards,
  getApiErrorMessage,
  reviewCard,
  type CardDto,
  type ReviewRating,
} from "@/lib/api/flashcard";

const RATINGS: { rating: ReviewRating; label: string; className: string }[] = [
  { rating: "hard", label: "Khó", className: "border-red-200 bg-red-50 text-red-600 hover:bg-red-100" },
  { rating: "normal", label: "Bình thường", className: "border-yellow-200 bg-yellow-50 text-amber-600 hover:bg-yellow-100" },
  { rating: "easy", label: "Dễ", className: "border-emerald-200 bg-emerald-50 text-emerald-600 hover:bg-emerald-100" },
];

/** First line is the main text, remaining lines (reading, example) are shown smaller. */
function CardText({ text, light }: { text: string; light?: boolean }) {
  const [main, ...rest] = text.split("\n");
  return (
    <>
      <p className={`font-serif text-6xl ${light ? "text-white" : "text-neutral-900"}`}>{main}</p>
      {rest.length > 0 && (
        <p className={`mt-4 text-xl ${light ? "text-neutral-400" : "text-neutral-500"}`}>{rest.join(" ")}</p>
      )}
    </>
  );
}

function StudySession() {
  const { deckId } = useParams<{ deckId: string }>();
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);

  const [deckName, setDeckName] = useState("");
  const [cards, setCards] = useState<CardDto[]>([]);
  const [index, setIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    (async () => {
      try {
        const [decks, due] = await Promise.all([getDecks(), getStudyCards(deckId)]);
        if (cancelled) return;
        setDeckName(decks.find((d) => d.id === deckId)?.name ?? "");
        setCards(due);
      } catch (err) {
        if (!cancelled) setError(getApiErrorMessage(err, "Không tải được bộ flashcard."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [accessToken, deckId]);

  const submit = async (rating: ReviewRating) => {
    if (submitting) return;
    try {
      setSubmitting(true);
      await reviewCard(cards[index].id, rating);
      setFlipped(false);
      setIndex((i) => i + 1);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const current = cards[index];
  const finished = !loading && !error && index >= cards.length;

  return (
    <div className="mx-auto flex max-w-[720px] flex-col items-stretch pt-16">
      <div className="mb-6 flex items-center gap-3 text-sm">
        <button
          type="button"
          onClick={() => router.push("/flashcard")}
          className="flex items-center gap-1 text-neutral-500 hover:text-neutral-800"
        >
          <ChevronLeft size={14} />
          Quay lại
        </button>
        <span className="font-medium text-neutral-900">{deckName}</span>
        <Link
          href={`/flashcard/${deckId}/new`}
          className="ml-auto flex items-center gap-1 text-neutral-500 hover:text-neutral-800"
        >
          <Plus size={14} />
          Thêm thẻ
        </Link>
      </div>

      {loading && <p className="py-20 text-center text-sm text-neutral-400">Đang tải...</p>}
      {error && <p className="py-20 text-center text-sm text-red-600">{error}</p>}

      {!loading && !error && cards.length === 0 && (
        <p className="py-20 text-center text-sm text-neutral-500">
          Không có thẻ nào cần ôn lúc này. Hãy thêm thẻ mới hoặc quay lại sau.
        </p>
      )}

      {finished && cards.length > 0 && (
        <div className="py-20 text-center">
          <p className="text-lg font-medium text-neutral-900">Hoàn thành!</p>
          <p className="mt-1 text-sm text-neutral-500">Bạn đã ôn xong {cards.length} thẻ.</p>
        </div>
      )}

      {current && !error && (
        <>
          <div className="[perspective:1000px]">
            <button
              type="button"
              onClick={() => setFlipped((f) => !f)}
              className={`relative h-96 w-full transition-transform duration-500 [transform-style:preserve-3d] ${
                flipped ? "[transform:rotateY(180deg)]" : ""
              }`}
            >
              <div className="absolute inset-0 flex flex-col items-center justify-center rounded-3xl bg-white px-8 text-center shadow-md [backface-visibility:hidden]">
                <span className="mb-6 text-xs tracking-widest text-neutral-400">MẶT TRƯỚC</span>
                <CardText text={current.front} />
                <span className="mt-10 text-sm text-neutral-300">Nhấn để lật thẻ</span>
              </div>
              <div className="absolute inset-0 flex flex-col items-center justify-center rounded-3xl bg-[#1A1A1A] px-8 text-center shadow-md [backface-visibility:hidden] [transform:rotateY(180deg)]">
                <span className="mb-6 text-xs tracking-widest text-neutral-500">MẶT SAU</span>
                <CardText text={current.back} light />
              </div>
            </button>
          </div>

          <div className={`mt-6 grid grid-cols-3 gap-3 ${flipped ? "" : "invisible"}`}>
            {RATINGS.map((r) => (
              <button
                key={r.rating}
                type="button"
                disabled={submitting}
                onClick={() => submit(r.rating)}
                className={`rounded-full border py-3.5 text-base font-medium transition disabled:opacity-50 ${r.className}`}
              >
                {r.label}
              </button>
            ))}
          </div>

          <p className="mt-6 text-center text-xs text-neutral-500">
            {index + 1} / {cards.length}
          </p>
          <div className="mt-2 flex justify-center gap-1.5">
            {cards.map((c, i) => (
              <span
                key={c.id}
                className={`h-1.5 rounded-full ${i === index ? "w-5 bg-neutral-900" : "w-1.5 bg-neutral-300"}`}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

export default function StudyPage() {
  return (
    <RequireAuth>
      <StudySession />
    </RequireAuth>
  );
}
