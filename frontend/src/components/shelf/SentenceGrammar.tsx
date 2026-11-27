"use client";

import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import {
  detectGrammar,
  fetchGrammarDetail,
  grammarLevels,
  type GrammarDetail,
  type GrammarFound,
} from "@/lib/api/grammar";

/**
 * "Ngữ pháp trong câu" in the word popup: patterns found in the sentence around the clicked word, the ones that
 * touch the clicked word first. Tapping one shows how to attach it and an example.
 */
export function SentenceGrammar({ text, start, end }: { text: string; start: number; end: number }) {
  const [found, setFound] = useState<GrammarFound[] | null>(null);
  const [open, setOpen] = useState<number | null>(null);
  const [details, setDetails] = useState<Record<number, GrammarDetail>>({});

  useEffect(() => {
    let alive = true;
    setFound(null);
    setOpen(null);
    if (!text.trim()) return;
    detectGrammar(text)
      .then((list) => {
        if (!alive) return;
        const touches = (f: GrammarFound) => f.end > start && f.start < end;
        setFound([...list.filter(touches), ...list.filter((f) => !touches(f))]);
      })
      .catch(() => alive && setFound([]));
    return () => {
      alive = false;
    };
  }, [text, start, end]);

  const toggle = (id: number) => {
    setOpen((cur) => (cur === id ? null : id));
    if (!details[id]) {
      fetchGrammarDetail(id)
        .then((d) => setDetails((cur) => ({ ...cur, [id]: d })))
        .catch(() => undefined);
    }
  };

  if (!found || found.length === 0) return null;

  return (
    <div className="mt-4 border-t border-black/5 pt-3">
      <p className="mb-2 text-[11px] font-bold uppercase tracking-wider text-neutral-400">Ngữ pháp trong câu</p>
      <ul className="flex flex-col gap-1">
        {found.map((f) => {
          const p = f.point;
          const isOpen = open === p.id;
          const d = details[p.id];
          const touches = f.end > start && f.start < end;
          return (
            <li key={p.id} className="rounded-xl bg-[#F5F3EE]/70">
              <button
                type="button"
                onClick={() => toggle(p.id)}
                className="flex w-full items-start gap-2 px-3 py-2 text-left"
              >
                {isOpen ? (
                  <ChevronDown size={14} className="mt-1 shrink-0 text-neutral-400" />
                ) : (
                  <ChevronRight size={14} className="mt-1 shrink-0 text-neutral-400" />
                )}
                <span className="min-w-0 flex-1">
                  <span className="flex items-baseline justify-between gap-2">
                    <span className={`text-sm font-bold ${touches ? "text-sky-700" : "text-neutral-800"}`}>
                      {p.pattern}
                    </span>
                    <span className="shrink-0 text-[10px] font-semibold text-emerald-600">{grammarLevels(p)}</span>
                  </span>
                  <span className="block text-xs text-neutral-600">{p.meaning}</span>
                  <span className="block text-[11px] text-neutral-400">「{text.slice(f.start, f.end)}」</span>
                </span>
              </button>
              {isOpen && (
                <div className="px-3 pb-3 pl-8 text-xs">
                  {!d ? (
                    <p className="text-neutral-400">Đang tải...</p>
                  ) : (
                    <>
                      {d.connection.length > 0 && (
                        <div className="flex flex-col gap-1">
                          {d.connection.map((c, i) => (
                            <span key={i} className="w-fit rounded-md bg-sky-50 px-2 py-0.5 font-mono text-sky-800">
                              {c}
                            </span>
                          ))}
                        </div>
                      )}
                      <p className="mt-2 leading-relaxed text-neutral-700">{d.explanation}</p>
                      {d.examples[0] && (
                        <div className="mt-2 border-l-2 border-neutral-300 pl-2">
                          <p className="text-neutral-800">{d.examples[0].ja}</p>
                          <p className="text-neutral-500">{d.examples[0].vi}</p>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
      <p className="mt-2 text-[10px] text-neutral-400">Nhận diện tự động theo mặt chữ, có thể sót hoặc nhầm.</p>
    </div>
  );
}
