"use client";

import { useMemo, useState } from "react";
import { Check, Copy } from "lucide-react";
import { buildExamPrompt, examFilePrefix } from "@/lib/adminPrompts";
import { GEN_PARTS, buildGeneratePrompt, genTestTitle, genTypesFor, type GenPart } from "@/lib/adminGenPrompt";

/** Copies text, with a textarea fallback for browsers that block the clipboard API on plain http. */
async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const area = document.createElement("textarea");
      area.value = text;
      area.style.position = "fixed";
      area.style.opacity = "0";
      document.body.appendChild(area);
      area.select();
      const ok = document.execCommand("copy");
      document.body.removeChild(area);
      return ok;
    } catch {
      return false;
    }
  }
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState<"ok" | "fail" | null>(null);

  async function copy() {
    setCopied((await copyText(text)) ? "ok" : "fail");
    window.setTimeout(() => setCopied(null), 2500);
  }

  return (
    <button
      type="button"
      onClick={() => void copy()}
      className="flex items-center gap-2 rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
    >
      {copied === "ok" ? <Check size={15} /> : <Copy size={15} />}
      {copied === "ok" ? "Đã sao chép" : copied === "fail" ? "Không sao chép được, bôi đen rồi Ctrl + C" : "Sao chép prompt"}
    </button>
  );
}

function PromptBox({ text }: { text: string }) {
  return (
    <pre className="mt-4 max-h-[60vh] overflow-auto whitespace-pre-wrap break-words rounded-2xl border border-black/5 bg-white p-4 font-mono text-xs leading-relaxed text-neutral-800">
      {text}
    </pre>
  );
}

const SELECT_CLASS = "mt-1 block rounded-lg border border-black/10 px-3 py-2 text-sm text-neutral-900";
const INPUT_CLASS = "mt-1 block rounded-lg border border-black/10 px-3 py-2 text-sm text-neutral-900";

function LevelSelect({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  return (
    <label className="text-xs text-neutral-500">
      Cấp độ
      <select value={value} onChange={(e) => onChange(Number(e.target.value))} className={SELECT_CLASS}>
        {[5, 4, 3, 2, 1].map((n) => (
          <option key={n} value={n}>
            N{n}
          </option>
        ))}
      </select>
    </label>
  );
}

/** Convert a real exam (PDF or images) into import JSON plus the list of pictures to crop. */
function ConvertPanel() {
  const [level, setLevel] = useState(3);
  const [year, setYear] = useState("2023");
  const [month, setMonth] = useState("07");
  const prompt = useMemo(() => buildExamPrompt({ level, year, month }), [level, year, month]);
  const prefix = examFilePrefix({ level, year, month });

  return (
    <>
      <p className="mt-4 text-sm text-neutral-500">
        Đính kèm PDF hoặc ảnh của đề thật vào AI, dán prompt này, rồi dán phần JSON AI trả về vào Ngân hàng đề, Import JSON.
        Bảng ảnh cần cắt AI trả về thì dùng để chụp ảnh từ PDF (Win + Shift + S) và đặt đúng tên file.
      </p>
      <div className="mt-4 flex flex-wrap items-end gap-3 rounded-2xl border border-black/5 bg-white p-4">
        <LevelSelect value={level} onChange={setLevel} />
        <label className="text-xs text-neutral-500">
          Năm
          <input
            value={year}
            onChange={(e) => setYear(e.target.value.replace(/\D/g, "").slice(0, 4))}
            inputMode="numeric"
            className={`${INPUT_CLASS} w-24`}
          />
        </label>
        <label className="text-xs text-neutral-500">
          Tháng
          <input
            value={month}
            onChange={(e) => setMonth(e.target.value.replace(/\D/g, "").slice(0, 2))}
            inputMode="numeric"
            className={`${INPUT_CLASS} w-16`}
          />
        </label>
        <p className="min-w-0 flex-1 text-xs text-neutral-400">
          Tên file trong prompt bắt đầu bằng <span className="font-mono text-neutral-600">{prefix}</span>. Đặt tên audio và
          ảnh của bạn đúng như vậy.
        </p>
        <CopyButton text={prompt} />
      </div>
      <PromptBox text={prompt} />
    </>
  );
}

/** Ask the AI to write a brand new test (no listening: the app borrows a listening set from another test of the level). */
function GeneratePanel() {
  const [level, setLevel] = useState(3);
  const [number, setNumber] = useState("01");
  const [part, setPart] = useState<GenPart>("language");
  const prompt = useMemo(() => buildGeneratePrompt({ level, number, part }), [level, number, part]);
  const types = genTypesFor(level, part);
  const total = types.reduce((sum, t) => sum + t.count, 0);
  const title = genTestTitle({ level, number });

  return (
    <>
      <p className="mt-4 text-sm text-neutral-500">
        AI tự soạn câu hỏi, đáp án và giải thích theo đúng số câu từng dạng của thi thử (lấy từ blueprint của ứng dụng), không có
        phần nghe. Khi thi thử, hệ thống tự mượn bộ nghe ngẫu nhiên của một đề khác cùng cấp độ. Chạy lần lượt ba phần với
        cùng cấp độ và số thứ tự, mỗi phần ra một file JSON, rồi import từng file: các phần tự ghép vào cùng đề{" "}
        <span className="font-mono text-neutral-700">{title}</span>.
      </p>
      <div className="mt-4 flex flex-wrap items-end gap-3 rounded-2xl border border-black/5 bg-white p-4">
        <LevelSelect value={level} onChange={setLevel} />
        <label className="text-xs text-neutral-500">
          Số thứ tự đề
          <input
            value={number}
            onChange={(e) => setNumber(e.target.value.replace(/\D/g, "").slice(0, 3))}
            inputMode="numeric"
            className={`${INPUT_CLASS} w-20`}
          />
        </label>
        <label className="text-xs text-neutral-500">
          Phần thi
          <select value={part} onChange={(e) => setPart(e.target.value as GenPart)} className={SELECT_CLASS}>
            {GEN_PARTS.map((p) => (
              <option key={p.value} value={p.value}>
                {p.label}
              </option>
            ))}
          </select>
        </label>
        <div className="flex-1" />
        <CopyButton text={prompt} />
      </div>

      <div className="mt-3 rounded-2xl border border-black/5 bg-white p-4">
        <p className="text-xs text-neutral-500">
          Phần này gồm <span className="font-medium text-neutral-800">{total} câu</span>:
        </p>
        <ul className="mt-2 grid gap-x-6 gap-y-1 text-sm text-neutral-700 sm:grid-cols-2">
          {types.map((t) => (
            <li key={t.code} className="flex justify-between gap-3">
              <span>
                {t.nameJa} <span className="text-neutral-400">{t.nameVi}</span>
              </span>
              <span className="font-mono text-neutral-900">{t.count}</span>
            </li>
          ))}
        </ul>
      </div>
      <PromptBox text={prompt} />
    </>
  );
}

type Tab = "convert" | "generate";

export default function AdminPromptsPage() {
  const [tab, setTab] = useState<Tab>("convert");
  const tabs: { id: Tab; label: string }[] = [
    { id: "convert", label: "Chuyển đề thật sang JSON" },
    { id: "generate", label: "AI soạn đề mới" },
  ];

  return (
    <div className="h-full overflow-y-auto p-6">
      <div className="mx-auto max-w-4xl">
        <h1 className="font-serif text-2xl font-semibold text-neutral-900">Prompt cho AI</h1>
        <div className="mt-4 flex gap-2">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={`rounded-full px-4 py-2 text-sm transition-colors ${
                tab === t.id ? "bg-neutral-900 text-white" : "border border-black/10 text-neutral-600 hover:bg-black/5"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        {tab === "convert" ? <ConvertPanel /> : <GeneratePanel />}
      </div>
    </div>
  );
}
