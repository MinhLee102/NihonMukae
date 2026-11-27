"use client";

import { useEffect, useState } from "react";
import { fetchGrammarDetail, grammarLevels, type GrammarDetail } from "@/lib/api/grammar";

/** Loads one pattern by id and shows it with GrammarDetailBody. */
export function GrammarDetailView({ id, onSearch }: { id: number; onSearch?: (pattern: string) => void }) {
  const [detail, setDetail] = useState<GrammarDetail | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    setFailed(false);
    fetchGrammarDetail(id)
      .then((d) => alive && setDetail(d))
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
    };
  }, [id]);

  if (failed) return <p className="py-10 text-center text-sm text-neutral-400">Không tải được mẫu ngữ pháp này.</p>;
  if (!detail || detail.id !== id) return <p className="py-10 text-center text-sm text-neutral-400">Đang tải...</p>;
  return <GrammarDetailBody detail={detail} onSearch={onSearch} />;
}

const Label = ({ children }: { children: React.ReactNode }) => (
  <p className="mb-2 text-xs font-bold uppercase tracking-wider text-neutral-400">{children}</p>
);

/** Pattern, meaning, how to conjugate into it, explanation, notes, easily confused patterns and examples. */
export function GrammarDetailBody({ detail, onSearch }: { detail: GrammarDetail; onSearch?: (pattern: string) => void }) {
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3">
        <h1 className="font-serif text-3xl font-bold tracking-tight text-neutral-900 md:text-4xl">{detail.pattern}</h1>
        <div className="flex flex-wrap items-center gap-2 text-xs">
          {detail.reading && detail.reading !== detail.pattern.replace(/[〜～]/g, "") && (
            <span className="rounded-full bg-neutral-100 px-3 py-1 font-medium text-neutral-700">{detail.reading}</span>
          )}
          <span className="rounded-full bg-emerald-50 px-3 py-1 font-semibold text-emerald-600">{grammarLevels(detail)}</span>
          {detail.category && (
            <span className="rounded-full border border-neutral-200 bg-neutral-50 px-3 py-1 text-neutral-500">
              {detail.category}
            </span>
          )}
        </div>
        <p className="text-lg font-bold text-neutral-800">{detail.meaning}</p>
      </div>

      {detail.connection.length > 0 && (
        <div className="border-t border-neutral-100 pt-4">
          <Label>Cách chia</Label>
          <ul className="flex flex-col gap-1.5">
            {detail.connection.map((c, i) => (
              <li key={i} className="w-fit rounded-lg bg-sky-50 px-3 py-1.5 font-mono text-sm text-sky-800">
                {c}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="border-t border-neutral-100 pt-4">
        <Label>Giải thích</Label>
        <p className="whitespace-pre-line text-sm leading-relaxed text-neutral-700">{detail.explanation}</p>
        {detail.notes && (
          <p className="mt-3 rounded-xl bg-amber-50 px-3 py-2 text-sm leading-relaxed text-amber-800">
            <span className="font-semibold">Lưu ý: </span>
            {detail.notes}
          </p>
        )}
      </div>

      {detail.examples.length > 0 && (
        <div className="border-t border-neutral-100 pt-4">
          <Label>Ví dụ</Label>
          <div className="flex flex-col gap-3 border-l-2 border-neutral-200 pl-4">
            {detail.examples.map((ex, i) => (
              <div key={i} className="text-sm">
                <p className="text-neutral-800">{ex.ja}</p>
                <p className="text-xs text-neutral-500">{ex.vi}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {detail.similar.length > 0 && (
        <div className="border-t border-neutral-100 pt-4">
          <Label>Dễ nhầm với</Label>
          <ul className="flex flex-col gap-2">
            {detail.similar.map((s, i) => (
              <li key={i} className="text-sm">
                {onSearch ? (
                  <button
                    type="button"
                    onClick={() => onSearch(s.pattern)}
                    className="font-bold text-sky-700 hover:underline"
                  >
                    {s.pattern}
                  </button>
                ) : (
                  <span className="font-bold text-neutral-800">{s.pattern}</span>
                )}
                <span className="text-neutral-600">: {s.diff}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="border-t border-neutral-100 pt-3 text-[11px] text-neutral-400">
        Giải thích và ví dụ do AI soạn, có thể còn sai sót.
      </p>
    </div>
  );
}
