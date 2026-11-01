"use client";

import { useState } from "react";
import Link from "next/link";
import { History } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { ExamPicker } from "@/components/jlpt/ExamPicker";
import { ModeDialog, type JlptMode } from "@/components/jlpt/ModeDialog";
import { PracticeSetup } from "@/components/jlpt/PracticeSetup";

function JlptHome() {
  // Opening JLPT from the sidebar always asks for a mode first
  const [mode, setMode] = useState<JlptMode | null>(null);
  const [dialogOpen, setDialogOpen] = useState(true);

  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-8 flex items-start justify-between gap-4">
        <div>
          <p className="flex items-center gap-3 text-[11px] tracking-widest text-neutral-400">
            試験対策 <span className="h-px w-10 bg-black/10" />
          </p>
          <h1 className="mt-3 text-3xl font-semibold text-neutral-900">Luyện thi JLPT</h1>
          <p className="mt-2 text-sm text-neutral-400">
            Làm đề trọn bộ hoặc thiết kế một phiên luyện tập theo mục tiêu của bạn.
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <Link
            href="/jlpt/history"
            className="flex items-center gap-1.5 rounded-full border border-black/10 bg-white px-4 py-2 text-xs text-neutral-600 hover:border-black/30"
          >
            <History size={14} />
            Lịch sử
          </Link>
          <button
            type="button"
            onClick={() => setDialogOpen(true)}
            className="rounded-full border border-black/10 bg-white px-4 py-2 text-xs text-neutral-600 hover:border-black/30"
          >
            Đổi chế độ
          </button>
        </div>
      </div>

      {mode === "exam" && <ExamPicker />}
      {mode === "practice" && <PracticeSetup />}

      {dialogOpen && (
        <ModeDialog
          onSelect={(m) => {
            setMode(m);
            setDialogOpen(false);
          }}
          onClose={mode ? () => setDialogOpen(false) : undefined}
        />
      )}
    </div>
  );
}

export default function JlptPage() {
  return (
    <RequireAuth>
      <JlptHome />
    </RequireAuth>
  );
}
