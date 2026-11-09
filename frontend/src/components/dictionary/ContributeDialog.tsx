"use client";

import { useState } from "react";
import Link from "next/link";
import { X } from "lucide-react";
import { submitContribution, type ContributionKind, type WordDto } from "@/lib/api/dictionary";
import { getApiErrorMessage } from "@/lib/api/flashcard";

const KINDS: { id: ContributionKind; label: string; hint: string }[] = [
  { id: "meaning", label: "Nghĩa bổ sung", hint: "Một nghĩa tiếng Việt còn thiếu hoặc diễn đạt hay hơn." },
  { id: "example", label: "Câu ví dụ", hint: "Một câu tiếng Nhật tự nhiên kèm bản dịch tiếng Việt." },
  { id: "note", label: "Ghi chú", hint: "Cách dùng, sắc thái, mẹo nhớ, lưu ý phân biệt với từ gần nghĩa." },
];

const field =
  "w-full rounded-xl border border-black/10 bg-white px-4 py-2.5 text-sm outline-none focus:border-black/30";

/** Gửi đóng góp cho một từ. Nội dung ở trạng thái chờ duyệt, chỉ hiện công khai sau khi admin duyệt. */
export function ContributeDialog({ word, onClose }: { word: WordDto; onClose: () => void }) {
  const [kind, setKind] = useState<ContributionKind>("meaning");
  const [ja, setJa] = useState("");
  const [vi, setVi] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const current = KINDS.find((k) => k.id === kind)!;

  async function send() {
    try {
      setSaving(true);
      setError(null);
      await submitContribution(word.id, { kind, ja: kind === "example" ? ja : undefined, vi });
      setSent(true);
    } catch (err) {
      setError(getApiErrorMessage(err, "Không gửi được đóng góp."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        className="flex max-h-[90vh] w-full max-w-lg flex-col rounded-2xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between border-b border-black/5 px-6 py-4">
          <div>
            <h2 className="font-serif text-lg font-semibold text-neutral-900">Đóng góp cho từ này</h2>
            <p className="text-sm text-neutral-500">
              {word.headword}
              {word.kana !== word.headword && <span className="ml-2 text-neutral-400">{word.kana}</span>}
            </p>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng" className="rounded-full p-1.5 text-neutral-400 hover:bg-neutral-100">
            <X size={16} />
          </button>
        </div>

        {sent ? (
          <div className="px-6 py-8 text-center">
            <p className="font-medium text-neutral-900">Cảm ơn bạn đã đóng góp.</p>
            <p className="mt-1 text-sm text-neutral-500">
              Nội dung sẽ hiện công khai, kèm tên bạn, sau khi quản trị viên duyệt.
            </p>
            <div className="mt-5 flex justify-center gap-2">
              <Link
                href="/contributions"
                className="rounded-full border border-black/10 px-5 py-2 text-sm text-neutral-600 hover:bg-neutral-50"
              >
                Đóng góp của tôi
              </Link>
              <button type="button" onClick={onClose} className="rounded-full bg-neutral-900 px-6 py-2 text-sm font-medium text-white">
                Xong
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="flex-1 space-y-4 overflow-y-auto px-6 py-5">
              <div className="flex gap-1">
                {KINDS.map((k) => (
                  <button
                    key={k.id}
                    type="button"
                    onClick={() => setKind(k.id)}
                    className={`rounded-full px-3.5 py-1.5 text-sm transition ${
                      kind === k.id ? "bg-neutral-900 text-white" : "text-neutral-500 hover:bg-black/5"
                    }`}
                  >
                    {k.label}
                  </button>
                ))}
              </div>
              <p className="text-xs text-neutral-400">{current.hint}</p>

              {kind === "example" && (
                <label className="block">
                  <span className="text-xs font-medium text-neutral-500">Câu tiếng Nhật</span>
                  <textarea
                    value={ja}
                    onChange={(e) => setJa(e.target.value)}
                    rows={2}
                    maxLength={300}
                    className={`${field} mt-1 resize-y`}
                    placeholder={`例: ${word.headword}を使った文`}
                  />
                </label>
              )}

              <label className="block">
                <span className="text-xs font-medium text-neutral-500">
                  {kind === "example" ? "Bản dịch tiếng Việt" : kind === "meaning" ? "Nghĩa tiếng Việt" : "Nội dung ghi chú"}
                </span>
                <textarea
                  value={vi}
                  onChange={(e) => setVi(e.target.value)}
                  rows={kind === "note" ? 5 : 3}
                  maxLength={kind === "note" ? 1000 : kind === "meaning" ? 300 : 500}
                  className={`${field} mt-1 resize-y`}
                />
              </label>

              <p className="text-[11px] leading-relaxed text-neutral-400">
                Bằng việc gửi, bạn đồng ý để nội dung này được chia sẻ theo giấy phép CC BY-SA 4.0 cùng dữ liệu từ điển.
                Đừng chép từ từ điển hoặc sách có bản quyền.
              </p>
              {error && <p className="text-xs text-red-600">{error}</p>}
            </div>

            <div className="flex justify-end gap-2 border-t border-black/5 px-6 py-4">
              <button type="button" onClick={onClose} className="rounded-full border border-black/10 px-5 py-2 text-sm text-neutral-600">
                Hủy
              </button>
              <button
                type="button"
                disabled={saving || !vi.trim() || (kind === "example" && !ja.trim())}
                onClick={() => void send()}
                className="rounded-full bg-neutral-900 px-6 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {saving ? "Đang gửi..." : "Gửi đóng góp"}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
