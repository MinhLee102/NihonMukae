"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ChevronLeft, Pencil, Trash2 } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { WordPopup } from "@/components/shelf/WordPopup";
import { useAuthStore } from "@/store/authStore";
import { deleteShelfItem, getShelfItem, type ShelfItemDto, type ShelfToken } from "@/lib/api/shelf";
import { getApiErrorMessage } from "@/lib/api/flashcard";

const POPUP_WIDTH = 320;

type Selected = { index: number; top: number; left: number };

function Reader() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);
  const containerRef = useRef<HTMLDivElement>(null);

  const [item, setItem] = useState<ShelfItemDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Selected | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    getShelfItem(id)
      .then(setItem)
      .catch((err) => setError(getApiErrorMessage(err, "Không tải được bài.")));
  }, [accessToken, id]);

  const remove = async () => {
    if (!window.confirm("Xóa bài này khỏi My Shelf?")) return;
    try {
      await deleteShelfItem(id);
      router.push("/shelf");
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const select = (index: number, el: HTMLElement) => {
    const container = containerRef.current;
    if (!container) return;
    const box = container.getBoundingClientRect();
    const rect = el.getBoundingClientRect();
    const left = Math.min(Math.max(rect.left - box.left, 0), Math.max(box.width - POPUP_WIDTH, 0));
    setSelected({ index, top: rect.bottom - box.top + 8, left });
  };

  const tokens = item?.tokens ?? [];

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-8 flex items-center justify-between">
        <Link href="/shelf" className="flex items-center gap-1 text-sm text-neutral-500 hover:text-neutral-800">
          <ChevronLeft size={14} />
          My Shelf
        </Link>
        {item && (
          <div className="flex items-center gap-1">
            <Link
              href={`/shelf/${id}/edit`}
              aria-label="Sửa bài"
              title="Sửa bài"
              className="rounded-full p-2 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700"
            >
              <Pencil size={18} />
            </Link>
            <button
              type="button"
              onClick={remove}
              aria-label="Xóa bài"
              title="Xóa bài"
              className="rounded-full p-2 text-neutral-400 hover:bg-red-50 hover:text-red-600"
            >
              <Trash2 size={18} />
            </button>
          </div>
        )}
      </div>

      {error && <p className="py-20 text-center text-sm text-red-600">{error}</p>}
      {!item && !error && <p className="py-20 text-center text-sm text-neutral-400">Đang tải...</p>}

      {item && (
        <div ref={containerRef} className="relative">
          <h1 className="mb-8 text-3xl leading-snug text-neutral-900">{item.title}</h1>

          <p className="whitespace-pre-wrap text-xl leading-[3] text-neutral-900">
            {tokens.map((token: ShelfToken, i) => {
              const isSelected = selected?.index === i;
              const content = token.parts.map((part, j) =>
                part.ruby ? (
                  <ruby key={j}>
                    {part.text}
                    <rt className="invisible text-[0.5em] text-sky-600 group-hover:visible">{part.ruby}</rt>
                  </ruby>
                ) : (
                  <span key={j}>{part.text}</span>
                )
              );
              if (!token.lookup) return <span key={i}>{content}</span>;
              return (
                <span
                  key={i}
                  onClick={(e) => select(i, e.currentTarget)}
                  className={`group cursor-pointer rounded transition-colors hover:bg-sky-50 ${
                    isSelected ? "bg-sky-100" : ""
                  }`}
                >
                  {content}
                </span>
              );
            })}
          </p>

          {selected && tokens[selected.index] && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setSelected(null)} />
              <div className="absolute z-20" style={{ top: selected.top, left: selected.left }}>
                <WordPopup token={tokens[selected.index]} onClose={() => setSelected(null)} />
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

export default function ShelfReaderPage() {
  return (
    <RequireAuth>
      <Reader />
    </RequireAuth>
  );
}
