"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { fetchMyContributions, type MyContribution } from "@/lib/api/dictionary";
import type { PageResponse } from "@/lib/api/dictionary";

const KIND_LABEL = { meaning: "Nghĩa bổ sung", example: "Câu ví dụ", note: "Ghi chú" } as const;

const STATUS = {
  PENDING: { label: "Chờ duyệt", cls: "bg-amber-50 text-amber-700" },
  APPROVED: { label: "Đã duyệt", cls: "bg-emerald-50 text-emerald-700" },
  REJECTED: { label: "Không duyệt", cls: "bg-red-50 text-red-700" },
} as const;

export default function MyContributionsPage() {
  return (
    <RequireAuth message="Bạn cần đăng nhập để xem đóng góp của mình.">
      <Contributions />
    </RequireAuth>
  );
}

function Contributions() {
  const [page, setPage] = useState<PageResponse<MyContribution> | null>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPage(await fetchMyContributions(pageIndex, 20));
      setError(null);
    } catch {
      setError("Không tải được danh sách đóng góp.");
    }
  }, [pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Đóng góp của tôi</h1>
      <p className="mt-1 text-sm text-neutral-500">
        Đóng góp hiện công khai trên từ điển sau khi quản trị viên duyệt.{" "}
        <Link href="/" className="underline-offset-2 hover:underline">
          Về từ điển
        </Link>
      </p>

      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      <ul className="mt-5 flex flex-col gap-3">
        {page?.content.map((c) => (
          <li key={c.id} className="rounded-2xl border border-black/5 bg-white p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm">
                <span className="font-bold text-neutral-900">{c.headword}</span>
                <span className="ml-2 text-xs text-neutral-400">{KIND_LABEL[c.kind]}</span>
              </p>
              <span className={`rounded-full px-2.5 py-1 text-xs ${STATUS[c.status].cls}`}>{STATUS[c.status].label}</span>
            </div>
            {c.ja && <p className="mt-2 text-sm text-neutral-800">{c.ja}</p>}
            <p className="mt-1 whitespace-pre-wrap text-sm text-neutral-700">{c.vi}</p>
            {c.reviewNote && <p className="mt-2 text-xs text-neutral-400">Ghi chú của admin: {c.reviewNote}</p>}
          </li>
        ))}
        {page && page.content.length === 0 && (
          <li className="rounded-2xl border border-black/5 bg-white p-8 text-center text-sm text-neutral-400">
            Bạn chưa gửi đóng góp nào. Mở một từ trong từ điển và bấm &ldquo;Đóng góp&rdquo;.
          </li>
        )}
      </ul>

      {page && page.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-neutral-500">
          <span>
            Trang {page.page + 1}/{page.totalPages}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={pageIndex === 0}
              onClick={() => setPageIndex(pageIndex - 1)}
              className="rounded-full border border-black/10 px-4 py-1.5 disabled:opacity-40"
            >
              Trước
            </button>
            <button
              type="button"
              disabled={pageIndex + 1 >= page.totalPages}
              onClick={() => setPageIndex(pageIndex + 1)}
              className="rounded-full border border-black/10 px-4 py-1.5 disabled:opacity-40"
            >
              Sau
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
