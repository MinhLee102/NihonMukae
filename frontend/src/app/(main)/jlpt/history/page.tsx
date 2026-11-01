"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import { getSessions, type SessionSummaryDto } from "@/lib/api/jlpt";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString("vi-VN", { dateStyle: "medium", timeStyle: "short" });
}

function scoreText(s: SessionSummaryDto): string {
  if (s.status !== "submitted") return "Đang làm";
  if (s.mode === "exam" && s.score !== undefined && s.maxScore !== undefined) {
    return `${Number.isInteger(s.score) ? s.score : s.score.toFixed(1)}/${s.maxScore} điểm`;
  }
  return `${s.correct}/${s.total} câu đúng`;
}

function History() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const [items, setItems] = useState<SessionSummaryDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    getSessions()
      .then(setItems)
      .catch((err) => setError(getApiErrorMessage(err, "Không tải được lịch sử.")));
  }, [accessToken]);

  return (
    <div className="mx-auto max-w-3xl">
      <Link href="/jlpt" className="mb-6 flex items-center gap-1 text-sm text-neutral-500 hover:text-neutral-800">
        <ChevronLeft size={14} />
        Luyện thi JLPT
      </Link>
      <h1 className="mb-6 text-2xl font-semibold text-neutral-900">Lịch sử làm bài</h1>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {!items && !error && <p className="py-10 text-center text-sm text-neutral-400">Đang tải...</p>}
      {items && items.length === 0 && <p className="py-10 text-center text-sm text-neutral-400">Bạn chưa làm bài nào.</p>}

      <div className="flex flex-col gap-2.5">
        {items?.map((s) => {
          const inProgress = s.status !== "submitted";
          return (
            <Link
              key={s.id}
              href={inProgress ? `/jlpt/session/${s.id}` : `/jlpt/result/${s.id}`}
              className="flex items-center gap-4 rounded-2xl border border-black/5 bg-white px-5 py-4 transition hover:border-black/20"
            >
              <span
                className={`shrink-0 rounded-full px-3 py-1 text-[10px] font-medium ${
                  s.mode === "exam" ? "bg-neutral-900 text-white" : "bg-[#EFEBE2] text-neutral-600"
                }`}
              >
                {s.mode === "exam" ? "Thi thử" : "Luyện tập"}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-neutral-900">{s.title}</p>
                <p className="mt-0.5 text-xs text-neutral-400">{formatDate(s.startedAt)}</p>
              </div>
              <span className={`shrink-0 text-sm font-medium ${inProgress ? "text-amber-600" : "text-neutral-800"}`}>
                {scoreText(s)}
              </span>
              <span className="flex shrink-0 items-center gap-0.5 text-xs text-neutral-400">
                {inProgress ? "Tiếp tục" : "Xem lại"}
                <ChevronRight size={14} />
              </span>
            </Link>
          );
        })}
      </div>
    </div>
  );
}

export default function HistoryPage() {
  return (
    <RequireAuth>
      <History />
    </RequireAuth>
  );
}
