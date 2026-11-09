"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, Trash2, X } from "lucide-react";
import {
  deleteDictContribution,
  fetchDictContributions,
  reviewDictContribution,
  type DictContributionRow,
  type Page,
} from "@/lib/api/admin";
import { getApiErrorMessage } from "@/lib/api/flashcard";

const PAGE_SIZE = 20;

const FILTERS = [
  { id: "PENDING", label: "Chờ duyệt" },
  { id: "APPROVED", label: "Đã duyệt" },
  { id: "REJECTED", label: "Không duyệt" },
  { id: "", label: "Tất cả" },
];

const KIND_LABEL = { meaning: "Nghĩa bổ sung", example: "Câu ví dụ", note: "Ghi chú" } as const;

const STATUS = {
  PENDING: "bg-amber-50 text-amber-700",
  APPROVED: "bg-emerald-50 text-emerald-700",
  REJECTED: "bg-red-50 text-red-700",
} as const;

export default function AdminContributionsPage() {
  const [page, setPage] = useState<Page<DictContributionRow> | null>(null);
  const [status, setStatus] = useState("PENDING");
  const [pageIndex, setPageIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPage(await fetchDictContributions({ status: status || undefined, page: pageIndex, size: PAGE_SIZE }));
      setError(null);
    } catch {
      setError("Không tải được danh sách đóng góp.");
    }
  }, [status, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  async function review(c: DictContributionRow, next: "APPROVED" | "REJECTED") {
    let note: string | undefined;
    if (next === "REJECTED") {
      const answer = window.prompt("Lý do không duyệt (người đóng góp sẽ thấy, có thể bỏ trống):", "");
      if (answer === null) return;
      note = answer;
    }
    try {
      await reviewDictContribution(c.id, next, note);
      await load();
    } catch (err) {
      setError(getApiErrorMessage(err, "Không xử lý được đóng góp này."));
    }
  }

  async function remove(c: DictContributionRow) {
    if (!window.confirm("Xoá hẳn đóng góp này? Nếu đã duyệt, nó cũng biến mất khỏi từ điển.")) return;
    try {
      await deleteDictContribution(c.id);
      await load();
    } catch (err) {
      setError(getApiErrorMessage(err, "Không xoá được."));
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Đóng góp từ điển</h1>
      <p className="mt-1 text-sm text-neutral-500">Nghĩa, câu ví dụ và ghi chú do người dùng gửi. Chỉ hiện công khai sau khi duyệt.</p>

      <div className="mt-6 flex gap-1">
        {FILTERS.map((f) => (
          <button
            key={f.id}
            type="button"
            onClick={() => {
              setStatus(f.id);
              setPageIndex(0);
            }}
            className={`rounded-full px-3.5 py-1.5 text-sm transition ${
              status === f.id ? "bg-neutral-900 text-white" : "text-neutral-500 hover:bg-black/5"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      <ul className="mt-4 flex flex-col gap-3">
        {page?.content.map((c) => (
          <li key={c.id} className="rounded-2xl border border-black/5 bg-white p-4">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <p className="text-sm">
                  <span className="font-bold text-neutral-900">{c.headword}</span>
                  {c.reading !== c.headword && <span className="ml-2 text-neutral-400">{c.reading}</span>}
                  <span className="ml-2 rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500">
                    {KIND_LABEL[c.kind]}
                  </span>
                </p>
                <p className="mt-0.5 text-xs text-neutral-400">
                  {c.userName} ({c.userEmail}) · {new Date(c.createdAt).toLocaleString("vi-VN")}
                </p>
              </div>
              <span className={`rounded-full px-2.5 py-1 text-xs ${STATUS[c.status]}`}>
                {c.status === "PENDING" ? "Chờ duyệt" : c.status === "APPROVED" ? "Đã duyệt" : "Không duyệt"}
              </span>
            </div>
            {c.ja && <p className="mt-3 text-sm text-neutral-800">{c.ja}</p>}
            <p className="mt-1 whitespace-pre-wrap text-sm text-neutral-700">{c.vi}</p>
            {c.reviewNote && <p className="mt-2 text-xs text-neutral-400">Ghi chú: {c.reviewNote}</p>}

            <div className="mt-3 flex items-center gap-2">
              {c.status !== "APPROVED" && (
                <button
                  type="button"
                  onClick={() => void review(c, "APPROVED")}
                  className="flex items-center gap-1.5 rounded-full bg-emerald-600 px-4 py-1.5 text-sm font-medium text-white"
                >
                  <Check size={14} /> Duyệt
                </button>
              )}
              {c.status !== "REJECTED" && (
                <button
                  type="button"
                  onClick={() => void review(c, "REJECTED")}
                  className="flex items-center gap-1.5 rounded-full border border-black/10 px-4 py-1.5 text-sm text-neutral-600 hover:bg-neutral-50"
                >
                  <X size={14} /> Không duyệt
                </button>
              )}
              <button
                type="button"
                onClick={() => void remove(c)}
                title="Xoá"
                className="ml-auto rounded-full p-2 text-neutral-400 hover:bg-black/5 hover:text-red-600"
              >
                <Trash2 size={15} />
              </button>
            </div>
          </li>
        ))}
        {page && page.content.length === 0 && (
          <li className="rounded-2xl border border-black/5 bg-white p-8 text-center text-sm text-neutral-400">
            Không có đóng góp nào.
          </li>
        )}
      </ul>

      {page && page.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-neutral-500">
          <span>
            Trang {page.page + 1}/{page.totalPages} ({page.totalElements} đóng góp)
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
