"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { BookOpen } from "lucide-react";
import { GrammarDetailView } from "@/components/grammar/GrammarDetailView";
import { grammarLevels, searchGrammar, type GrammarSummary } from "@/lib/api/grammar";

const LEVELS = [5, 4, 3, 2, 1];

/** Tab Ngữ pháp của Từ điển: lọc theo cấp, danh sách mẫu khớp bên phải, chi tiết bên trái. */
export function GrammarPanel({ query, onSearch }: { query: string; onSearch: (pattern: string) => void }) {
  const [level, setLevel] = useState<number | undefined>(undefined);
  const [results, setResults] = useState<GrammarSummary[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    let alive = true;
    setSearching(true);
    searchGrammar(query, level)
      .then((list) => {
        if (!alive) return;
        setResults(list);
        setSelected(list[0]?.id ?? null);
      })
      .catch(() => {
        if (alive) {
          setResults([]);
          setSelected(null);
        }
      })
      .finally(() => alive && setSearching(false));
    return () => {
      alive = false;
    };
  }, [query, level]);

  const filter = (
    <div className="flex flex-wrap items-center gap-2 text-xs">
      {[undefined, ...LEVELS].map((l) => (
        <button
          key={l ?? "all"}
          type="button"
          onClick={() => setLevel(l)}
          className={`rounded-full px-3 py-1 transition ${
            level === l ? "bg-neutral-900 text-white" : "border border-black/10 text-neutral-600 hover:bg-neutral-50"
          }`}
        >
          {l ? `N${l}` : "Mọi cấp"}
        </button>
      ))}
      <Link href="/grammar" className="ml-auto text-neutral-400 hover:text-neutral-600 hover:underline">
        Xem theo cấp độ
      </Link>
    </div>
  );

  if (!searching && results.length === 0) {
    return (
      <section className="flex flex-col gap-3">
        {filter}
        <div className="flex flex-col items-center justify-center gap-2 py-16 text-neutral-400">
          <BookOpen size={36} strokeWidth={1.5} />
          <p className="text-sm">Không tìm thấy mẫu ngữ pháp nào phù hợp</p>
        </div>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-3">
      {filter}
      <div className="grid grid-cols-1 items-start gap-5 md:grid-cols-3">
        <div className="rounded-2xl border border-black/5 bg-white p-6 shadow-sm md:col-span-2">
          {selected ? (
            <GrammarDetailView id={selected} onSearch={onSearch} />
          ) : (
            <p className="py-10 text-center text-sm text-neutral-400">Đang tìm...</p>
          )}
        </div>

        <div className="flex flex-col rounded-2xl border border-black/5 bg-white p-4 shadow-sm">
          <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-neutral-400">Mẫu ngữ pháp ({results.length})</h3>
          <div className="flex max-h-[550px] flex-col gap-2 overflow-y-auto pr-1">
            {results.map((g) => (
              <button
                key={g.id}
                type="button"
                onClick={() => setSelected(g.id)}
                className={`flex flex-col items-start gap-0.5 rounded-xl p-3 text-left transition-all ${
                  g.id === selected ? "border border-sky-200 bg-sky-50/70" : "border border-transparent hover:bg-neutral-50"
                }`}
              >
                <span className="flex w-full items-baseline justify-between gap-2">
                  <span className="font-bold text-neutral-900">{g.pattern}</span>
                  <span className="shrink-0 text-[11px] font-semibold text-emerald-600">{grammarLevels(g)}</span>
                </span>
                <span className="line-clamp-1 text-xs text-neutral-500">{g.meaning}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
