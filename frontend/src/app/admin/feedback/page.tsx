"use client";

import { useCallback, useEffect, useState } from "react";
import {
  CATEGORY_LABELS,
  STATUS_LABELS,
  fetchFeedback,
  fetchFeedbackSummary,
  updateFeedback,
  type FeedbackCategory,
  type FeedbackRow,
  type FeedbackStatus,
} from "@/lib/api/feedback";
import type { Page } from "@/lib/api/admin";

const PAGE_SIZE = 20;
const STATUSES = Object.keys(STATUS_LABELS) as FeedbackStatus[];
const CATEGORIES = Object.keys(CATEGORY_LABELS) as FeedbackCategory[];

const STATUS_STYLE: Record<FeedbackStatus, string> = {
  new: "bg-amber-100 text-amber-800",
  seen: "bg-sky-100 text-sky-800",
  fixed: "bg-emerald-100 text-emerald-800",
  rejected: "bg-neutral-200 text-neutral-600",
};

function FeedbackCard({ row, onChanged }: { row: FeedbackRow; onChanged: () => void }) {
  const [note, setNote] = useState(row.adminNote ?? "");
  const [busy, setBusy] = useState(false);

  async function setStatus(status: FeedbackStatus) {
    try {
      setBusy(true);
      await updateFeedback(row.id, { status, adminNote: note.trim() || null });
      onChanged();
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="rounded-2xl border border-black/5 bg-white p-5">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className={`rounded-full px-2.5 py-1 font-medium ${STATUS_STYLE[row.status]}`}>{STATUS_LABELS[row.status]}</span>
        <span className="rounded-full bg-[#F5F3EE] px-2.5 py-1 text-neutral-600">{CATEGORY_LABELS[row.category]}</span>
        <span className="text-neutral-400">
          {row.userName || row.userEmail || "Người dùng đã xóa"} · {new Date(row.createdAt).toLocaleString("vi-VN")}
        </span>
      </div>

      <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-neutral-800">{row.message}</p>

      <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-neutral-400">
        {row.pageUrl && <span>Trang: {row.pageUrl}</span>}
        {row.context?.questionId !== undefined && <span>Câu hỏi #{row.context.questionId}</span>}
        {row.context?.sessionId && (
          <a href={`/jlpt/result/${row.context.sessionId}`} target="_blank" rel="noreferrer" className="underline hover:text-neutral-700">
            Xem phiên làm bài
          </a>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={1000}
          placeholder="Ghi chú xử lý (không bắt buộc)"
          className="min-w-[200px] flex-1 rounded-full border border-black/10 bg-white px-4 py-1.5 text-xs outline-none focus:border-black/30"
        />
        {STATUSES.filter((s) => s !== row.status).map((s) => (
          <button
            key={s}
            type="button"
            disabled={busy}
            onClick={() => void setStatus(s)}
            className="rounded-full border border-black/10 px-3.5 py-1.5 text-xs text-neutral-600 transition hover:border-black/30 disabled:opacity-50"
          >
            {STATUS_LABELS[s]}
          </button>
        ))}
      </div>
    </li>
  );
}

export default function AdminFeedbackPage() {
  const [status, setStatus] = useState<string | undefined>("new");
  const [category, setCategory] = useState<string | undefined>();
  const [pageIndex, setPageIndex] = useState(0);
  const [page, setPage] = useState<Page<FeedbackRow> | null>(null);
  const [summary, setSummary] = useState<Record<FeedbackStatus, number> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [p, s] = await Promise.all([
        fetchFeedback({ status, category, page: pageIndex, size: PAGE_SIZE }),
        fetchFeedbackSummary(),
      ]);
      setPage(p);
      setSummary(s);
      setError(null);
    } catch {
      setError("Không tải được danh sách góp ý.");
    }
  }, [status, category, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  const chip = (active: boolean) =>
    `rounded-full px-3.5 py-1.5 text-sm transition ${active ? "bg-neutral-900 text-white" : "text-neutral-500 hover:bg-black/5"}`;

  return (
    <div>
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Góp ý của người dùng</h1>

      <div className="mt-6 flex flex-wrap gap-1">
        <button type="button" className={chip(status === undefined)} onClick={() => { setStatus(undefined); setPageIndex(0); }}>
          Tất cả
        </button>
        {STATUSES.map((s) => (
          <button key={s} type="button" className={chip(status === s)} onClick={() => { setStatus(s); setPageIndex(0); }}>
            {STATUS_LABELS[s]} <span className="opacity-60">{summary?.[s] ?? 0}</span>
          </button>
        ))}
      </div>
      <div className="mt-2 flex flex-wrap gap-1">
        <button type="button" className={chip(category === undefined)} onClick={() => { setCategory(undefined); setPageIndex(0); }}>
          Mọi loại
        </button>
        {CATEGORIES.map((c) => (
          <button key={c} type="button" className={chip(category === c)} onClick={() => { setCategory(c); setPageIndex(0); }}>
            {CATEGORY_LABELS[c]}
          </button>
        ))}
      </div>

      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      <ul className="mt-5 flex flex-col gap-3">
        {page?.content.map((row) => (
          <FeedbackCard key={`${row.id}-${row.status}`} row={row} onChanged={() => void load()} />
        ))}
        {page && page.content.length === 0 && (
          <li className="rounded-2xl border border-black/5 bg-white px-5 py-10 text-center text-sm text-neutral-400">
            Không có góp ý nào.
          </li>
        )}
      </ul>

      {page && page.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-neutral-500">
          <span>
            Trang {page.page + 1}/{page.totalPages} ({page.totalElements} góp ý)
          </span>
          <div className="flex gap-2">
            <button type="button" disabled={pageIndex === 0} onClick={() => setPageIndex(pageIndex - 1)} className="rounded-full border border-black/10 px-4 py-1.5 disabled:opacity-40">
              Trước
            </button>
            <button type="button" disabled={pageIndex + 1 >= page.totalPages} onClick={() => setPageIndex(pageIndex + 1)} className="rounded-full border border-black/10 px-4 py-1.5 disabled:opacity-40">
              Sau
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
