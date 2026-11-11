"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Plus, Trash2 } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { AddMaterialDialog } from "@/components/shadowing/AddMaterialDialog";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import { deleteShadowing, listShadowing, type ShadowingSummary } from "@/lib/api/shadowing";

function ShadowingHome() {
  const [items, setItems] = useState<ShadowingSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const load = useCallback(() => {
    listShadowing()
      .then(setItems)
      .catch((err) => setError(getApiErrorMessage(err, "Không tải được danh sách bài.")));
  }, []);

  useEffect(load, [load]);

  const remove = async (item: ShadowingSummary) => {
    if (!window.confirm(`Xoá bài "${item.title}"? Các câu đã đánh dấu cũng mất.`)) return;
    try {
      await deleteShadowing(item.id);
      setItems((prev) => prev?.filter((i) => i.id !== item.id) ?? null);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <div className="mb-8 flex items-start justify-between gap-4">
        <div>
          <p className="flex items-center gap-3 text-[11px] tracking-widest text-neutral-400">
            発音 <span className="h-px w-10 bg-black/10" />
          </p>
          <h1 className="mt-3 text-3xl font-semibold text-neutral-900">Shadowing</h1>
          <p className="mt-2 text-sm text-neutral-400">
            Dán link YouTube và phụ đề, nghe rồi nói theo từng câu, thu âm và đánh dấu những câu còn yếu để luyện lại.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="flex shrink-0 items-center gap-1.5 rounded-full bg-neutral-900 px-4 py-2 text-xs font-medium text-white"
        >
          <Plus size={14} />
          Thêm bài
        </button>
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
      {items === null && !error && <p className="py-10 text-center text-sm text-neutral-400">Đang tải...</p>}

      {items?.length === 0 && (
        <div className="rounded-2xl border border-dashed border-black/15 bg-white p-10 text-center">
          <p className="text-sm text-neutral-600">Bạn chưa có bài nào.</p>
          <p className="mt-1 text-xs text-neutral-400">
            Bấm &ldquo;Thêm bài&rdquo;, dán link video và bản chép lời (Show transcript) của video đó.
          </p>
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {items?.map((item) => (
          <div key={item.id} className="group relative rounded-2xl border border-black/5 bg-white p-4">
            <Link href={`/shadowing/${item.id}`} className="flex gap-3">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={`https://i.ytimg.com/vi/${item.videoId}/mqdefault.jpg`}
                alt=""
                className="h-16 w-28 shrink-0 rounded-lg bg-neutral-100 object-cover"
              />
              <div className="min-w-0">
                <p className="line-clamp-2 text-sm font-medium text-neutral-900">{item.title}</p>
                <p className="mt-1 text-[11px] text-neutral-400">
                  {item.segmentCount} câu
                  {item.markedCount > 0 ? ` · ${item.markedCount} câu cần luyện` : ""}
                </p>
                {item.lastPracticedAt && (
                  <p className="text-[11px] text-neutral-400">
                    Luyện lần cuối {new Date(item.lastPracticedAt).toLocaleDateString("vi-VN")}
                  </p>
                )}
              </div>
            </Link>
            <button
              type="button"
              onClick={() => remove(item)}
              aria-label={`Xoá ${item.title}`}
              className="absolute right-3 top-3 rounded-full bg-white/90 p-1.5 text-neutral-400 opacity-0 shadow-sm transition hover:text-red-600 group-hover:opacity-100 focus:opacity-100"
            >
              <Trash2 size={14} />
            </button>
          </div>
        ))}
      </div>

      {adding && <AddMaterialDialog onClose={() => setAdding(false)} />}
    </div>
  );
}

export default function ShadowingPage() {
  return (
    <RequireAuth message="Bạn cần đăng nhập để dùng Shadowing.">
      <ShadowingHome />
    </RequireAuth>
  );
}
