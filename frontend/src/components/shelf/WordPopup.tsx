"use client";

import { useEffect, useMemo, useState } from "react";
import { X } from "lucide-react";
import { AddToFlashcardButton } from "@/components/flashcard/AddToFlashcardButton";
import { lookupAt, type SpanLookupDto } from "@/lib/api/dictionary";
import type { ShelfToken } from "@/lib/api/shelf";
import { SentenceGrammar } from "@/components/shelf/SentenceGrammar";

const WINDOW_BEFORE = 4; // tokens before the clicked one
const WINDOW_AFTER = 10; // tokens after the clicked one
const WINDOW_MAX_CHARS = 100; // the server accepts at most 120

const surfaceOf = (token: ShelfToken) => token.parts.map((p) => p.text).join("");

/** Text around the clicked token plus the clicked character offset and each token's range inside that text. */
function buildWindow(tokens: ShelfToken[], index: number) {
  const first = Math.max(0, index - WINDOW_BEFORE);
  let text = "";
  const ranges: { start: number; end: number; index: number }[] = [];
  for (let i = first; i < Math.min(tokens.length, index + WINDOW_AFTER + 1); i++) {
    const s = surfaceOf(tokens[i]);
    if (i > index && text.length + s.length > WINDOW_MAX_CHARS) break;
    ranges.push({ start: text.length, end: text.length + s.length, index: i });
    text += s;
  }
  const clicked = ranges.find((r) => r.index === index);
  return { text, offset: clicked ? clicked.start : 0, ranges };
}

const SENTENCE_END = /[。！？!?\n]/;
const SENTENCE_MAX_CHARS = 200; // the server accepts at most 300

/** The sentence containing the clicked token, and where the clicked token sits inside it. */
function buildSentence(tokens: ShelfToken[], index: number) {
  let first = index;
  let length = surfaceOf(tokens[index]).length;
  while (first > 0 && !SENTENCE_END.test(surfaceOf(tokens[first - 1]))) {
    const s = surfaceOf(tokens[first - 1]);
    if (length + s.length > SENTENCE_MAX_CHARS / 2) break;
    length += s.length;
    first--;
  }
  let text = "";
  let start = 0;
  let end = 0;
  for (let i = first; i < tokens.length; i++) {
    const s = surfaceOf(tokens[i]);
    if (i > index && text.length + s.length > SENTENCE_MAX_CHARS) break;
    if (i === index) start = text.length;
    text += s;
    if (i === index) end = text.length;
    if (i >= index && SENTENCE_END.test(s)) break;
  }
  return { text: text.replace(/\n/g, " "), start, end };
}

/**
 * Popup shown under a clicked word. It looks up the LONGEST dictionary string containing the clicked token
 * (so 容疑者 is found even though the tokenizer splits it into 容疑 + 者) and tells the page which tokens that
 * covers, so the page can highlight the whole word.
 */
export function WordPopup({
  tokens,
  index,
  onResolved,
  onClose,
}: {
  tokens: ShelfToken[];
  index: number;
  onResolved: (from: number, to: number) => void;
  onClose: () => void;
}) {
  const token = tokens[index];
  const [result, setResult] = useState<SpanLookupDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const win = useMemo(() => buildWindow(tokens, index), [tokens, index]);
  const sentence = useMemo(() => buildSentence(tokens, index), [tokens, index]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    setResult(null);
    lookupAt(win.text, win.offset)
      .then((res) => {
        if (cancelled) return;
        setResult(res.data);
        // Round the matched range outwards to whole tokens (the server tokenizes the window on its own)
        const covered = win.ranges.filter((r) => r.end > res.data.start && r.start < res.data.end);
        if (covered.length > 0) onResolved(covered[0].index, covered[covered.length - 1].index);
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
    // onResolved is a new function on every render of the page; the lookup must only depend on what was clicked
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [win]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const word = result?.entry ?? null;
  const parts = result && result.parts.length > 1 ? result.parts : [];
  const example = word?.examples?.[0];
  const nameLabel = token.name === "person" ? "Tên người" : token.name === "place" ? "Địa danh" : null;
  const shown = word?.kanji || word?.kana || token.base;

  return (
    <div className="max-h-[75vh] w-[min(320px,calc(100vw-2rem))] overflow-y-auto rounded-2xl border border-black/5 bg-white p-4 text-left shadow-xl">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <span className="font-serif text-2xl text-neutral-900">{word ? shown : token.base}</span>
          {word && word.kana !== shown && <span className="ml-2 text-sm text-neutral-400">{word.kana}</span>}
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

          {parts.length > 0 && (
            <p className="mt-3 text-[11px] leading-relaxed text-neutral-400">
              Gồm:{" "}
              {parts.map((p, i) => (
                <span key={i}>
                  {i > 0 && " · "}
                  <span className="text-neutral-600">{p.surface}</span>
                  {p.meaning && ` ${p.meaning.replace(/^◆\s*/, "").replace(/[.。]\s*$/, "").split(/[,;，、]/)[0]}`}
                </span>
              ))}
            </p>
          )}

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

      {!loading && <SentenceGrammar text={sentence.text} start={sentence.start} end={sentence.end} />}
    </div>
  );
}
