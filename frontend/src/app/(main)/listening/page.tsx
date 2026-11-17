"use client";

import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { PracticeSetup } from "@/components/jlpt/PracticeSetup";

/** Listening: nghe nguyên một bộ đề (cả phần nghe dùng chung một file audio), dùng phiên ôn tập chung. */
export default function ListeningPage() {
  return (
    <RequireAuth>
      <div className="mx-auto max-w-5xl">
        <div className="mb-8">
          <p className="flex items-center gap-3 text-[11px] tracking-widest text-neutral-400">
            聴解 <span className="h-px w-10 bg-black/10" />
          </p>
          <h1 className="mt-3 text-3xl font-semibold text-neutral-900">Nghe hiểu</h1>
          <p className="mt-2 text-sm text-neutral-400">
            Chọn cấp độ để nghe trọn một bộ đề với file ghi âm đi kèm. Khi luyện bạn có thể tua và nghe lại.
          </p>
        </div>
        <PracticeSetup kinds={["listening"]} />
      </div>
    </RequireAuth>
  );
}
