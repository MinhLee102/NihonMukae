"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight, Clock, Infinity as InfinityIcon, X } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { AnswerSheet, type SheetCell } from "@/components/jlpt/AnswerSheet";
import { ConfirmDialog } from "@/components/jlpt/ConfirmDialog";
import { QuestionPanel } from "@/components/jlpt/QuestionPanel";
import { SectionAudio, SectionAudioReview } from "@/components/jlpt/ListeningAudio";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import {
  getSession,
  saveAnswer,
  startBlock,
  submitBlock,
  submitSession,
  takeSessionWarnings,
  type QuestionViewDto,
  type SessionViewDto,
} from "@/lib/api/jlpt";

const LETTERS = ["A", "B", "C", "D"];

function formatClock(ms: number): string {
  const total = Math.max(Math.ceil(ms / 1000), 0);
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function Runner() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);

  const [view, setView] = useState<SessionViewDto | null>(null);
  const [offsetMs, setOffsetMs] = useState(0);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [current, setCurrent] = useState<number | null>(null);
  const [flags, setFlags] = useState<Set<number>>(new Set());
  const [warnings, setWarnings] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<"submit" | "exit" | null>(null);
  const [busy, setBusy] = useState(false);
  const handledExpiry = useRef<number | null>(null);
  const finalising = useRef(false);

  const flagsKey = `jlpt:flags:${id}`;

  const apply = useCallback(
    (next: SessionViewDto) => {
      setOffsetMs(new Date(next.serverNow).getTime() - Date.now());
      setView(next);
      if (next.status === "submitted") {
        router.replace(`/jlpt/result/${next.id}`);
        return;
      }
      setCurrent((prev) => {
        const stillOpen = prev !== null && next.questions.some((q) => q.position === prev && !q.hidden);
        if (stillOpen) return prev;
        return next.questions.find((q) => !q.hidden)?.position ?? null;
      });
    },
    [router]
  );

  const reload = useCallback(async () => {
    try {
      apply(await getSession(id));
    } catch (err) {
      setError(getApiErrorMessage(err, "Không tải được phiên làm bài."));
    }
  }, [apply, id]);

  useEffect(() => {
    if (!accessToken) return;
    setWarnings(takeSessionWarnings(id));
    try {
      const saved = localStorage.getItem(flagsKey);
      if (saved) setFlags(new Set(JSON.parse(saved) as number[]));
    } catch {
      // storage unavailable: flags just are not remembered
    }
    reload();
  }, [accessToken, id, flagsKey, reload]);

  useEffect(() => {
    const timer = setInterval(() => setNowMs(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const exam = view?.mode === "exam";
  const activeBlock = view?.blocks.find((b) => b.status === "active");
  const pendingBlock = view?.blocks.find((b) => b.status === "pending");
  // A block without a deadline (unlimited practice) comes back as null, which must not read as "time is up"
  const remainingMs = activeBlock?.deadline ? new Date(activeBlock.deadline).getTime() - (nowMs + offsetMs) : null;

  const finalSubmit = useCallback(async () => {
    if (finalising.current) return;
    finalising.current = true;
    setBusy(true);
    try {
      await submitSession(id);
      router.replace(`/jlpt/result/${id}`);
    } catch (err) {
      finalising.current = false;
      setBusy(false);
      setError(getApiErrorMessage(err));
    }
  }, [id, router]);

  // When a block runs out of time the server closes it; practice (a single block) is submitted outright
  useEffect(() => {
    if (!view || view.status !== "in_progress" || !activeBlock || remainingMs === null || remainingMs > 0) return;
    if (handledExpiry.current === activeBlock.blockNo) return;
    handledExpiry.current = activeBlock.blockNo;
    if (!exam) {
      finalSubmit();
      return;
    }
    submitBlock(id, activeBlock.blockNo).then(apply).catch(reload);
  }, [view, activeBlock, remainingMs, exam, id, apply, reload, finalSubmit]);

  // Every block is closed and none is waiting to start: nothing left to do, so submit
  useEffect(() => {
    if (view && view.status === "in_progress" && !activeBlock && !pendingBlock) finalSubmit();
  }, [view, activeBlock, pendingBlock, finalSubmit]);

  const questions = useMemo(() => view?.questions ?? [], [view]);
  const openPositions = useMemo(() => questions.filter((q) => !q.hidden).map((q) => q.position), [questions]);
  const currentQuestion: QuestionViewDto | undefined = questions.find((q) => q.position === current);

  const toggleFlag = (position: number) =>
    setFlags((prev) => {
      const next = new Set(prev);
      if (next.has(position)) next.delete(position);
      else next.add(position);
      try {
        localStorage.setItem(flagsKey, JSON.stringify(Array.from(next)));
      } catch {
        // ignore
      }
      return next;
    });

  const patchQuestion = (position: number, patch: Partial<QuestionViewDto>) =>
    setView((v) =>
      v ? { ...v, questions: v.questions.map((q) => (q.position === position ? { ...q, ...patch } : q)) } : v
    );

  const choose = useCallback(
    async (letter: string) => {
      const q = currentQuestion;
      if (!q || q.hidden || q.locked || q.questionId === undefined) return;
      const next = q.selected === letter ? null : letter;
      patchQuestion(q.position, { selected: next ?? undefined });
      try {
        const res = await saveAnswer(id, q.questionId, next);
        if (res.locked) {
          patchQuestion(q.position, {
            locked: true,
            correct: res.correct,
            correctAnswer: res.correctAnswer,
            explanation: res.explanation,
          });
        }
      } catch (err) {
        setError(getApiErrorMessage(err));
        reload();
      }
    },
    [currentQuestion, id, reload]
  );

  const go = useCallback(
    (delta: number) => {
      if (current === null) return;
      const index = openPositions.indexOf(current);
      const target = openPositions[index + delta];
      if (target !== undefined) setCurrent(target);
    },
    [current, openPositions]
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (dialog || e.metaKey || e.ctrlKey || e.altKey) return;
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") return;
      if (e.key === "ArrowRight") go(1);
      else if (e.key === "ArrowLeft") go(-1);
      else if (e.key >= "1" && e.key <= "4") {
        const letter = LETTERS[parseInt(e.key, 10) - 1];
        if (currentQuestion?.options && currentQuestion.options.length >= parseInt(e.key, 10)) choose(letter);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [dialog, go, choose, currentQuestion]);

  const isLastBlock = !pendingBlock;
  const submitLabel = exam && !isLastBlock ? "Nộp khối" : "Nộp bài";

  const unansweredInScope = questions.filter(
    (q) => !q.hidden && q.selected === undefined && (!exam || q.blockNo === activeBlock?.blockNo)
  ).length;

  const confirmSubmit = async () => {
    setBusy(true);
    try {
      if (exam && !isLastBlock && activeBlock) {
        apply(await submitBlock(id, activeBlock.blockNo));
        setDialog(null);
        setBusy(false);
      } else {
        setDialog(null);
        await finalSubmit();
      }
    } catch (err) {
      setError(getApiErrorMessage(err));
      setBusy(false);
    }
  };

  const beginBlock = async (blockNo: number) => {
    setBusy(true);
    try {
      apply(await startBlock(id, blockNo));
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const cells: SheetCell[] = questions.map((q) => ({
    position: q.position,
    state: q.selected !== undefined ? "answered" : "unanswered",
    flagged: flags.has(q.position),
    locked: q.hidden,
  }));
  const answeredCount = questions.filter((q) => q.selected !== undefined).length;
  const group = currentQuestion?.groupId !== undefined ? view?.groups.find((g) => g.id === currentQuestion.groupId) : undefined;
  const lowTime = remainingMs !== null && remainingMs < 5 * 60 * 1000;

  const subtitle = exam
    ? `JLPT SIMULATION${activeBlock ? ` · KHỐI ${activeBlock.blockNo} · ${activeBlock.name.toUpperCase()}` : ""}`
    : "LUYỆN THEO DẠNG BÀI";

  return (
    <div className="-mx-8 -my-8 flex h-[calc(100%+4rem)] min-h-[560px] flex-col">
      <header className="flex shrink-0 items-center gap-4 border-b border-black/5 bg-white px-5 py-3">
        <button
          type="button"
          onClick={() => setDialog("exit")}
          aria-label="Thoát"
          className="rounded-full p-2 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700"
        >
          <X size={18} />
        </button>
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-neutral-900">{view?.title ?? "Đang tải..."}</p>
          <p className="text-[10px] tracking-widest text-neutral-400">{subtitle}</p>
        </div>
        <div className="ml-auto flex items-center gap-3">
          {activeBlock && (
            <span
              className={`flex items-center gap-2 rounded-full px-4 py-2 text-xs font-semibold tabular-nums text-white ${
                lowTime ? "bg-red-600" : "bg-neutral-900"
              }`}
            >
              {remainingMs === null ? (
                <>
                  <InfinityIcon size={14} />
                  Không giới hạn
                </>
              ) : (
                <>
                  <Clock size={14} />
                  {formatClock(remainingMs)}
                </>
              )}
            </span>
          )}
          <button
            type="button"
            onClick={() => setDialog("submit")}
            disabled={!activeBlock || busy}
            className="rounded-full border border-black/10 bg-white px-4 py-2 text-xs font-medium text-neutral-800 hover:border-black/30 disabled:opacity-40"
          >
            {submitLabel}
          </button>
        </div>
      </header>

      {warnings.length > 0 && (
        <div className="flex shrink-0 items-start justify-between gap-3 bg-amber-50 px-5 py-2.5 text-xs text-amber-700">
          <span>{warnings.join(" ")}</span>
          <button type="button" onClick={() => setWarnings([])} aria-label="Đóng thông báo">
            <X size={14} />
          </button>
        </div>
      )}
      {error && (
        <div className="flex shrink-0 items-start justify-between gap-3 bg-red-50 px-5 py-2.5 text-xs text-red-700">
          <span>{error}</span>
          <button type="button" onClick={() => setError(null)} aria-label="Đóng thông báo">
            <X size={14} />
          </button>
        </div>
      )}

      {!exam && activeBlock?.audioUrl && <SectionAudioReview url={activeBlock.audioUrl} />}
      {exam && activeBlock?.audioUrl && (
        <SectionAudio key={`${id}:${activeBlock.blockNo}`} url={activeBlock.audioUrl} progressKey={`${id}:${activeBlock.blockNo}`} />
      )}

      <div className="flex min-h-0 flex-1">
        <main className="min-h-0 min-w-0 flex-1 bg-[#F5F3EE]">
          {!view && <p className="py-24 text-center text-sm text-neutral-400">Đang tải...</p>}

          {view && !activeBlock && pendingBlock && (
            <div className="mx-auto flex h-full max-w-md flex-col items-center justify-center gap-3 px-6 text-center">
              <p className="text-[10px] tracking-[0.2em] text-neutral-400">
                KHỐI {pendingBlock.blockNo} · {pendingBlock.questionCount} CÂU
              </p>
              <h2 className="text-2xl font-semibold text-neutral-900">{pendingBlock.name}</h2>
              <p className="text-sm text-neutral-500">
                Thời gian làm bài {pendingBlock.durationMin} phút. Khối trước đã đóng, bạn không thể quay lại.
              </p>
              <button
                type="button"
                onClick={() => beginBlock(pendingBlock.blockNo)}
                disabled={busy}
                className="mt-3 rounded-full bg-neutral-900 px-8 py-3 text-sm font-medium text-white disabled:opacity-50"
              >
                {busy ? "Đang bắt đầu..." : "Bắt đầu khối"}
              </button>
            </div>
          )}

          {view && activeBlock && currentQuestion && !currentQuestion.hidden && currentQuestion.options && (
            <QuestionPanel
              splitKey="jlpt:split"
              audioMode={exam ? "exam" : "free"}
              sectionAudio={!!activeBlock?.audioUrl}
              question={{
                position: currentQuestion.position,
                total: questions.length,
                section: currentQuestion.section,
                typeName: currentQuestion.typeName,
                typeCode: currentQuestion.typeCode,
                question: currentQuestion.question ?? "",
                options: currentQuestion.options,
                audioUrl: currentQuestion.audioUrl,
                selected: currentQuestion.selected,
                correctAnswer: currentQuestion.locked ? currentQuestion.correctAnswer : undefined,
                explanation: currentQuestion.explanation,
                mondaiNo: currentQuestion.mondaiNo,
                instructionJa: currentQuestion.instructionJa,
                instructionVi: currentQuestion.instructionVi,
              }}
              group={group}
              disabled={currentQuestion.locked || busy}
              onSelect={choose}
              flagged={flags.has(currentQuestion.position)}
              onToggleFlag={() => toggleFlag(currentQuestion.position)}
              footer={
                <div className="flex items-center justify-between">
                  <button
                    type="button"
                    onClick={() => go(-1)}
                    disabled={openPositions.indexOf(currentQuestion.position) <= 0}
                    className="flex items-center gap-1 rounded-full border border-black/10 bg-white px-5 py-2.5 text-sm text-neutral-700 disabled:opacity-40"
                  >
                    <ChevronLeft size={14} />
                    Câu trước
                  </button>
                  <button
                    type="button"
                    onClick={() => go(1)}
                    disabled={openPositions.indexOf(currentQuestion.position) >= openPositions.length - 1}
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

        <aside className="hidden w-[300px] shrink-0 overflow-y-auto border-l border-black/5 bg-white p-5 lg:block">
          <AnswerSheet
            cells={cells}
            current={current}
            onJump={setCurrent}
            header={
              <div>
                <p className="text-sm font-semibold text-neutral-900">Phiếu trả lời</p>
                <p className="mt-0.5 text-[11px] text-neutral-400">
                  {answeredCount}/{questions.length} câu đã chọn
                </p>
              </div>
            }
            legend={
              <ul className="mt-2 space-y-2 border-t border-black/5 pt-4 text-[11px] text-neutral-500">
                <li className="flex items-center gap-2">
                  <span className="h-3.5 w-3.5 rounded border border-black/10 bg-[#E8E3D6]" /> Đã trả lời
                </li>
                <li className="flex items-center gap-2">
                  <span className="h-3.5 w-3.5 rounded border border-black/10 bg-white" /> Chưa trả lời
                </li>
                <li className="flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full bg-amber-400" /> Đã đánh dấu
                </li>
              </ul>
            }
          />
        </aside>
      </div>

      {dialog === "submit" && (
        <ConfirmDialog
          title={exam && !isLastBlock ? "Nộp khối này?" : "Nộp bài?"}
          confirmLabel={submitLabel}
          busy={busy}
          onConfirm={confirmSubmit}
          onCancel={() => setDialog(null)}
        >
          {unansweredInScope > 0 ? `Bạn còn ${unansweredInScope} câu chưa trả lời. ` : "Bạn đã trả lời hết các câu. "}
          {exam && !isLastBlock ? "Sau khi nộp khối này bạn không thể quay lại." : "Sau khi nộp bạn sẽ xem được kết quả."}
        </ConfirmDialog>
      )}
      {dialog === "exit" && (
        <ConfirmDialog
          title="Thoát khỏi bài làm?"
          confirmLabel="Thoát"
          onConfirm={() => router.push("/jlpt")}
          onCancel={() => setDialog(null)}
        >
          Bài làm vẫn được lưu, bạn có thể tiếp tục trong Lịch sử. Đồng hồ vẫn tiếp tục chạy.
        </ConfirmDialog>
      )}
    </div>
  );
}

export default function SessionPage() {
  return (
    <RequireAuth>
      <Runner />
    </RequireAuth>
  );
}
