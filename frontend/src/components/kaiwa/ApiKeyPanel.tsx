"use client";

import { useEffect, useState } from "react";
import { KeyRound, ShieldCheck } from "lucide-react";
import { clearApiKey, loadApiKey, saveApiKey } from "@/lib/kaiwa/gemini";

/** Mỗi người dùng tự nhập key Gemini của mình. Key chỉ lưu trong trình duyệt này. */
export function ApiKeyPanel({ onChange }: { onChange: (key: string) => void }) {
  const [saved, setSaved] = useState("");
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const key = loadApiKey();
    setSaved(key);
    onChange(key);
    // chỉ đọc key một lần khi mở trang
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const save = () => {
    const key = draft.trim();
    if (!/^[A-Za-z0-9_-]{20,}$/.test(key)) {
      setError("Key trông chưa đúng. Key Gemini thường bắt đầu bằng AIza và dài khoảng 39 ký tự.");
      return;
    }
    if (!saveApiKey(key)) {
      setError("Trình duyệt đang chặn lưu dữ liệu nên không lưu được key.");
      return;
    }
    setSaved(key);
    setDraft("");
    setError(null);
    onChange(key);
  };

  const remove = () => {
    clearApiKey();
    setSaved("");
    onChange("");
  };

  return (
    <div className="rounded-2xl border border-black/5 bg-white p-5">
      <p className="mb-1 flex items-center gap-2 text-sm font-semibold text-neutral-900">
        <KeyRound size={15} /> Key Gemini của bạn
      </p>

      {saved ? (
        <div className="mt-3 flex items-center justify-between gap-3 rounded-xl bg-[#F5F3EE] px-4 py-3">
          <span className="text-sm text-neutral-700">
            Đã lưu key <span className="font-mono text-xs">…{saved.slice(-4)}</span>
          </span>
          <button type="button" onClick={remove} className="text-xs text-neutral-500 underline hover:text-red-600">
            Xoá key
          </button>
        </div>
      ) : (
        <>
          <p className="mt-1 text-xs leading-5 text-neutral-500">
            Kaiwa dùng Gemini bằng key miễn phí của riêng bạn, nên hạn mức không dùng chung với ai. Tạo key tại{" "}
            <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener noreferrer" className="underline">
              aistudio.google.com/apikey
            </a>{" "}
            (đăng nhập Google, bấm Create API key) rồi dán vào đây. <strong>Hãy dùng key miễn phí</strong> (dự án chưa bật thanh toán) để
            không thể bị tính tiền.
          </p>
          <div className="mt-3 flex gap-2">
            <input
              type="password"
              autoComplete="off"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && save()}
              placeholder="AIza..."
              aria-label="Key Gemini"
              className="min-w-0 flex-1 rounded-lg border border-black/10 px-3 py-2 font-mono text-sm outline-none focus:border-black/40"
            />
            <button
              type="button"
              onClick={save}
              disabled={!draft.trim()}
              className="rounded-full bg-neutral-900 px-5 py-2 text-xs font-medium text-white disabled:opacity-40"
            >
              Lưu
            </button>
          </div>
          {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
        </>
      )}

      <p className="mt-3 flex items-start gap-1.5 text-[11px] leading-4 text-neutral-400">
        <ShieldCheck size={13} className="mt-px shrink-0" />
        Key chỉ lưu trong trình duyệt này và gửi thẳng tới Google, không đi qua máy chủ của Nihon Mukae. Đừng nhập key trên máy dùng chung.
      </p>
    </div>
  );
}
