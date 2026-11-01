"use client";

import { useState } from "react";
import { X } from "lucide-react";
import { createDeck, getApiErrorMessage, type DeckDto } from "@/lib/api/flashcard";

const COLORS = ["#1A1A1A", "#C0392B", "#D68910", "#1E8449", "#2874A6", "#7D3C98"];

export function DeckFormModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (deck: DeckDto) => void;
}) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [color, setColor] = useState(COLORS[0]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError("Vui lòng nhập tên bộ flashcard");
      return;
    }
    try {
      setSaving(true);
      setError(null);
      const deck = await createDeck({ name: name.trim(), description: description.trim() || undefined, color });
      onCreated(deck);
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 px-4">
      <form onSubmit={submit} className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-neutral-900">Tạo bộ mới</h2>
          <button type="button" onClick={onClose} aria-label="Đóng" className="text-neutral-400 hover:text-neutral-700">
            <X size={18} />
          </button>
        </div>

        <label className="mb-1 block text-sm text-neutral-600">Tên bộ</label>
        <input
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={200}
          className="mb-4 w-full rounded-xl border border-black/10 px-3 py-2 text-sm outline-none focus:border-black/30"
          placeholder="Ví dụ: JLPT N5 Từ vựng"
        />

        <label className="mb-1 block text-sm text-neutral-600">Mô tả</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          className="mb-4 w-full resize-none rounded-xl border border-black/10 px-3 py-2 text-sm outline-none focus:border-black/30"
        />

        <label className="mb-2 block text-sm text-neutral-600">Màu</label>
        <div className="mb-5 flex gap-2">
          {COLORS.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setColor(c)}
              aria-label={c}
              style={{ backgroundColor: c }}
              className={`h-7 w-7 rounded-full ${color === c ? "ring-2 ring-offset-2 ring-neutral-400" : ""}`}
            />
          ))}
        </div>

        {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-full px-4 py-2 text-sm text-neutral-600">
            Hủy
          </button>
          <button
            type="submit"
            disabled={saving}
            className="rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {saving ? "Đang tạo..." : "Tạo bộ"}
          </button>
        </div>
      </form>
    </div>
  );
}
