"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileText, Plus } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { useAuthStore } from "@/store/authStore";
import { getShelfItems, type ShelfItemDto } from "@/lib/api/shelf";
import { getApiErrorMessage } from "@/lib/api/flashcard";

function ShelfList() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const [items, setItems] = useState<ShelfItemDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    (async () => {
      try {
        setItems(await getShelfItems());
      } catch (err) {
        setError(getApiErrorMessage(err, "Không tải được My Shelf."));
      } finally {
        setLoading(false);
      }
    })();
  }, [accessToken]);

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="mb-8 text-3xl font-semibold text-neutral-900">My Shelf</h1>

      {loading && <p className="py-10 text-center text-sm text-neutral-400">Đang tải...</p>}
      {error && <p className="py-4 text-center text-sm text-red-600">{error}</p>}

      {!loading && (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <Link
            href="/shelf/new"
            className="flex h-44 flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-black/10 text-neutral-500 transition hover:border-black/25 hover:text-neutral-800"
          >
            <Plus size={22} />
            <span className="text-xs font-medium tracking-wide">THÊM BÀI MỚI</span>
          </Link>

          {items.map((item) => (
            <Link
              key={item.id}
              href={`/shelf/${item.id}`}
              className="flex h-44 flex-col rounded-2xl border border-black/5 bg-white p-4 transition hover:border-black/15 hover:shadow-sm"
            >
              <div className="mb-3 flex h-8 w-8 items-center justify-center rounded-lg bg-[#EFE9DD] text-neutral-600">
                <FileText size={16} />
              </div>
              <p className="line-clamp-2 text-sm font-medium text-neutral-900">{item.title}</p>
              <p className="mt-1 line-clamp-3 text-xs text-neutral-400">{item.excerpt}</p>
            </Link>
          ))}
        </div>
      )}

      {!loading && items.length === 0 && !error && (
        <p className="mt-8 text-center text-sm text-neutral-400">
          Chưa có bài nào. Dán một đoạn văn tiếng Nhật để bắt đầu.
        </p>
      )}
    </div>
  );
}

export default function ShelfPage() {
  return (
    <RequireAuth>
      <ShelfList />
    </RequireAuth>
  );
}
