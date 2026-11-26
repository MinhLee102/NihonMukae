"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Check, ChevronLeft, ChevronRight, RotateCcw, X } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { useAuthStore } from "@/store/authStore";
import { cardFontClass } from "@/lib/japanese";
import { getApiErrorMessage, getDecks, type DeckDto } from "@/lib/api/flashcard";
import {
  QUIZ_KINDS,
  getQuiz,
  getQuizAttempts,
  saveQuizAttempt,
  type QuizAttemptDto,
  type QuizKind,
  type QuizQuestionDto,
} from "@/lib/api/quiz";

const SIZE_PRESETS = [10, 20, 30];
const SETTINGS_KEY = "flashcard:quiz-settings";
const KIND_TITLE: Record<QuizKind, string> = {
  meaning: "Chọn nghĩa đúng",
  reading: "Chọn cách đọc đúng",
  cloze: "Chọn từ điền vào chỗ trống",
};

type Phase = "setup" | "play" | "done";
interface Settings {
  kinds: QuizKind[];
  /** null: as many as the deck allows */
  size: number | null;
}

function readSettings(): Settings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (raw) {
      const s = JSON.parse(raw) as Settings;
      const kinds = (s.kinds ?? []).filter((k) => QUIZ_KINDS.some((q) => q.kind === k));
      if (kinds.length > 0 && (s.size === null || (Number.isInteger(s.size) && s.size > 0))) return { kinds, size: s.size };
    }
  } catch {
    // defaults below
  }
  return { kinds: ["meaning", "reading", "cloze"], size: 20 };
}

function formatDate(iso: string) {
  const d = new Date(iso);
  return `${d.getDate()}/${d.getMonth() + 1} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function promptSize(kind: QuizKind, text: string) {
  if (kind === "cloze") return text.length > 40 ? "text-lg sm:text-xl" : "text-xl sm:text-2xl";
  if (text.length <= 4) return "text-5xl sm:text-6xl";
  if (text.length <= 10) return "text-4xl sm:text-5xl";
  return "text-2xl sm:text-3xl";
}

function Quiz() {
  const { deckId } = useParams<{ deckId: string }>();
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);

  const [deck, setDeck] = useState<DeckDto | null>(null);
  const [history, setHistory] = useState<QuizAttemptDto[]>([]);
  const [phase, setPhase] = useState<Phase>("setup");
  const [settings, setSettings] = useState<Settings>({ kinds: ["meaning", "reading", "cloze"], size: 20 });
  const [custom, setCustom] = useState("");
  const [questions, setQuestions] = useState<QuizQuestionDto[]>([]);
  const [index, setIndex] = useState(0);
  const [picked, setPicked] = useState<(number | null)[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await getQuizAttempts(deckId, 10));
    } catch {
      // history is only informative
    }
  }, [deckId]);

  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    setSettings(readSettings());
    (async () => {
      try {
        const decks = await getDecks();
        if (!cancelled) setDeck(decks.find((d) => d.id === deckId) ?? null);
        await loadHistory();
      } catch (err) {
        if (!cancelled) setError(getApiErrorMessage(err, "Không tải được bộ thẻ."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [accessToken, deckId, loadHistory]);

  const toggleKind = (kind: QuizKind) =>
    setSettings((s) => {
      const has = s.kinds.includes(kind);
      if (has && s.kinds.length === 1) return s; // at least one kind
      return { ...s, kinds: has ? s.kinds.filter((k) => k !== kind) : [...s.kinds, kind] };
    });

  const start = async () => {
    const customSize = parseInt(custom, 10);
    const size = custom.trim() && customSize > 0 ? customSize : settings.size;
    const chosen = { ...settings, size };
    try {
      localStorage.setItem(SETTINGS_KEY, JSON.stringify(chosen));
    } catch {
      // only a convenience
    }
    try {
      setBusy(true);
      setError(null);
      const qs = await getQuiz(deckId, chosen.kinds, size ?? undefined);
      if (qs.length === 0) {
        setError(
          "Bộ thẻ chưa đủ dữ liệu để tạo câu hỏi dạng này. Thử thêm thẻ từ từ điển hoặc chọn dạng khác (cần ít nhất 4 lựa chọn khác nhau)."
        );
        return;
      }
      setQuestions(qs);
      setPicked(qs.map(() => null));
      setIndex(0);
      setPhase("play");
    } catch (err) {
      setError(getApiErrorMessage(err, "Không tạo được quiz."));
    } finally {
      setBusy(false);
    }
  };

  const choose = (option: number) => {
    if (picked[index] !== null) return;
    setPicked((p) => p.map((v, i) => (i === index ? option : v)));
  };

  const finish = async () => {
    setPhase("done");
    const correct = questions.filter((q, i) => picked[i] === q.answer).length;
    try {
      await saveQuizAttempt({ deckId, kinds: settings.kinds, total: questions.length, correct });
      await loadHistory();
    } catch {
      // the score is still shown even if saving failed
    }
  };

  const next = () => {
    if (index + 1 >= questions.length) void finish();
    else setIndex((i) => i + 1);
  };

  const q = phase === "play" ? questions[index] : undefined;
  const answered = q ? picked[index] !== null : false;
  const score = questions.filter((x, i) => picked[i] === x.answer).length;
  const mistakes = questions.filter((x, i) => picked[i] !== x.answer);

  return (
    <div className="mx-auto flex max-w-[720px] flex-col pt-4 sm:pt-12">
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
        <span className="ml-auto shrink-0 text-xs uppercase tracking-widest text-neutral-400">Quiz</span>
      </div>

      {loading && <p className="py-20 text-center text-sm text-neutral-400">Đang tải...</p>}
      {error && <p className="mb-4 rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

      {!loading && deck && phase === "setup" && (
        <>
          <div className="rounded-3xl bg-white p-6 shadow-sm sm:p-8">
            <h1 className="text-lg font-semibold text-neutral-900">Tạo quiz từ bộ thẻ</h1>
            <p className="mt-1 text-sm text-neutral-500">
              Câu hỏi 4 đáp án, không ảnh hưởng tới lịch ôn flashcard.
            </p>

            <p className="mt-6 text-xs font-medium uppercase tracking-widest text-neutral-400">Dạng câu hỏi</p>
            <div className="mt-2 grid gap-2 sm:grid-cols-3">
              {QUIZ_KINDS.map((k) => {
                const on = settings.kinds.includes(k.kind);
                return (
                  <button
                    key={k.kind}
                    type="button"
                    onClick={() => toggleKind(k.kind)}
                    aria-pressed={on}
                    className={`rounded-2xl border px-4 py-3 text-left transition ${
                      on ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/10 hover:border-black/30"
                    }`}
                  >
                    <p className="text-sm font-medium">{k.label}</p>
                    <p className={`mt-0.5 text-xs ${on ? "text-neutral-300" : "text-neutral-400"}`}>{k.hint}</p>
                  </button>
                );
              })}
            </div>

            <p className="mt-6 text-xs font-medium uppercase tracking-widest text-neutral-400">Số câu</p>
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
                  {size === null ? "Tối đa" : `${size} câu`}
                </button>
              ))}
              <input
                value={custom}
                onChange={(e) => setCustom(e.target.value.replace(/\D/g, "").slice(0, 3))}
                inputMode="numeric"
                placeholder="Số khác"
                aria-label="Số câu tự chọn"
                className={`w-24 rounded-full border px-4 py-2 text-sm outline-none ${
                  custom.trim() ? "border-neutral-900" : "border-black/10"
                }`}
              />
            </div>
            <p className="mt-3 text-xs leading-5 text-neutral-400">
              Mỗi thẻ được hỏi một lần. Dạng chọn cách đọc và điền vào câu chỉ dùng thẻ tạo từ từ điển (từ có Hán tự,
              có câu ví dụ). Tối đa 100 câu một lượt.
            </p>

            <button
              type="button"
              onClick={() => void start()}
              disabled={busy || deck.cardCount === 0}
              className="mt-6 w-full rounded-full bg-neutral-900 py-3.5 text-sm font-medium text-white disabled:opacity-40"
            >
              {busy ? "Đang tạo câu hỏi..." : deck.cardCount === 0 ? "Bộ thẻ chưa có thẻ nào" : "Bắt đầu"}
            </button>
          </div>

          {history.length > 0 && (
            <div className="mt-6 rounded-3xl bg-white p-6 shadow-sm">
              <p className="text-sm font-semibold text-neutral-900">Lịch sử gần đây</p>
              <ul className="mt-3 divide-y divide-black/5">
                {history.map((h) => (
                  <li key={h.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                    <span className="text-neutral-500">
                      {formatDate(h.createdAt)}
                      <span className="ml-2 text-xs text-neutral-400">
                        {h.kinds.map((k) => QUIZ_KINDS.find((x) => x.kind === k)?.label ?? k).join(", ")}
                      </span>
                    </span>
                    <span className="shrink-0 font-medium tabular-nums text-neutral-900">
                      {h.correct}/{h.total}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}

      {q && (
        <div className="rounded-3xl bg-white p-6 shadow-sm sm:p-8">
          <div className="flex items-center justify-between text-xs text-neutral-400">
            <span className="uppercase tracking-widest">{KIND_TITLE[q.kind]}</span>
            <span className="tabular-nums">
              {index + 1} / {questions.length}
            </span>
          </div>
          <div className="mt-2 h-1 overflow-hidden rounded-full bg-neutral-100">
            <div className="h-full bg-neutral-900" style={{ width: `${((index + 1) / questions.length) * 100}%` }} />
          </div>

          <p
            className={`mt-8 break-words text-center leading-relaxed text-neutral-900 ${cardFontClass(q.prompt)} ${promptSize(
              q.kind,
              q.prompt
            )}`}
          >
            {q.prompt}
          </p>

          <div className="mt-8 grid gap-3 sm:grid-cols-2">
            {q.options.map((option, i) => {
              const isAnswer = i === q.answer;
              const isPicked = picked[index] === i;
              const style = !answered
                ? "border-black/10 hover:border-black/30"
                : isAnswer
                  ? "border-emerald-300 bg-emerald-50 text-emerald-800"
                  : isPicked
                    ? "border-red-300 bg-red-50 text-red-700"
                    : "border-black/5 text-neutral-400";
              return (
                <button
                  key={i}
                  type="button"
                  disabled={answered}
                  onClick={() => choose(i)}
                  className={`flex items-center gap-3 rounded-2xl border px-4 py-3.5 text-left text-[15px] transition disabled:cursor-default ${style}`}
                >
                  <span className={`min-w-0 flex-1 break-words ${cardFontClass(option)}`}>{option}</span>
                  {answered && isAnswer && <Check size={18} className="shrink-0 text-emerald-600" />}
                  {answered && isPicked && !isAnswer && <X size={18} className="shrink-0 text-red-500" />}
                </button>
              );
            })}
          </div>

          {answered && (
            <div className="mt-5 rounded-2xl bg-[#F5F3EE] px-4 py-3 text-sm leading-6 text-neutral-700">
              <p>
                <span className={`font-medium text-neutral-900 ${cardFontClass(q.word)}`}>{q.word}</span>
                {q.reading && <span className="ml-2 text-neutral-500">{q.reading}</span>}
                <span className="ml-2">{q.meaning}</span>
              </p>
              {q.translation && <p className="mt-1 text-neutral-500">{q.translation}</p>}
            </div>
          )}

          <div className="mt-6 flex justify-end">
            <button
              type="button"
              onClick={next}
              disabled={!answered}
              className="flex items-center gap-1 rounded-full bg-neutral-900 px-6 py-2.5 text-sm font-medium text-white disabled:opacity-30"
            >
              {index + 1 >= questions.length ? "Xem kết quả" : "Câu tiếp"}
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      )}

      {phase === "done" && (
        <div className="rounded-3xl bg-white p-6 shadow-sm sm:p-8">
          <div className="text-center">
            <p className="text-xs uppercase tracking-widest text-neutral-400">Kết quả</p>
            <p className="mt-2 text-4xl font-semibold tabular-nums text-neutral-900">
              {score}/{questions.length}
            </p>
            <p className="mt-1 text-sm text-neutral-500">
              Đúng {Math.round((score / questions.length) * 100)}% số câu
            </p>
          </div>

          {mistakes.length > 0 && (
            <div className="mt-6">
              <p className="text-sm font-semibold text-neutral-900">Các câu sai</p>
              <ul className="mt-2 divide-y divide-black/5">
                {mistakes.map((m, i) => (
                  <li key={i} className="py-2.5 text-sm leading-6">
                    <span className={`font-medium text-neutral-900 ${cardFontClass(m.word)}`}>{m.word}</span>
                    {m.reading && <span className="ml-2 text-neutral-500">{m.reading}</span>}
                    <span className="ml-2 text-neutral-700">{m.meaning}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="mt-6 flex flex-col justify-center gap-2 sm:flex-row">
            <button
              type="button"
              onClick={() => void start()}
              disabled={busy}
              className="flex items-center justify-center gap-2 rounded-full bg-neutral-900 px-5 py-2.5 text-sm font-medium text-white disabled:opacity-50"
            >
              <RotateCcw size={14} />
              Làm lượt mới
            </button>
            <button
              type="button"
              onClick={() => setPhase("setup")}
              className="rounded-full border border-black/10 px-5 py-2.5 text-sm text-neutral-700 hover:border-black/30"
            >
              Đổi cài đặt
            </button>
            <Link
              href={`/flashcard/${deckId}`}
              className="rounded-full px-5 py-2.5 text-center text-sm text-neutral-500 hover:text-neutral-800"
            >
              Về bộ thẻ
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

export default function QuizPage() {
  return (
    <RequireAuth>
      <Quiz />
    </RequireAuth>
  );
}
