"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import { createShelfItem, updateShelfItem } from "@/lib/api/shelf";

/** Add a new text (no `id`) or edit an existing one. Furigana is regenerated on every save. */
export function ShelfForm({
  id,
  initialTitle = "",
  initialContent = "",
}: {
  id?: string;
  initialTitle?: string;
  initialContent?: string;
}) {
  const router = useRouter();
  const [title, setTitle] = useState(initialTitle);
  const [content, setContent] = useState(initialContent);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const back = () => router.push(id ? `/shelf/${id}` : "/shelf");

  const save = async () => {
    if (!content.trim()) {
      setError("Hãy nhập nội dung tiếng Nhật");
      return;
    }
    try {
      setSaving(true);
      setError(null);
      const input = { title: title.trim() || undefined, content };
      const saved = id ? await updateShelfItem(id, input) : await createShelfItem(input);
      router.push(`/shelf/${saved.id}`);
    } catch (err) {
      setError(getApiErrorMessage(err));
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <button
        type="button"
        onClick={back}
        className="mb-6 flex items-center gap-1 text-sm text-neutral-500 hover:text-neutral-800"
      >
        <ChevronLeft size={14} />
        {id ? "Quay lại bài" : "My Shelf"}
      </button>

      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        maxLength={500}
        placeholder="Tiêu đề (bỏ trống sẽ lấy dòng đầu của bài)"
        className="mb-4 w-full border-b border-black/10 bg-transparent pb-3 text-2xl text-neutral-900 outline-none placeholder:text-neutral-300 focus:border-black/30"
      />
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        maxLength={20000}
        placeholder="Dán hoặc gõ đoạn văn tiếng Nhật vào đây..."
        className="min-h-[50vh] w-full resize-y rounded-2xl border border-black/5 bg-white p-6 text-lg leading-9 text-neutral-900 outline-none placeholder:text-neutral-300 focus:border-black/20"
      />

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      <div className="mt-6 flex items-center justify-center gap-3">
        <button
          type="button"
          onClick={back}
          className="rounded-full border border-black/10 px-8 py-2.5 text-sm font-medium text-neutral-600 hover:bg-neutral-50"
        >
          Hủy
        </button>
        <button
          type="button"
          onClick={save}
          disabled={saving}
          className="rounded-full bg-neutral-900 px-10 py-2.5 text-sm font-medium text-white disabled:opacity-50"
        >
          {saving ? "Đang lưu..." : "Lưu"}
        </button>
      </div>
      <p className="mt-3 text-center text-xs text-neutral-400">{content.length} / 20000 ký tự</p>
    </div>
  );
}
