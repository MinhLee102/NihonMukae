"use client";

import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { AddToFlashcardButton } from "@/components/flashcard/AddToFlashcardButton";
import { lookupWord, type WordDto } from "@/lib/api/dictionary";
import type { ShelfToken } from "@/lib/api/shelf";

const KANJI = /[一-鿿々]/;

/** What to send to /dictionary/lookup for a token. */
function lookupParams(token: ShelfToken): { kanji?: string; kana?: string } {
  const surface = token.parts.map((p) => p.text).join("");
  if (!KANJI.test(token.base)) return { kana: token.base };
  // The reading only describes the surface form, so it only helps when the word is not inflected.
  return surface === token.base ? { kanji: token.base, kana: token.reading } : { kanji: token.base };
}

/** Popup shown under a clicked word: Vietnamese meanings, one example and an add-to-flashcard button. */
export function WordPopup({ token, onClose }: { token: ShelfToken; onClose: () => void }) {
  const [word, setWord] = useState<WordDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setWord(null);
    lookupWord(lookupParams(token))
      .then((res) => {
        if (!cancelled) setWord(res.data);
      })
      .catch(() => {
        if (!cancelled) setNotFound(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const example = word?.examples?.[0];
  const nameLabel = token.name === "person" ? "Tên người" : token.name === "place" ? "Địa danh" : null;

  return (
    <div className="w-[320px] rounded-2xl border border-black/5 bg-white p-4 text-left shadow-xl">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <span className="font-serif text-2xl text-neutral-900">{word?.kanji || token.base}</span>
          {word && word.kana !== (word.kanji || token.base) && (
            <span className="ml-2 text-sm text-neutral-400">{word.kana}</span>
          )}
          {!word && token.reading && <span className="ml-2 text-sm text-neutral-400">{token.reading}</span>}
          {nameLabel && (
            <p className="mt-0.5 text-[11px] font-medium uppercase tracking-widest text-amber-600">{nameLabel}</p>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {word?.jlptLevel && (
            <span className="rounded-full bg-violet-50 px-2 py-0.5 text-[11px] font-semibold text-violet-600">
              N{word.jlptLevel}
            </span>
          )}
          <button type="button" onClick={onClose} aria-label="Đóng" className="text-neutral-400 hover:text-neutral-700">
            <X size={16} />
          </button>
        </div>
      </div>

      {loading && <p className="mt-4 text-sm text-neutral-400">Đang tra...</p>}
      {notFound && (
        <p className="mt-4 text-sm text-neutral-500">
          {nameLabel ? "Tên riêng, từ điển không có nghĩa cho tên này." : "Không tìm thấy từ này trong từ điển."}
        </p>
      )}

      {word && (
        <>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px]">
            {word.pos && <span className="text-neutral-400">{word.pos}</span>}
            {word.hanViet && <span className="font-bold uppercase text-sky-600">{word.hanViet}</span>}
            {!word.hasVietnamese && <span className="text-amber-600">Chưa có bản dịch Việt, hiện nghĩa tiếng Anh</span>}
          </div>

          <ul className="mt-3 flex flex-col gap-1.5 border-t border-black/5 pt-3">
            {word.meanings.slice(0, 4).map((m, i) => (
              <li key={i} className="flex items-start gap-2 text-sm text-neutral-800">
                <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-sky-500" />
                {m.meaning.replace(/^◆\s*/, "").replace(/[.。]\s*$/, "")}
              </li>
            ))}
          </ul>

          {example && (
            <div className="mt-3 rounded-xl bg-[#F5F3EE] px-3 py-2.5 text-xs">
              <p className="text-neutral-700">{example.sentence}</p>
              {example.translation && <p className="mt-0.5 text-neutral-400">{example.translation}</p>}
            </div>
          )}

          <div className="mt-3">
            <AddToFlashcardButton wordId={word.id} variant="pill" />
          </div>
        </>
      )}
    </div>
  );
}
