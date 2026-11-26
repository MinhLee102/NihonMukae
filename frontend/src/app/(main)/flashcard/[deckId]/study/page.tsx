"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ChevronLeft, Plus, RotateCcw } from "lucide-react";
import { cardFontClass } from "@/lib/japanese";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { useAuthStore } from "@/store/authStore";
import {
  getDecks,
  getStudyCards,
  getApiErrorMessage,
  reviewCard,
  type CardDto,
  type DeckDto,
  type ReviewRating,
  type StudyMode,
} from "@/lib/api/flashcard";

const RATINGS: { rating: ReviewRating; label: string; className: string }[] = [
  { rating: "hard", label: "Khó", className: "border-red-200 bg-red-50 text-red-600 hover:bg-red-100" },
  { rating: "normal", label: "Bình thường", className: "border-yellow-200 bg-yellow-50 text-amber-600 hover:bg-yellow-100" },
  { rating: "easy", label: "Dễ", className: "border-emerald-200 bg-emerald-50 text-emerald-600 hover:bg-emerald-100" },
];

const SIZE_PRESETS = [15, 30];
const SETTINGS_KEY = "flashcard:study-settings";
// With more cards than this the dots would not fit on one line, so a bar is shown instead
const MAX_DOTS = 30;

type Phase = "setup" | "study" | "done";
interface Settings {
  mode: StudyMode;
  /** null: every card */
  size: number | null;
}

function readSettings(): Settings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (raw) {
      const s = JSON.parse(raw) as Settings;
      if ((s.mode === "due" || s.mode === "all") && (s.size === null || (Number.isInteger(s.size) && s.size > 0))) {
        return s;
      }
    }
  } catch {
    // fall back to the defaults
  }
  return { mode: "due", size: 30 };
}

function saveSettings(s: Settings) {
  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(s));
  } catch {
    // only a convenience
  }
}

function shuffle<T>(list: T[]): T[] {
  const a = [...list];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/** Font size by text length, so long text still fits the card (it can also scroll). */
function frontSize(text: string) {
  const n = text.length;
  if (n <= 4) return "text-5xl sm:text-6xl";
  if (n <= 8) return "text-4xl sm:text-5xl";
  if (n <= 16) return "text-3xl sm:text-4xl";
  return "text-2xl sm:text-3xl";
}

function backSize(text: string) {
  const n = text.length;
  if (n <= 16) return "text-3xl sm:text-4xl";
  if (n <= 40) return "text-2xl sm:text-3xl";
  if (n <= 90) return "text-xl sm:text-2xl";
  return "text-lg sm:text-xl";
}

function FrontFace({ card }: { card: CardDto }) {
  const [main, ...rest] = card.front.split("\n");
  return (
    <>
      <p className={`${cardFontClass(main)} ${frontSize(main)} break-words text-neutral-900`}>{main}</p>
      {rest.length > 0 && <p className="mt-4 text-lg text-neutral-500 sm:text-xl">{rest.join(" ")}</p>}
    </>
  );
}

/**
 * Back: the meaning saved on the card, then the Hán Việt reading and an example with its translation taken
 * from the dictionary (cards made from the dictionary). Other cards show their own extra lines.
 */
function BackFace({ card }: { card: CardDto }) {
  const [meaning, ...rest] = card.back.split("\n");
  const fromDictionary = !!card.exampleJa;
  return (
    <>
      <p className={`${cardFontClass(meaning)} ${backSize(meaning)} break-words text-white`}>{meaning}</p>
      {card.hanViet && (
        <p className="mt-3 text-sm font-semibold uppercase tracking-wider text-sky-400">{card.hanViet}</p>
      )}
      {fromDictionary ? (
        <div className="mt-6 w-full max-w-lg rounded-2xl bg-white/5 px-4 py-3 text-left">
          <p className="font-serif text-base leading-7 text-neutral-100">{card.exampleJa}</p>
          <p className="mt-1 text-sm leading-6 text-neutral-400">{card.exampleVi}</p>
        </div>
      ) : (
        rest.length > 0 && <p className="mt-5 text-base leading-7 text-neutral-400 sm:text-lg">{rest.join(" ")}</p>
      )}
    </>
  );
}

function StudySession() {
  const { deckId } = useParams<{ deckId: string }>();
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);

  const [deck, setDeck] = useState<DeckDto | null>(null);
  const [phase, setPhase] = useState<Phase>("setup");
  const [settings, setSettings] = useState<Settings>({ mode: "due", size: 30 });
  const [custom, setCustom] = useState("");
  const [cards, setCards] = useState<CardDto[]>([]);
  const [index, setIndex] = useState(0);
  // Changes for every card shown, so the card element is replaced instead of turning back over:
  // turning back would show the next card's back for a moment
  const [round, setRound] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDeck = useCallback(async () => {
    const decks = await getDecks();
    const found = decks.find((d) => d.id === deckId) ?? null;
    setDeck(found);
    return found;
  }, [deckId]);

  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    (async () => {
      try {
        const found = await loadDeck();
        if (cancelled) return;
        const saved = readSettings();
        // Nothing due: practising the whole deck is the only thing to do
        setSettings(found && found.dueCount === 0 ? { ...saved, mode: "all" } : saved);
      } catch (err) {
        if (!cancelled) setError(getApiErrorMessage(err, "Không tải được bộ flashcard."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [accessToken, loadDeck]);

  const available = deck ? (settings.mode === "due" ? deck.dueCount : deck.cardCount) : 0;

  const begin = (list: CardDto[]) => {
    setCards(list);
    setIndex(0);
    setFlipped(false);
    setRound((r) => r + 1);
    setPhase(list.length > 0 ? "study" : "done");
  };

  const start = async () => {
    const customSize = parseInt(custom, 10);
    const size = custom.trim() && customSize > 0 ? customSize : settings.size;
    const chosen = { ...settings, size };
    saveSettings(chosen);
    setSettings(chosen);
    try {
      setStarting(true);
      setError(null);
      begin(await getStudyCards(deckId, { mode: chosen.mode, limit: size ?? undefined }));
    } catch (err) {
      setError(getApiErrorMessage(err, "Không tải được thẻ."));
    } finally {
      setStarting(false);
    }
  };

  const submit = async (rating: ReviewRating) => {
    if (submitting) return;
    try {
      setSubmitting(true);
      await reviewCard(cards[index].id, rating);
      setFlipped(false);
      setRound((r) => r + 1);
      if (index + 1 >= cards.length) {
        setPhase("done");
      } else {
        setIndex((i) => i + 1);
      }
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const backToSetup = async () => {
    setPhase("setup");
    setCustom("");
    try {
      await loadDeck();
    } catch {
      // the counts are only informative
    }
  };

  const current = phase === "study" ? cards[index] : undefined;

  return (
    <div className="mx-auto flex max-w-[720px] flex-col items-stretch pt-4 sm:pt-12">
      <div className="mb-6 flex items-center gap-3 text-sm">
        <button
          type="button"
          onClick={() => router.push(`/flashcard/${deckId}`)}
          className="flex items-center gap-1 text-neutral-500 hover:text-neutral-800"
        >
          <ChevronLeft size={14} />
          Quay lại
        </button>
        <span className="truncate font-medium text-neutral-900">{deck?.name ?? ""}</span>
        <Link
          href={`/flashcard/${deckId}/new`}
          className="ml-auto flex shrink-0 items-center gap-1 text-neutral-500 hover:text-neutral-800"
        >
          <Plus size={14} />
          Thêm thẻ
        </Link>
      </div>

      {loading && <p className="py-20 text-center text-sm text-neutral-400">Đang tải...</p>}
      {error && <p className="py-6 text-center text-sm text-red-600">{error}</p>}

      {!loading && deck && phase === "setup" && (
        <div className="rounded-3xl bg-white p-6 shadow-sm sm:p-8">
          <h1 className="text-lg font-semibold text-neutral-900">Chọn cách ôn</h1>

          <p className="mt-5 text-xs font-medium uppercase tracking-widest text-neutral-400">Thẻ để ôn</p>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {(
              [
                ["due", "Thẻ đến hạn", `${deck.dueCount} thẻ cần ôn theo lịch nhắc`],
                ["all", "Cả bộ thẻ", `${deck.cardCount} thẻ, ôn thêm không đổi lịch nhắc (trừ khi bấm Khó)`],
              ] as const
            ).map(([mode, label, hint]) => (
              <button
                key={mode}
                type="button"
                onClick={() => setSettings((s) => ({ ...s, mode }))}
                className={`rounded-2xl border px-4 py-3 text-left transition ${
                  settings.mode === mode ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/10 hover:border-black/30"
                }`}
              >
                <p className="text-sm font-medium">{label}</p>
                <p className={`mt-0.5 text-xs ${settings.mode === mode ? "text-neutral-300" : "text-neutral-400"}`}>{hint}</p>
              </button>
            ))}
          </div>

          <p className="mt-6 text-xs font-medium uppercase tracking-widest text-neutral-400">Số thẻ mỗi lượt</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {[...SIZE_PRESETS, null].map((size) => (
              <button
                key={size ?? "all"}
                type="button"
                onClick={() => {
                  setCustom("");
                  setSettings((s) => ({ ...s, size }));
                }}
                className={`rounded-full border px-4 py-2 text-sm transition ${
                  !custom.trim() && settings.size === size
                    ? "border-neutral-900 bg-neutral-900 text-white"
                    : "border-black/10 text-neutral-600 hover:border-black/30"
                }`}
              >
                {size === null ? "Tất cả" : `${size} thẻ`}
              </button>
            ))}
            <input
              value={custom}
              onChange={(e) => setCustom(e.target.value.replace(/\D/g, "").slice(0, 4))}
              inputMode="numeric"
              placeholder="Số khác"
              aria-label="Số thẻ tự chọn"
              className={`w-24 rounded-full border px-4 py-2 text-sm outline-none ${
                custom.trim() ? "border-neutral-900" : "border-black/10"
              }`}
            />
          </div>
          <p className="mt-3 text-xs leading-5 text-neutral-400">
            Thẻ được chọn ngẫu nhiên, ưu tiên những thẻ bạn hay bấm Khó hoặc Bình thường.
          </p>

          <button
            type="button"
            onClick={() => void start()}
            disabled={starting || available === 0}
            className="mt-6 w-full rounded-full bg-neutral-900 py-3.5 text-sm font-medium text-white disabled:opacity-40"
          >
            {starting ? "Đang chuẩn bị..." : available === 0 ? "Không có thẻ nào" : "Bắt đầu ôn"}
          </button>
        </div>
      )}

      {phase === "done" && (
        <div className="rounded-3xl bg-white p-8 text-center shadow-sm">
          <p className="text-lg font-medium text-neutral-900">{cards.length > 0 ? "Hoàn thành!" : "Không có thẻ nào"}</p>
          {cards.length > 0 && <p className="mt-1 text-sm text-neutral-500">Bạn đã ôn xong {cards.length} thẻ.</p>}
          <div className="mt-6 flex flex-col justify-center gap-2 sm:flex-row">
            {cards.length > 0 && (
              <button
                type="button"
                onClick={() => begin(shuffle(cards))}
                className="flex items-center justify-center gap-2 rounded-full bg-neutral-900 px-5 py-2.5 text-sm font-medium text-white"
              >
                <RotateCcw size={14} />
                Ôn lại lượt này
              </button>
            )}
            <button
              type="button"
              onClick={() => void backToSetup()}
              className="rounded-full border border-black/10 px-5 py-2.5 text-sm text-neutral-700 hover:border-black/30"
            >
              Ôn lượt khác
            </button>
            <Link
              href={`/flashcard/${deckId}`}
              className="rounded-full px-5 py-2.5 text-sm text-neutral-500 hover:text-neutral-800"
            >
              Về bộ thẻ
            </Link>
          </div>
        </div>
      )}

      {current && (
        <>
          <div className="[perspective:1000px]">
            <div
              key={round}
              role="button"
              tabIndex={0}
              aria-label={flipped ? "Lật về mặt trước" : "Lật thẻ"}
              onClick={() => setFlipped((f) => !f)}
              onKeyDown={(e) => {
                if (e.key === " " || e.key === "Enter") {
                  e.preventDefault();
                  setFlipped((f) => !f);
                }
              }}
              className={`relative h-[22rem] w-full cursor-pointer transition-transform duration-500 [transform-style:preserve-3d] sm:h-96 ${
                flipped ? "[transform:rotateY(180deg)]" : ""
              }`}
            >
              <div className="absolute inset-0 overflow-y-auto rounded-3xl bg-white shadow-md [backface-visibility:hidden]">
                <div className="flex min-h-full flex-col items-center justify-center px-6 py-8 text-center">
                  <span className="mb-6 text-xs tracking-widest text-neutral-400">MẶT TRƯỚC</span>
                  <FrontFace card={current} />
                  <span className="mt-10 text-sm text-neutral-300">Nhấn để lật thẻ</span>
                </div>
              </div>
              <div className="absolute inset-0 overflow-y-auto rounded-3xl bg-[#1A1A1A] shadow-md [backface-visibility:hidden] [transform:rotateY(180deg)]">
                <div className="flex min-h-full flex-col items-center justify-center px-6 py-8 text-center">
                  <span className="mb-5 text-xs tracking-widest text-neutral-500">MẶT SAU</span>
                  {/* Rendered only once turned over, so the back can never be read before the front */}
                  {flipped && <BackFace card={current} />}
                </div>
              </div>
            </div>
          </div>

          <div className={`mt-6 grid grid-cols-3 gap-3 ${flipped ? "" : "invisible"}`}>
            {RATINGS.map((r) => (
              <button
                key={r.rating}
                type="button"
                disabled={submitting}
                onClick={() => submit(r.rating)}
                className={`rounded-full border py-3.5 text-sm font-medium transition disabled:opacity-50 sm:text-base ${r.className}`}
              >
                {r.label}
              </button>
            ))}
          </div>

          <p className="mt-6 text-center text-xs text-neutral-500">
            {index + 1} / {cards.length}
          </p>
          {cards.length <= MAX_DOTS ? (
            <div className="mt-2 flex flex-wrap justify-center gap-1.5">
              {cards.map((c, i) => (
                <span
                  key={c.id}
                  className={`h-1.5 rounded-full ${i === index ? "w-5 bg-neutral-900" : "w-1.5 bg-neutral-300"}`}
                />
              ))}
            </div>
          ) : (
            <div className="mx-auto mt-2 h-1.5 w-full max-w-xs overflow-hidden rounded-full bg-neutral-200">
              <div className="h-full bg-neutral-900" style={{ width: `${((index + 1) / cards.length) * 100}%` }} />
            </div>
          )}
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
