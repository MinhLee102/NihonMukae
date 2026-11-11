"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, Sparkles, Trash2 } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { ApiKeyPanel } from "@/components/kaiwa/ApiKeyPanel";
import { ChatView } from "@/components/kaiwa/ChatView";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import { deleteKaiwaSession, listKaiwaSessions, type KaiwaSessionDto } from "@/lib/api/kaiwa";
import { GeminiError, suggestTopics } from "@/lib/kaiwa/gemini";

const LEVELS = [5, 4, 3, 2, 1];

function KaiwaHome() {
  const [apiKey, setApiKey] = useState("");
  const [level, setLevel] = useState(4);
  const [topic, setTopic] = useState("");
  const [topics, setTopics] = useState<string[]>([]);
  const [suggesting, setSuggesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [chat, setChat] = useState<{ level: number; topic: string } | null>(null);
  const [history, setHistory] = useState<KaiwaSessionDto[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);

  const loadHistory = useCallback(() => {
    listKaiwaSessions()
      .then(setHistory)
      .catch(() => setHistory([]));
  }, []);

  useEffect(loadHistory, [loadHistory]);

  const suggest = async () => {
    setSuggesting(true);
    setError(null);
    try {
      setTopics(await suggestTopics(apiKey, level));
    } catch (err) {
      setError(err instanceof GeminiError ? err.message : "Không lấy được gợi ý.");
    } finally {
      setSuggesting(false);
    }
  };

  const remove = async (s: KaiwaSessionDto) => {
    if (!window.confirm("Xoá buổi hội thoại này?")) return;
    try {
      await deleteKaiwaSession(s.id);
      setHistory((prev) => prev?.filter((x) => x.id !== s.id) ?? null);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  if (chat) {
    return (
      <ChatView
        apiKey={apiKey}
        level={chat.level}
        topic={chat.topic}
        onExit={() => {
          setChat(null);
          setTopics([]);
          loadHistory();
        }}
      />
    );
  }

  const canStart = apiKey !== "" && topic.trim() !== "";

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-8">
        <p className="flex items-center gap-3 text-[11px] tracking-widest text-neutral-400">
          会話 <span className="h-px w-10 bg-black/10" />
        </p>
        <h1 className="mt-3 text-3xl font-semibold text-neutral-900">Kaiwa</h1>
        <p className="mt-2 text-sm text-neutral-400">
          Trò chuyện bằng tiếng Nhật với giáo viên AI. Bạn nói hoặc gõ, giáo viên trả lời và nhẹ nhàng sửa lỗi bằng tiếng Việt.
        </p>
      </div>

      <div className="space-y-5">
        <ApiKeyPanel onChange={setApiKey} />

        <div className="rounded-2xl border border-black/5 bg-white p-5">
          <p className="mb-3 text-xs font-semibold text-neutral-900">1. Trình độ</p>
          <div className="grid grid-cols-5 gap-2">
            {LEVELS.map((l) => (
              <button
                key={l}
                type="button"
                onClick={() => {
                  setLevel(l);
                  setTopics([]);
                }}
                className={`rounded-xl border py-2.5 text-sm font-medium transition ${
                  level === l ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/10 text-neutral-600 hover:border-black/30"
                }`}
              >
                N{l}
              </button>
            ))}
          </div>

          <p className="mb-3 mt-6 text-xs font-semibold text-neutral-900">2. Chủ đề</p>
          <input
            value={topic}
            maxLength={150}
            onChange={(e) => setTopic(e.target.value)}
            placeholder="Nhập chủ đề, ví dụ: đặt món ở nhà hàng"
            aria-label="Chủ đề"
            className="w-full rounded-lg border border-black/10 px-3 py-2 text-sm outline-none focus:border-black/40"
          />
          <button
            type="button"
            onClick={suggest}
            disabled={!apiKey || suggesting}
            className="mt-3 flex items-center gap-1.5 rounded-full border border-black/10 px-4 py-2 text-xs text-neutral-700 hover:border-black/30 disabled:opacity-40"
          >
            {suggesting ? <Loader2 size={13} className="animate-spin" /> : <Sparkles size={13} />}
            Gợi ý 3 chủ đề cho N{level}
          </button>
          {topics.length > 0 && (
            <div className="mt-3 flex flex-col gap-2">
              {topics.map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setTopic(t)}
                  className={`rounded-xl border px-4 py-2.5 text-left text-sm transition ${
                    topic === t ? "border-neutral-900 bg-[#F5F3EE]" : "border-black/10 hover:border-black/30"
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>
          )}

          {error && <p className="mt-3 text-xs text-red-600">{error}</p>}

          <button
            type="button"
            onClick={() => setChat({ level, topic: topic.trim() })}
            disabled={!canStart}
            className="mt-6 w-full rounded-full bg-neutral-900 py-3 text-sm font-medium text-white transition disabled:opacity-40"
          >
            {apiKey ? "Bắt đầu hội thoại" : "Nhập key Gemini để bắt đầu"}
          </button>
        </div>

        {history && history.length > 0 && (
          <div className="rounded-2xl border border-black/5 bg-white p-5">
            <p className="mb-3 text-xs font-semibold text-neutral-900">Các buổi trước</p>
            <div className="divide-y divide-black/5">
              {history.map((s) => (
                <div key={s.id} className="py-3">
                  <div className="flex items-start justify-between gap-3">
                    <button type="button" onClick={() => setOpen(open === s.id ? null : s.id)} className="min-w-0 text-left">
                      <p className="truncate text-sm font-medium text-neutral-900">{s.topic}</p>
                      <p className="text-[11px] text-neutral-400">
                        N{s.level} · {s.turns} lượt · {new Date(s.endedAt).toLocaleDateString("vi-VN")}
                      </p>
                    </button>
                    <button
                      type="button"
                      onClick={() => remove(s)}
                      aria-label="Xoá buổi này"
                      className="shrink-0 p-1 text-neutral-300 hover:text-red-600"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                  {open === s.id && <p className="mt-2 whitespace-pre-wrap text-xs leading-5 text-neutral-600">{s.summary}</p>}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default function KaiwaPage() {
  return (
    <RequireAuth message="Bạn cần đăng nhập để dùng Kaiwa.">
      <KaiwaHome />
    </RequireAuth>
  );
}
