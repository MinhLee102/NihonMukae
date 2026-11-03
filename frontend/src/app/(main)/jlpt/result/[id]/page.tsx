"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight, RotateCcw, X } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { AnswerSheet, type SheetCell } from "@/components/jlpt/AnswerSheet";
import { QuestionPanel } from "@/components/jlpt/QuestionPanel";
import { ResultSummary } from "@/components/jlpt/ResultSummary";
import { SectionAudioReview } from "@/components/jlpt/ListeningAudio";
import { useAuthStore } from "@/store/authStore";
import { useFeedbackStore } from "@/store/feedbackStore";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import Link from "next/link";
import { getResult, retrySession, saveSessionToShelf, stashSessionWarnings, type ResultDto, type ShelfSaveScope } from "@/lib/api/jlpt";

const SCORING_LABELS: Record<string, string> = {
  language: "Ngôn ngữ (chữ, từ, ngữ pháp)",
  reading: "Đọc hiểu",
  language_reading: "Ngôn ngữ và đọc hiểu",
  listening: "Nghe hiểu",
};

function formatScore(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

function ResultView() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const openFeedback = useFeedbackStore((s) => s.open);
  const accessToken = useAuthStore((s) => s.accessToken);

  const [result, setResult] = useState<ResultDto | null>(null);
  const [current, setCurrent] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<{ id: string; title: string } | null>(null);
  const [view, setView] = useState<"summary" | "review">("summary");

  useEffect(() => {
    if (!accessToken) return;
    getResult(id)
      .then(setResult)
      .catch((err) => setError(getApiErrorMessage(err, "Không tải được kết quả.")));
  }, [accessToken, id]);

  const questions = useMemo(() => result?.questions ?? [], [result]);
  const positions = useMemo(() => questions.map((q) => q.position), [questions]);

  const go = useCallback(
    (delta: number) => {
      const target = positions[positions.indexOf(current) + delta];
      if (target !== undefined) setCurrent(target);
    },
    [positions, current]
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key === "ArrowRight") go(1);
      else if (e.key === "ArrowLeft") go(-1);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [go]);

  const saveToShelf = async (scope: ShelfSaveScope, position?: number) => {
    try {
      setSaving(true);
      setSaved(null);
      setError(null);
      setSaved(await saveSessionToShelf(id, scope, position));
    } catch (err) {
      setError(getApiErrorMessage(err, "Không lưu được vào My Shelf."));
    } finally {
      setSaving(false);
    }
  };

  const retry = async () => {
    try {
      setRetrying(true);
      const session = await retrySession(id);
      stashSessionWarnings(session.id, session.warnings);
      router.push(`/jlpt/session/${session.id}`);
    } catch (err) {
      setError(getApiErrorMessage(err));
      setRetrying(false);
    }
  };

  const q = questions.find((x) => x.position === current);
  const group = q?.groupId !== undefined ? result?.groups.find((g) => g.id === q.groupId) : undefined;
  const percent = result && result.total > 0 ? Math.round((result.correct / result.total) * 100) : 0;
  const cells: SheetCell[] = questions.map((x) => ({
    position: x.position,
    state: x.selected === undefined ? "skipped" : x.correct ? "correct" : "wrong",
  }));

  return (
    <div className="-mx-8 -my-8 flex h-[calc(100%+4rem)] min-h-[560px] flex-col">
      <header className="flex shrink-0 items-center gap-4 border-b border-black/5 bg-white px-5 py-3">
        <button
          type="button"
          onClick={() => router.push("/jlpt")}
          aria-label="Đóng"
          className="rounded-full p-2 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700"
        >
          <X size={18} />
        </button>
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-neutral-900">{result?.title ?? "Đang tải..."}</p>
          <p className="text-[10px] tracking-widest text-neutral-400">KẾT QUẢ VÀ ĐÁP ÁN</p>
        </div>
        <nav className="ml-auto flex gap-1 rounded-full bg-[#F5F3EE] p-1 text-sm">
          {(
            [
              ["summary", "Tổng kết"],
              ["review", "Xem lại từng câu"],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => setView(id)}
              className={`rounded-full px-4 py-1.5 transition ${
                view === id ? "bg-neutral-900 text-white" : "text-neutral-500 hover:text-neutral-800"
              }`}
            >
              {label}
            </button>
          ))}
        </nav>
      </header>

      {error && <div className="shrink-0 bg-red-50 px-5 py-2.5 text-xs text-red-700">{error}</div>}

      {result?.listeningAudioUrl && view === "review" && <SectionAudioReview url={result.listeningAudioUrl} />}

      <div className="flex min-h-0 flex-1">
        <main className="min-h-0 min-w-0 flex-1 bg-[#F5F3EE]">
          {!result && !error && <p className="py-24 text-center text-sm text-neutral-400">Đang tải...</p>}
          {result && view === "summary" && (
            <ResultSummary
              result={result}
              onReview={(position) => {
                setCurrent(position);
                setView("review");
              }}
            />
          )}
          {result && q && view === "review" && (
            <QuestionPanel
              sectionAudio={!!result.listeningAudioUrl}
              splitKey="jlpt:split"
              question={{
                position: q.position,
                total: questions.length,
                section: q.section,
                typeName: q.typeName,
                typeCode: q.typeCode,
                question: q.question,
                options: q.options,
                audioUrl: q.audioUrl,
                selected: q.selected,
                correctAnswer: q.correctAnswer,
                explanation: q.explanation,
                mondaiNo: q.mondaiNo,
                instructionJa: q.instructionJa,
                instructionVi: q.instructionVi,
              }}
              group={group}
              disabled
              onSaveToShelf={() => void saveToShelf("question", q.position)}
              onReport={() =>
                openFeedback({
                  category: "exam_error",
                  context: { questionId: q.questionId, sessionId: id },
                  hint: `Báo lỗi câu ${q.position}. Mô tả chỗ sai (đáp án, nội dung, audio...).`,
                })
              }
              footer={
                <div className="flex items-center justify-between">
                  <button
                    type="button"
                    onClick={() => go(-1)}
                    disabled={positions.indexOf(current) <= 0}
                    className="flex items-center gap-1 rounded-full border border-black/10 bg-white px-5 py-2.5 text-sm text-neutral-700 disabled:opacity-40"
                  >
                    <ChevronLeft size={14} />
                    Câu trước
                  </button>
                  <button
                    type="button"
                    onClick={() => go(1)}
                    disabled={positions.indexOf(current) >= positions.length - 1}
                    className="flex items-center gap-1 rounded-full bg-neutral-900 px-5 py-2.5 text-sm font-medium text-white disabled:opacity-40"
                  >
                    Câu tiếp theo
                    <ChevronRight size={14} />
                  </button>
                </div>
              }
            />
          )}
        </main>

        <aside className="hidden w-[320px] shrink-0 overflow-y-auto border-l border-black/5 bg-white p-5 lg:block">
          {result && (
            <div className="flex flex-col gap-5">
              <div className="rounded-2xl bg-[#1A1A1A] p-5 text-white">
                <p className="text-[10px] tracking-[0.2em] text-neutral-400">
                  {result.jlpt ? "ĐIỂM ƯỚC TÍNH" : "KẾT QUẢ"}
                </p>
                {result.jlpt ? (
                  <>
                    <p className="mt-2 text-3xl font-semibold">
                      {formatScore(result.jlpt.total)}
                      <span className="text-base font-normal text-neutral-400">/{result.jlpt.maxTotal}</span>
                    </p>
                    <p className="mt-1 text-xs text-neutral-300">
                      {result.correct}/{result.total} câu đúng
                    </p>
                    <p
                      className={`mt-3 inline-block rounded-full px-3 py-1 text-[11px] font-medium ${
                        result.jlpt.passed === true
                          ? "bg-emerald-500/20 text-emerald-300"
                          : result.jlpt.passed === false
                            ? "bg-red-500/20 text-red-300"
                            : "bg-white/10 text-neutral-300"
                      }`}
                    >
                      {result.jlpt.passed === true
                        ? `Đạt (điểm đỗ ${result.jlpt.passScore})`
                        : result.jlpt.passed === false
                          ? `Chưa đạt (điểm đỗ ${result.jlpt.passScore})`
                          : "Chưa đủ các phần để kết luận đạt hay chưa"}
                    </p>
                  </>
                ) : (
                  <>
                    <p className="mt-2 text-3xl font-semibold">
                      {result.correct}
                      <span className="text-base font-normal text-neutral-400">/{result.total}</span>
                    </p>
                    <p className="mt-1 text-xs text-neutral-300">{percent}% câu trả lời chính xác</p>
                  </>
                )}
                {result.unanswered > 0 && (
                  <p className="mt-2 text-[11px] text-neutral-400">{result.unanswered} câu chưa trả lời</p>
                )}
              </div>

              {result.jlpt && (
                <div className="space-y-3">
                  {result.jlpt.sections.map((s) => (
                    <div key={s.code}>
                      <div className="flex justify-between text-xs">
                        <span className="text-neutral-600">{SCORING_LABELS[s.code] ?? s.code}</span>
                        <span className={`font-medium ${s.belowMin ? "text-red-600" : "text-neutral-800"}`}>
                          {formatScore(s.score)}/{s.max}
                        </span>
                      </div>
                      <div className="mt-1 h-1.5 rounded-full bg-[#E8E4DA]">
                        <div
                          className={`h-1.5 rounded-full ${s.belowMin ? "bg-red-500" : "bg-neutral-900"}`}
                          style={{ width: `${(s.score / s.max) * 100}%` }}
                        />
                      </div>
                      {s.belowMin && <p className="mt-0.5 text-[10px] text-red-500">Dưới điểm liệt {s.min}</p>}
                    </div>
                  ))}
                  <p className="text-[10px] leading-4 text-neutral-400">
                    Điểm chỉ là ước tính theo điểm mỗi câu của từng dạng bài. JLPT thật dùng điểm quy đổi nên có thể khác.
                  </p>
                </div>
              )}

              <AnswerSheet
                cells={cells}
                current={current}
                onJump={(position) => {
                  setCurrent(position);
                  setView("review");
                }}
                legend={
                  <ul className="space-y-2 border-t border-black/5 pt-4 text-[11px] text-neutral-500">
                    <li className="flex items-center gap-2">
                      <span className="h-3.5 w-3.5 rounded border border-emerald-200 bg-emerald-50" /> Đúng
                    </li>
                    <li className="flex items-center gap-2">
                      <span className="h-3.5 w-3.5 rounded border border-red-200 bg-red-50" /> Sai
                    </li>
                    <li className="flex items-center gap-2">
                      <span className="h-3.5 w-3.5 rounded border border-black/10 bg-white" /> Chưa trả lời
                    </li>
                  </ul>
                }
              />

              <div className="space-y-2 border-t border-black/5 pt-4">
                <p className="text-[10px] tracking-widest text-neutral-400">LƯU VÀO MY SHELF</p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    disabled={saving || result.correct === result.total}
                    onClick={() => void saveToShelf("wrong")}
                    className="flex-1 rounded-full border border-black/10 bg-white py-2 text-xs text-neutral-700 hover:border-black/30 disabled:opacity-40"
                  >
                    Câu sai
                  </button>
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => void saveToShelf("all")}
                    className="flex-1 rounded-full border border-black/10 bg-white py-2 text-xs text-neutral-700 hover:border-black/30 disabled:opacity-40"
                  >
                    Cả bài
                  </button>
                </div>
                {saved && (
                  <p className="text-xs text-emerald-700">
                    Đã lưu.{" "}
                    <Link href={`/shelf/${saved.id}`} className="underline">
                      Mở trong My Shelf
                    </Link>
                  </p>
                )}
                <p className="text-[10px] leading-4 text-neutral-400">
                  Bài lưu có furigana, bấm vào chữ để tra từ và thêm vào Flashcard. Phần giải thích tiếng Việt không được lưu.
                </p>
              </div>

              <div className="flex flex-col gap-2">
                <button
                  type="button"
                  onClick={retry}
                  disabled={retrying}
                  className="flex items-center justify-center gap-2 rounded-full border border-black/10 bg-white py-2.5 text-sm text-neutral-800 hover:border-black/30 disabled:opacity-50"
                >
                  <RotateCcw size={14} />
                  {retrying ? "Đang tạo..." : "Làm lại"}
                </button>
                <button
                  type="button"
                  onClick={() => router.push("/jlpt")}
                  className="rounded-full bg-neutral-900 py-2.5 text-sm font-medium text-white"
                >
                  Chọn đề khác
                </button>
              </div>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

export default function ResultPage() {
  return (
    <RequireAuth>
      <ResultView />
    </RequireAuth>
  );
}
