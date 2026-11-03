"use client";

import { useMemo } from "react";
import { AlertTriangle, ArrowRight } from "lucide-react";
import { SECTION_LABELS, SECTION_ORDER, type ResultDto, type Section } from "@/lib/api/jlpt";

interface Tally {
  key: string;
  label: string;
  correct: number;
  wrong: number;
  skipped: number;
  total: number;
  section?: Section;
  /** position of the first question that was not answered correctly, to jump into the review */
  firstMissed?: number;
}

function percentOf(t: { correct: number; total: number }): number {
  return t.total > 0 ? Math.round((t.correct / t.total) * 100) : 0;
}

/** Green from 80%, amber from 50%, red below. */
function tone(percent: number) {
  if (percent >= 80) return { bar: "bg-emerald-500", text: "text-emerald-700" };
  if (percent >= 50) return { bar: "bg-amber-400", text: "text-amber-700" };
  return { bar: "bg-red-500", text: "text-red-600" };
}

function Donut({ correct, wrong, skipped }: { correct: number; wrong: number; skipped: number }) {
  const total = correct + wrong + skipped;
  const radius = 52;
  const circumference = 2 * Math.PI * radius;
  const segments = [
    { value: correct, color: "#10b981" },
    { value: wrong, color: "#ef4444" },
    { value: skipped, color: "#d6d3cb" },
  ];
  let offset = 0;
  return (
    <svg viewBox="0 0 140 140" className="h-36 w-36 shrink-0 -rotate-90" role="img" aria-label="Tỉ lệ đúng, sai, bỏ trống">
      <circle cx="70" cy="70" r={radius} fill="none" stroke="#EFEBE2" strokeWidth="16" />
      {total > 0 &&
        segments.map((s, i) => {
          const length = (s.value / total) * circumference;
          const el = (
            <circle
              key={i}
              cx="70"
              cy="70"
              r={radius}
              fill="none"
              stroke={s.color}
              strokeWidth="16"
              strokeDasharray={`${length} ${circumference - length}`}
              strokeDashoffset={-offset}
            />
          );
          offset += length;
          return el;
        })}
    </svg>
  );
}

function BarRow({ tally, onReview }: { tally: Tally; onReview: (position: number) => void }) {
  const percent = percentOf(tally);
  const colors = tone(percent);
  return (
    <li>
      <div className="flex items-baseline justify-between gap-3 text-sm">
        <span className="min-w-0 truncate text-neutral-700">{tally.label}</span>
        <span className="shrink-0 text-xs text-neutral-500">
          <span className={`font-semibold ${colors.text}`}>{percent}%</span> · {tally.correct}/{tally.total}
        </span>
      </div>
      <div className="mt-1.5 flex items-center gap-3">
        <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-[#EFEBE2]">
          <div className={`h-full rounded-full ${colors.bar}`} style={{ width: `${percent}%` }} />
        </div>
        {tally.firstMissed !== undefined && (
          <button
            type="button"
            onClick={() => onReview(tally.firstMissed!)}
            className="shrink-0 text-[11px] text-neutral-400 underline-offset-2 hover:text-neutral-800 hover:underline"
          >
            Xem câu sai
          </button>
        )}
      </div>
    </li>
  );
}

/** Summary shown after an exam or practice session: counts, accuracy per section and per question type, weakest areas. */
export function ResultSummary({ result, onReview }: { result: ResultDto; onReview: (position: number) => void }) {
  const { byType, bySection, weakest } = useMemo(() => {
    const types = new Map<string, Tally>();
    const sections = new Map<string, Tally>();
    for (const q of result.questions) {
      const skipped = q.selected === undefined;
      const wrong = !skipped && !q.correct;
      const add = (map: Map<string, Tally>, key: string, label: string) => {
        const t = map.get(key) ?? { key, label, correct: 0, wrong: 0, skipped: 0, total: 0, section: q.section };
        t.total += 1;
        if (q.correct) t.correct += 1;
        else if (wrong) t.wrong += 1;
        else t.skipped += 1;
        if (!q.correct && t.firstMissed === undefined) t.firstMissed = q.position;
        map.set(key, t);
      };
      add(types, q.typeCode ?? "other", q.typeName ?? "Khác");
      const section = q.section ? SECTION_LABELS[q.section] : undefined;
      add(sections, q.section ?? "other", section ? `${section.ja} ${section.vi}` : "Khác");
    }
    const byType = Array.from(types.values()).sort((a, b) => percentOf(a) - percentOf(b) || b.wrong - a.wrong);
    const bySection = SECTION_ORDER.map((s) => sections.get(s)).filter((t): t is Tally => !!t);
    // Only types with something missed count as weak spots
    const weakest = byType.filter((t) => t.correct < t.total).slice(0, 3);
    return { byType, bySection, weakest };
  }, [result]);

  const skipped = result.unanswered;
  const wrong = result.total - result.correct - skipped;
  const percent = result.total > 0 ? Math.round((result.correct / result.total) * 100) : 0;

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto flex max-w-3xl flex-col gap-6 px-6 py-8">
        <section className="flex flex-col items-center gap-6 rounded-2xl border border-black/5 bg-white p-6 sm:flex-row">
          <div className="relative">
            <Donut correct={result.correct} wrong={wrong} skipped={skipped} />
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-3xl font-semibold text-neutral-900">{percent}%</span>
              <span className="text-[10px] tracking-widest text-neutral-400">CHÍNH XÁC</span>
            </div>
          </div>
          <dl className="grid w-full flex-1 grid-cols-3 gap-3 text-center">
            <div className="rounded-xl bg-emerald-50 px-3 py-4">
              <dt className="text-xs text-emerald-700">Đúng</dt>
              <dd className="mt-1 text-2xl font-semibold text-emerald-800">{result.correct}</dd>
            </div>
            <div className="rounded-xl bg-red-50 px-3 py-4">
              <dt className="text-xs text-red-600">Sai</dt>
              <dd className="mt-1 text-2xl font-semibold text-red-700">{wrong}</dd>
            </div>
            <div className="rounded-xl bg-[#F5F3EE] px-3 py-4">
              <dt className="text-xs text-neutral-500">Bỏ trống</dt>
              <dd className="mt-1 text-2xl font-semibold text-neutral-700">{skipped}</dd>
            </div>
          </dl>
        </section>

        {weakest.length > 0 ? (
          <section className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
            <p className="flex items-center gap-2 text-sm font-semibold text-amber-900">
              <AlertTriangle size={16} /> Phần bạn đang hổng nhất
            </p>
            <ul className="mt-3 space-y-2">
              {weakest.map((t, i) => (
                <li key={t.key} className="flex items-center justify-between gap-3 text-sm text-amber-900">
                  <span className="min-w-0 truncate">
                    {i + 1}. {t.section && SECTION_LABELS[t.section]?.vi} · {t.label}
                  </span>
                  <span className="flex shrink-0 items-center gap-3">
                    <span className="text-xs">
                      đúng {t.correct}/{t.total} ({percentOf(t)}%)
                    </span>
                    {t.firstMissed !== undefined && (
                      <button
                        type="button"
                        onClick={() => onReview(t.firstMissed!)}
                        className="flex items-center gap-1 rounded-full bg-white px-3 py-1 text-xs text-neutral-800 hover:bg-neutral-100"
                      >
                        Xem lại <ArrowRight size={12} />
                      </button>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ) : (
          result.total > 0 && (
            <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5 text-sm text-emerald-800">
              Bạn trả lời đúng tất cả các câu. Không có phần nào bị hổng.
            </section>
          )
        )}

        {bySection.length > 1 && (
          <section className="rounded-2xl border border-black/5 bg-white p-6">
            <p className="mb-4 text-[10px] tracking-widest text-neutral-400">THEO PHẦN THI</p>
            <ul className="space-y-4">
              {bySection.map((t) => (
                <BarRow key={t.key} tally={t} onReview={onReview} />
              ))}
            </ul>
          </section>
        )}

        <section className="rounded-2xl border border-black/5 bg-white p-6">
          <p className="mb-1 text-[10px] tracking-widest text-neutral-400">THEO DẠNG BÀI</p>
          <p className="mb-4 text-xs text-neutral-400">Xếp từ dạng yếu nhất đến mạnh nhất.</p>
          <ul className="space-y-4">
            {byType.map((t) => (
              <BarRow key={t.key} tally={{ ...t, label: `${t.section ? SECTION_LABELS[t.section]?.vi : ""} · ${t.label}` }} onReview={onReview} />
            ))}
          </ul>
          <div className="mt-6 overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-black/5 text-neutral-400">
                <tr>
                  <th className="py-2 pr-3 font-medium">Dạng bài</th>
                  <th className="px-2 py-2 text-right font-medium">Đúng</th>
                  <th className="px-2 py-2 text-right font-medium">Sai</th>
                  <th className="px-2 py-2 text-right font-medium">Bỏ trống</th>
                  <th className="py-2 pl-2 text-right font-medium">Tổng</th>
                </tr>
              </thead>
              <tbody>
                {byType.map((t) => (
                  <tr key={t.key} className="border-b border-black/5 last:border-0">
                    <td className="py-2 pr-3 text-neutral-700">{t.label}</td>
                    <td className="px-2 py-2 text-right text-emerald-700">{t.correct}</td>
                    <td className="px-2 py-2 text-right text-red-600">{t.wrong}</td>
                    <td className="px-2 py-2 text-right text-neutral-500">{t.skipped}</td>
                    <td className="py-2 pl-2 text-right font-medium text-neutral-800">{t.total}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  );
}
