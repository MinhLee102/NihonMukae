"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronRight, Clock, HelpCircle, ListChecks, Shuffle } from "lucide-react";
import { ConfirmDialog } from "@/components/jlpt/ConfirmDialog";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import {
  SECTION_LABELS,
  getBlueprint,
  getTests,
  startSession,
  stashSessionWarnings,
  type BlueprintDto,
  type TestSummaryDto,
} from "@/lib/api/jlpt";

const LEVELS = [5, 4, 3, 2, 1];

type Target = { kind: "test"; test: TestSummaryDto } | { kind: "random" };

/** "Làm đề JLPT": choose a level, then a specific test or a random exam built from the real blueprint. */
export function ExamPicker({ initialLevel = 3 }: { initialLevel?: number }) {
  const router = useRouter();
  const [level, setLevel] = useState(initialLevel);
  const [tests, setTests] = useState<TestSummaryDto[]>([]);
  const [blueprint, setBlueprint] = useState<BlueprintDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [target, setTarget] = useState<Target | null>(null);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([getTests(level), getBlueprint(level)])
      .then(([t, b]) => {
        if (cancelled) return;
        setTests(t);
        setBlueprint(b);
      })
      .catch((err) => !cancelled && setError(getApiErrorMessage(err, "Không tải được danh sách đề.")))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [level]);

  const start = async () => {
    if (!target) return;
    try {
      setStarting(true);
      const session = await startSession(
        target.kind === "test" ? { mode: "exam", level, testId: target.test.id } : { mode: "exam", level, random: true }
      );
      stashSessionWarnings(session.id, session.warnings);
      router.push(`/jlpt/session/${session.id}`);
    } catch (err) {
      setError(getApiErrorMessage(err));
      setTarget(null);
      setStarting(false);
    }
  };

  const totalMinutes = blueprint?.blocks.reduce((sum, b) => sum + b.durationMin, 0) ?? 0;
  const totalQuestions = blueprint?.blocks.reduce((sum, b) => sum + b.types.reduce((s, t) => s + t.quantity, 0), 0) ?? 0;

  return (
    <div>
      <div className="mb-6 inline-flex rounded-2xl bg-[#EFEBE2] p-1">
        {LEVELS.map((l) => (
          <button
            key={l}
            type="button"
            onClick={() => setLevel(l)}
            className={`rounded-xl px-6 py-2 text-sm font-medium transition ${
              level === l ? "bg-white text-neutral-900 shadow-sm" : "text-neutral-500 hover:text-neutral-800"
            }`}
          >
            N{l}
          </button>
        ))}
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
      {loading && <p className="py-10 text-sm text-neutral-400">Đang tải...</p>}

      {!loading && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <button
            type="button"
            onClick={() => setTarget({ kind: "random" })}
            className="flex overflow-hidden rounded-2xl border border-black/5 bg-white text-left transition hover:border-black/20"
          >
            <div className="flex w-20 shrink-0 flex-col items-center justify-center gap-1 bg-[#1A1A1A] text-white">
              <Shuffle size={18} />
              <span className="text-[10px] tracking-widest text-neutral-400">RANDOM</span>
            </div>
            <div className="min-w-0 flex-1 p-5">
              <p className="text-sm font-semibold text-neutral-900">Đề ngẫu nhiên N{level}</p>
              <p className="mt-0.5 text-xs text-neutral-400">Bốc câu từ kho theo đúng cấu trúc đề thật</p>
              <div className="mt-3 flex items-center gap-4 text-xs text-neutral-500">
                <span className="flex items-center gap-1">
                  <ListChecks size={13} />
                  {totalQuestions} câu
                </span>
                <span className="flex items-center gap-1">
                  <Clock size={13} />
                  {totalMinutes} phút
                </span>
              </div>
            </div>
            <ChevronRight size={16} className="mr-4 mt-5 text-neutral-400" />
          </button>

          {tests.map((test, index) => (
            <button
              key={test.id}
              type="button"
              onClick={() => setTarget({ kind: "test", test })}
              className="flex overflow-hidden rounded-2xl border border-black/5 bg-white text-left transition hover:border-black/20"
            >
              <div className="flex w-20 shrink-0 flex-col items-center justify-center bg-[#1A1A1A] text-white">
                <span className="text-xl font-semibold">N{level}</span>
                <span className="mt-1 text-[10px] tracking-widest text-neutral-400">
                  SET {String(index + 1).padStart(2, "0")}
                </span>
              </div>
              <div className="min-w-0 flex-1 p-5">
                <p className="truncate text-sm font-semibold text-neutral-900">{test.title}</p>
                {test.sourceNote && <p className="mt-0.5 truncate text-xs text-neutral-400">{test.sourceNote}</p>}
                <div className="mt-3 flex items-center gap-4 text-xs text-neutral-500">
                  <span className="flex items-center gap-1">
                    <ListChecks size={13} />
                    {test.questionCount} câu
                  </span>
                  <span className="flex items-center gap-1">
                    <Clock size={13} />
                    {test.durationMin} phút
                  </span>
                </div>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {test.sections.map((s) => (
                    <span key={s} className="rounded bg-[#EFEBE2] px-2 py-0.5 text-[10px] text-neutral-500">
                      {SECTION_LABELS[s].ja}
                    </span>
                  ))}
                </div>
              </div>
              <ChevronRight size={16} className="mr-4 mt-5 text-neutral-400" />
            </button>
          ))}
        </div>
      )}

      {!loading && blueprint && (
        <div className="mt-6 flex items-start gap-3 rounded-2xl border border-dashed border-black/10 px-5 py-4 text-xs text-neutral-500">
          <HelpCircle size={16} className="mt-0.5 shrink-0 text-neutral-400" />
          <div>
            <p>
              Mỗi khối thi có giờ riêng, hết giờ khối nào thì khóa khối đó:{" "}
              {blueprint.blocks.map((b) => `${b.name} ${b.durationMin}'`).join(" · ")}.
            </p>
            {blueprint.shortages.length > 0 && (
              <p className="mt-1 text-amber-600">
                Kho chưa đủ câu cho {blueprint.shortages.length} dạng bài, nên đề ngẫu nhiên sẽ ngắn hơn đề thật.
              </p>
            )}
          </div>
        </div>
      )}

      {target && (
        <ConfirmDialog
          title="Bắt đầu làm bài?"
          confirmLabel="Bắt đầu"
          busy={starting}
          onConfirm={start}
          onCancel={() => setTarget(null)}
        >
          Đồng hồ khối đầu tiên sẽ chạy ngay khi bạn bắt đầu.{" "}
          {target.kind === "test" ? `Đề: ${target.test.title}.` : `Đề ngẫu nhiên N${level}.`}
        </ConfirmDialog>
      )}
    </div>
  );
}
