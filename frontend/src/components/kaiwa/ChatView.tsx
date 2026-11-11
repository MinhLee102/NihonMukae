"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2, Mic, MicOff, Send } from "lucide-react";
import { useSpeechRecognition } from "@/components/kaiwa/useSpeechRecognition";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import { saveKaiwaSession } from "@/lib/api/kaiwa";
import { GeminiError, nextReply, summarize, type ChatMessage } from "@/lib/kaiwa/gemini";

interface Props {
  apiKey: string;
  level: number;
  topic: string;
  onExit: () => void;
}

function Bubble({ message }: { message: ChatMessage }) {
  const mine = message.role === "user";
  // Dòng "💡" là phần sửa lỗi của giáo viên, tách ra cho dễ đọc
  const lines = message.text.split("\n").filter((l) => l.trim() !== "");
  const notes = mine ? [] : lines.filter((l) => l.trim().startsWith("💡"));
  const body = mine ? lines : lines.filter((l) => !l.trim().startsWith("💡"));
  return (
    <div className={`flex ${mine ? "justify-end" : "justify-start"}`}>
      <div className={`max-w-[85%] rounded-2xl px-4 py-2.5 ${mine ? "bg-neutral-900 text-white" : "bg-white text-neutral-900 shadow-sm"}`}>
        <p lang="ja" className="whitespace-pre-wrap text-[15px] leading-6">
          {body.join("\n")}
        </p>
        {notes.map((n, i) => (
          <p key={i} className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900">
            {n}
          </p>
        ))}
      </div>
    </div>
  );
}

export function ChatView({ apiKey, level, topic, onExit }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [failed, setFailed] = useState<"reply" | "finish">("reply");
  const [ending, setEnding] = useState(false);
  const [summary, setSummary] = useState<string | null>(null);
  const startedAt = useRef(new Date().toISOString());
  const bottomRef = useRef<HTMLDivElement>(null);
  const started = useRef(false);
  const speech = useSpeechRecognition((text) => setInput(text));

  const userTurns = messages.filter((m) => m.role === "user").length;

  const ask = async (history: ChatMessage[]) => {
    setBusy(true);
    setError(null);
    try {
      const reply = await nextReply(apiKey, level, topic, history);
      setMessages([...history, { role: "ai", text: reply }]);
    } catch (err) {
      setFailed("reply");
      setError(err instanceof GeminiError ? err.message : "Có lỗi khi gọi Gemini.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (started.current) return; // Strict Mode chạy effect hai lần
    started.current = true;
    void ask([]);
    // chỉ chạy khi mở buổi
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, busy]);

  const send = () => {
    const text = input.trim();
    if (!text || busy) return;
    if (speech.listening) speech.stop();
    const history: ChatMessage[] = [...messages, { role: "user", text }];
    setMessages(history);
    setInput("");
    void ask(history);
  };

  // Gửi lại sau lỗi: tin nhắn cuối của người dùng đã nằm trong danh sách
  const retry = () => void ask(messages);

  const finish = async (withSummary: boolean) => {
    setEnding(true);
    setError(null);
    try {
      const text = withSummary
        ? await summarize(apiKey, level, topic, messages)
        : "Buổi này chưa có tóm tắt (không tạo được nhận xét từ Gemini).";
      await saveKaiwaSession({ level, topic, summary: text, turns: userTurns, startedAt: startedAt.current });
      setSummary(text);
    } catch (err) {
      setFailed("finish");
      setError(err instanceof GeminiError ? err.message : getApiErrorMessage(err, "Không lưu được buổi hội thoại."));
    } finally {
      setEnding(false);
    }
  };

  const end = () => {
    if (userTurns === 0) return onExit();
    void finish(true);
  };

  if (summary !== null) {
    return (
      <div className="mx-auto max-w-2xl rounded-2xl border border-black/5 bg-white p-6">
        <p className="text-[10px] tracking-widest text-neutral-400">TÓM TẮT BUỔI HỘI THOẠI</p>
        <h2 className="mt-2 text-lg font-semibold text-neutral-900">{topic}</h2>
        <p className="mt-4 whitespace-pre-wrap text-sm leading-6 text-neutral-700">{summary}</p>
        <button type="button" onClick={onExit} className="mt-6 rounded-full bg-neutral-900 px-5 py-2.5 text-sm font-medium text-white">
          Xong
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto flex h-[calc(100vh-11rem)] min-h-[420px] max-w-2xl flex-col">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-neutral-900">{topic}</p>
          <p className="text-[11px] text-neutral-400">N{level} · {userTurns} lượt của bạn</p>
        </div>
        <button
          type="button"
          onClick={end}
          disabled={ending}
          className="shrink-0 rounded-full border border-black/10 bg-white px-4 py-2 text-xs text-neutral-700 hover:border-black/30 disabled:opacity-50"
        >
          {ending ? "Đang tóm tắt..." : "Kết thúc"}
        </button>
      </div>

      <div className="flex-1 space-y-3 overflow-y-auto rounded-2xl bg-[#F5F3EE] p-4">
        {messages.map((m, i) => (
          <Bubble key={i} message={m} />
        ))}
        {busy && (
          <div className="flex items-center gap-2 text-xs text-neutral-400">
            <Loader2 size={14} className="animate-spin" /> Giáo viên đang trả lời...
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {error && (
        <div className="mt-2 flex items-center justify-between gap-3 rounded-xl bg-red-50 px-4 py-2.5 text-xs text-red-700">
          <span>{error}</span>
          <span className="flex shrink-0 gap-3">
            <button type="button" onClick={failed === "finish" ? () => finish(true) : retry} className="underline">
              Thử lại
            </button>
            {failed === "finish" && (
              <button type="button" onClick={() => finish(false)} className="underline">
                Lưu không tóm tắt
              </button>
            )}
          </span>
        </div>
      )}
      {speech.error && <p className="mt-2 text-xs text-red-600">{speech.error}</p>}
      {!speech.supported && (
        <p className="mt-2 rounded-xl bg-amber-50 px-4 py-2 text-xs text-amber-800">
          Trình duyệt này chưa hỗ trợ nhận dạng giọng nói. Hãy dùng Chrome hoặc Edge để nói, hoặc gõ câu trả lời.
        </p>
      )}

      <div className="mt-3 flex items-end gap-2">
        {speech.supported && (
          <button
            type="button"
            onClick={speech.listening ? speech.stop : speech.start}
            disabled={busy}
            aria-label={speech.listening ? "Dừng nói" : "Nói"}
            aria-pressed={speech.listening}
            className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full transition disabled:opacity-40 ${
              speech.listening ? "bg-red-600 text-white" : "border border-black/10 bg-white text-neutral-700 hover:border-black/30"
            }`}
          >
            {speech.listening ? <MicOff size={18} /> : <Mic size={18} />}
          </button>
        )}
        <textarea
          lang="ja"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              send();
            }
          }}
          rows={1}
          placeholder={speech.listening ? "Đang nghe, hãy nói tiếng Nhật..." : "Nói hoặc gõ câu trả lời của bạn"}
          className="max-h-32 min-h-11 flex-1 resize-none rounded-2xl border border-black/10 bg-white px-4 py-2.5 text-[15px] outline-none focus:border-black/40"
        />
        <button
          type="button"
          onClick={send}
          disabled={busy || !input.trim()}
          aria-label="Gửi"
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-neutral-900 text-white disabled:opacity-40"
        >
          <Send size={16} />
        </button>
      </div>
    </div>
  );
}
