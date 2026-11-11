"use client";

import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { PracticeSetup } from "@/components/jlpt/PracticeSetup";

/** Reading là chế độ xem của kho đề: chỉ các dạng bài đọc hiểu (dokkai), làm bằng phiên ôn tập chung. */
export default function ReadingPage() {
  return (
    <RequireAuth>
      <div className="mx-auto max-w-5xl">
        <div className="mb-8">
          <p className="flex items-center gap-3 text-[11px] tracking-widest text-neutral-400">
            読解 <span className="h-px w-10 bg-black/10" />
          </p>
          <h1 className="mt-3 text-3xl font-semibold text-neutral-900">Đọc hiểu</h1>
          <p className="mt-2 text-sm text-neutral-400">
            Chọn cấp độ và dạng bài đọc (đoản văn, trung văn, trường văn, so sánh, tìm thông tin), rồi làm bài như một phiên luyện tập.
          </p>
        </div>
        <PracticeSetup sections={["dokkai"]} kinds={["types"]} />
      </div>
    </RequireAuth>
  );
}
