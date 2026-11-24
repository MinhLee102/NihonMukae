"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { X } from "lucide-react";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import { createShadowing } from "@/lib/api/shadowing";
import { extractVideoId, formatClock, parseTranscript } from "@/lib/shadowing/parseTranscript";

const FORMAT_LABEL = { srt: "SRT", vtt: "VTT", youtube: "bản chép lời YouTube", none: "" } as const;

/** Thêm bài: link YouTube + phụ đề có mốc giờ (dán bản chép lời của YouTube, hoặc file SRT/VTT). */
export function AddMaterialDialog({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const [link, setLink] = useState("");
  const [title, setTitle] = useState("");
  const [raw, setRaw] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const videoId = useMemo(() => extractVideoId(link), [link]);
  const parsed = useMemo(() => parseTranscript(raw), [raw]);

  const onFile = async (file: File | undefined) => {
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      setError("File quá lớn (tối đa 2 MB).");
      return;
    }
    setRaw(await file.text());
    if (!title) setTitle(file.name.replace(/\.[^.]+$/, ""));
  };

  const save = async () => {
    if (!videoId) return setError("Link YouTube chưa đúng.");
    if (!title.trim()) return setError("Hãy đặt tiêu đề cho bài.");
    if (parsed.segments.length === 0) return setError("Chưa đọc được phụ đề có mốc giờ.");
    try {
      setSaving(true);
      setError(null);
      const created = await createShadowing({ videoId, title: title.trim(), segments: parsed.segments });
      router.push(`/shadowing/${created.id}`);
    } catch (err) {
      setError(getApiErrorMessage(err));
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" role="dialog" aria-modal="true">
      <div className="flex max-h-[90dvh] w-full max-w-xl flex-col rounded-2xl bg-white p-4 shadow-xl sm:p-6">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-neutral-900">Thêm bài shadowing</h2>
          <button type="button" onClick={onClose} aria-label="Đóng" className="text-neutral-400 hover:text-neutral-700">
            <X size={18} />
          </button>
        </div>

        <div className="space-y-4 overflow-y-auto pr-1">
          <label className="block">
            <span className="text-xs font-medium text-neutral-700">Link video YouTube</span>
            <input
              value={link}
              onChange={(e) => setLink(e.target.value)}
              placeholder="https://www.youtube.com/watch?v=..."
              className="mt-1 w-full rounded-lg border border-black/10 px-3 py-2 text-sm outline-none focus:border-black/40"
            />
            {link && !videoId && <span className="mt-1 block text-[11px] text-red-600">Không nhận ra link này.</span>}
          </label>

          {videoId && (
            <div className="aspect-video w-full overflow-hidden rounded-xl bg-black">
              <iframe
                src={`https://www.youtube-nocookie.com/embed/${videoId}`}
                title="Xem trước video"
                allow="encrypted-media"
                className="h-full w-full"
              />
            </div>
          )}

          <label className="block">
            <span className="text-xs font-medium text-neutral-700">Tiêu đề</span>
            <input
              value={title}
              maxLength={200}
              onChange={(e) => setTitle(e.target.value)}
              className="mt-1 w-full rounded-lg border border-black/10 px-3 py-2 text-sm outline-none focus:border-black/40"
            />
          </label>

          <div>
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-neutral-700">Phụ đề có mốc giờ</span>
              <label className="cursor-pointer text-xs text-neutral-500 underline">
                Mở file SRT/VTT
                <input type="file" accept=".srt,.vtt,.txt" className="hidden" onChange={(e) => onFile(e.target.files?.[0])} />
              </label>
            </div>
            <textarea
              value={raw}
              onChange={(e) => setRaw(e.target.value)}
              rows={7}
              placeholder={"Dán bản chép lời vào đây, ví dụ:\n0:03\nこんにちは\n0:06\n今日はいい天気ですね"}
              className="mt-1 w-full resize-y rounded-lg border border-black/10 px-3 py-2 font-mono text-xs outline-none focus:border-black/40"
            />
            <p className="mt-1 text-[11px] leading-4 text-neutral-400">
              Trên YouTube: mở video, bấm &ldquo;...more&rdquo; rồi &ldquo;Show transcript&rdquo;, bôi đen toàn bộ bản chép lời,
              sao chép và dán vào đây. Nếu bản chép lời không có mốc giờ, bấm &ldquo;Toggle timestamps&rdquo; để bật rồi chép lại.
            </p>
            {raw.trim() !== "" && (
              <p className={`mt-1.5 text-xs ${parsed.segments.length > 0 ? "text-emerald-700" : "text-red-600"}`}>
                {parsed.segments.length > 0
                  ? `Đọc được ${parsed.segments.length} câu (${FORMAT_LABEL[parsed.format]}), từ ${formatClock(parsed.segments[0].start)} đến ${formatClock(parsed.segments[parsed.segments.length - 1].end)}.`
                  : "Không thấy mốc giờ trong văn bản này. Cần bản chép lời có mốc giờ hoặc file SRT/VTT."}
              </p>
            )}
          </div>
        </div>

        {error && <p className="mt-3 text-xs text-red-600">{error}</p>}

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-full border border-black/10 px-4 py-2 text-xs text-neutral-600">
            Huỷ
          </button>
          <button
            type="button"
            onClick={save}
            disabled={saving || !videoId || parsed.segments.length === 0}
            className="rounded-full bg-neutral-900 px-5 py-2 text-xs font-medium text-white disabled:opacity-40"
          >
            {saving ? "Đang lưu..." : "Lưu và bắt đầu"}
          </button>
        </div>
      </div>
    </div>
  );
}
