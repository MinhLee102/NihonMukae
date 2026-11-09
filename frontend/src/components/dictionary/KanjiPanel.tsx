"use client";

import { useEffect, useState } from "react";
import { BookOpen } from "lucide-react";
import { StrokeOrder } from "@/components/dictionary/StrokeOrder";
import {
  getKanji,
  searchKanji,
  type KanjiDetailDto,
  type KanjiSummaryDto,
} from "@/lib/api/dictionary";

const clean = (s: string) => s.replace(/^◆\s*/, "").replace(/[.。]\s*$/, "");

function gradeLabel(grade: number | null | undefined) {
  if (!grade) return null;
  if (grade <= 6) return `Lớp ${grade} (tiểu học)`;
  if (grade === 8) return "Thường dụng (trung học)";
  if (grade >= 9) return "Dùng trong tên người";
  return null;
}

/** Tab Hán tự: danh sách chữ khớp bên phải, chi tiết (Hán Việt, âm on/kun, nghĩa, thứ tự nét, từ chứa chữ) bên trái. */
export function KanjiPanel({ query, onSearchWord }: { query: string; onSearchWord: (word: string) => void }) {
  const [results, setResults] = useState<KanjiSummaryDto[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<KanjiDetailDto | null>(null);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    let alive = true;
    setSearching(true);
    searchKanji(query)
      .then((list) => {
        if (!alive) return;
        setResults(list);
        setSelected(list[0]?.kanji ?? null);
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
  }, [query]);

  useEffect(() => {
    if (!selected) {
      setDetail(null);
      return;
    }
    let alive = true;
    getKanji(selected)
      .then((d) => alive && setDetail(d))
      .catch(() => alive && setDetail(null));
    return () => {
      alive = false;
    };
  }, [selected]);

  if (!searching && results.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 py-16 text-neutral-400">
        <BookOpen size={36} strokeWidth={1.5} />
        <p className="text-sm">Không tìm thấy chữ Hán nào phù hợp</p>
      </div>
    );
  }

  const grade = gradeLabel(detail?.grade);

  return (
    <div className="grid grid-cols-1 items-start gap-5 md:grid-cols-3">
      <div className="flex flex-col gap-5 rounded-2xl border border-black/5 bg-white p-6 shadow-sm md:col-span-2">
        {detail && detail.kanji === selected ? (
          <>
            <div className="flex items-start gap-6">
              <h1 className="font-serif text-7xl leading-none text-neutral-900">{detail.kanji}</h1>
              <div className="flex flex-col gap-2">
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  {detail.hanViet.map((h) => (
                    <span key={h} className="rounded-full bg-sky-50 px-3 py-1 text-sm font-bold uppercase text-sky-600">
                      {h}
                    </span>
                  ))}
                  {detail.strokeCount && (
                    <span className="rounded-full bg-neutral-100 px-3 py-1 text-neutral-600">{detail.strokeCount} nét</span>
                  )}
                  {detail.radicalChar && (
                    <span className="rounded-full bg-neutral-100 px-3 py-1 text-neutral-600">
                      Bộ {detail.radicalChar} ({detail.radical})
                    </span>
                  )}
                  {grade && <span className="rounded-full bg-emerald-50 px-3 py-1 text-emerald-700">{grade}</span>}
                  {detail.frequency && (
                    <span className="rounded-full bg-neutral-50 px-3 py-1 text-neutral-500">Thứ {detail.frequency} về độ phổ biến</span>
                  )}
                </div>
                {detail.meaningVi ? (
                  <p className="text-lg font-bold text-neutral-800">{clean(detail.meaningVi)}</p>
                ) : null}
                {detail.meaningsEn.length > 0 && (
                  <p className={detail.meaningVi ? "text-sm text-neutral-500" : "text-base text-neutral-700"}>
                    {detail.meaningsEn.join(", ")}
                    {!detail.meaningVi && <span className="ml-2 rounded bg-neutral-100 px-2 py-0.5 text-[11px] text-neutral-500">EN</span>}
                  </p>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 border-t border-neutral-100 pt-4 text-sm md:grid-cols-2">
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-neutral-400">Âm On (音読み)</p>
                <p className="mt-1 text-neutral-800">{detail.onYomi.length ? detail.onYomi.join("、") : "-"}</p>
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-neutral-400">Âm Kun (訓読み)</p>
                <p className="mt-1 text-neutral-800">{detail.kunYomi.length ? detail.kunYomi.join("、") : "-"}</p>
              </div>
            </div>

            <div className="border-t border-neutral-100 pt-4">
              <p className="mb-3 text-xs font-bold uppercase tracking-wider text-neutral-400">Thứ tự nét viết</p>
              <StrokeOrder key={detail.kanji} paths={detail.strokePaths} />
            </div>

            {detail.words.length > 0 && (
              <div className="border-t border-neutral-100 pt-4">
                <p className="mb-3 text-xs font-bold uppercase tracking-wider text-neutral-400">Từ chứa chữ này</p>
                <ul className="flex flex-col gap-1">
                  {detail.words.map((w) => (
                    <li key={w.id}>
                      <button
                        type="button"
                        onClick={() => onSearchWord(w.headword)}
                        className="flex w-full items-baseline gap-3 rounded-lg px-2 py-1.5 text-left transition hover:bg-neutral-50"
                      >
                        <span className="font-bold text-neutral-900">{w.headword}</span>
                        {w.kana !== w.headword && <span className="text-xs text-neutral-400">{w.kana}</span>}
                        <span className="line-clamp-1 text-sm text-neutral-600">
                          {w.meanings[0] ? clean(w.meanings[0].meaning) : ""}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <p className="border-t border-neutral-100 pt-3 text-[11px] text-neutral-400">
              Dữ liệu chữ Hán: KANJIDIC2 © EDRDG (CC BY-SA 4.0). Hình nét viết: KanjiVG (CC BY-SA 3.0).
            </p>
          </>
        ) : (
          <p className="py-10 text-center text-sm text-neutral-400">Đang tải...</p>
        )}
      </div>

      <div className="flex flex-col rounded-2xl border border-black/5 bg-white p-4 shadow-sm">
        <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-neutral-400">Chữ Hán ({results.length})</h3>
        <div className="flex max-h-[550px] flex-col gap-2 overflow-y-auto pr-1">
          {results.map((k) => (
            <button
              key={k.kanji}
              type="button"
              onClick={() => setSelected(k.kanji)}
              className={`flex items-center gap-3 rounded-xl p-3 text-left transition-all ${
                k.kanji === selected ? "border border-sky-200 bg-sky-50/70" : "border border-transparent hover:bg-neutral-50"
              }`}
            >
              <span className="font-serif text-3xl text-neutral-900">{k.kanji}</span>
              <span className="min-w-0">
                <span className="block text-sm font-bold uppercase text-sky-600">{k.hanViet.slice(0, 2).join(", ")}</span>
                <span className="line-clamp-1 text-xs text-neutral-500">
                  {k.meaningVi ? clean(k.meaningVi) : k.meaningsEn.slice(0, 3).join(", ")}
                </span>
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
