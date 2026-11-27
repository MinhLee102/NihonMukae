"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Download, RotateCcw, Trash2, Upload } from "lucide-react";
import { CopyButton } from "@/components/admin/CopyButton";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import { buildAgentFile, buildExplainPrompt, buildExtractPrompt, buildListPrompt } from "@/lib/adminGrammarPrompts";
import {
  deleteGrammarPoint,
  fetchGrammarBatch,
  fetchGrammarDuplicates,
  fetchGrammarPending,
  markGrammarDistinct,
  mergeGrammar,
  fetchGrammarPoints,
  fetchGrammarSummary,
  importGrammarList,
  parseJsonArray,
  restoreGrammarPoint,
  saveGrammarContent,
  type GrammarBatchItem,
  type GrammarContentResult,
  type GrammarDuplicateRow,
  type GrammarLevelSummary,
  type GrammarListItem,
  type GrammarListResult,
  type GrammarPointRow,
} from "@/lib/api/adminGrammar";

const BATCH_SIZE = 15;

function PromptBox({ text }: { text: string }) {
  return (
    <pre className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap break-words rounded-xl border border-black/5 bg-[#FAF9F6] p-3 font-mono text-[11px] leading-relaxed text-neutral-700">
      {text}
    </pre>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mt-5 rounded-2xl border border-black/5 bg-white p-5">
      <h2 className="text-base font-semibold text-neutral-900">{title}</h2>
      {children}
    </section>
  );
}

const TEXTAREA = "mt-3 h-40 w-full resize-y rounded-xl border border-black/10 p-3 font-mono text-xs outline-none focus:border-neutral-900";
const BUTTON = "rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white disabled:opacity-40";

/** Step 1: save the list of pattern names for a level (AI listing, or extracted from a reference PDF). */
function ListStep({ level, existing, onSaved }: { level: number; existing: string[]; onSaved: () => void }) {
  const [source, setSource] = useState<"ai" | "pdf">("ai");
  const [withFile, setWithFile] = useState(false);
  const [pages, setPages] = useState("1-10");
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<GrammarListResult | null>(null);

  const prompt = useMemo(
    () => (source === "ai" ? buildListPrompt(level, existing, withFile) : buildExtractPrompt(level, pages)),
    [source, level, existing, withFile, pages]
  );

  const save = async () => {
    setError(null);
    setResult(null);
    let items: GrammarListItem[];
    try {
      items = parseJsonArray(text).filter(
        (x): x is GrammarListItem => typeof x === "object" && x !== null && typeof (x as GrammarListItem).pattern === "string"
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "JSON không hợp lệ.");
      return;
    }
    if (items.length === 0) {
      setError("Không có mục nào có \"pattern\".");
      return;
    }
    try {
      setBusy(true);
      setResult(await importGrammarList(level, items));
      setText("");
      onSaved();
    } catch (e) {
      setError(getApiErrorMessage(e, "Không lưu được danh sách."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title={`Bước 1. Danh sách tên mẫu N${level}`}>
      <p className="mt-1 text-sm text-neutral-500">
        Lấy tên các mẫu ngữ pháp trước, nội dung viết ở bước 2. Lưu nhiều lần được: mẫu đã có (so theo tên) tự bỏ qua, mẫu
        mới thêm vào cuối danh sách.
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
        {(
          [
            ["ai", "AI tự liệt kê"],
            ["pdf", "Trích từ file PDF"],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            onClick={() => setSource(id)}
            className={`rounded-full px-4 py-1.5 ${source === id ? "bg-neutral-900 text-white" : "border border-black/10 text-neutral-600"}`}
          >
            {label}
          </button>
        ))}
        {source === "ai" ? (
          <label className="ml-2 flex items-center gap-2 text-xs text-neutral-500">
            <input type="checkbox" checked={withFile} onChange={(e) => setWithFile(e.target.checked)} />
            Có đính kèm file tham khảo
          </label>
        ) : (
          <label className="ml-2 flex items-center gap-2 text-xs text-neutral-500">
            Trang
            <input
              value={pages}
              onChange={(e) => setPages(e.target.value.slice(0, 20))}
              className="w-20 rounded-lg border border-black/10 px-2 py-1 text-sm text-neutral-900"
            />
          </label>
        )}
        <div className="flex-1" />
        <CopyButton text={prompt} />
      </div>
      {source === "ai" && existing.length > 0 && (
        <p className="mt-2 text-xs text-neutral-400">
          Prompt đã kèm {existing.length} mẫu đang có, để AI liệt kê tiếp phần còn thiếu.
        </p>
      )}
      {source === "pdf" && (
        <p className="mt-2 text-xs text-neutral-400">
          Đính kèm file PDF khi dán prompt. File dài thì chia theo trang (ví dụ 1-11, rồi 12-21), mỗi lần lưu một phần.
        </p>
      )}
      <PromptBox text={prompt} />
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        spellCheck={false}
        placeholder='Dán câu trả lời của AI: [{"pattern": "...", "reading": "..."}]'
        className={TEXTAREA}
      />
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {result && (
        <div className="mt-2 text-sm text-neutral-700">
          Đã thêm {result.added} mẫu, bỏ qua {result.skipped} mẫu đã có.
          {result.crossLevel.length > 0 && (
            <p className="mt-1 text-xs text-amber-700">
              {result.crossLevel.length} mẫu trùng tên với cấp khác: {result.crossLevel.join("、")}. Xem mục Trùng giữa các cấp
              để gộp hoặc giữ riêng.
            </p>
          )}
          {result.problems.length > 0 && (
            <ul className="mt-1 list-disc pl-5 text-xs text-amber-700">
              {result.problems.map((p, i) => (
                <li key={i}>{p}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="mt-3 flex justify-end">
        <button type="button" onClick={() => void save()} disabled={busy || !text.trim()} className={BUTTON}>
          {busy ? "Đang lưu..." : "Lưu danh sách"}
        </button>
      </div>
    </Card>
  );
}

/** Step 2: the next patterns without content, the prompt for them, and the box for the AI answer. */
function ContentStep({ level, version, onSaved }: { level: number; version: number; onSaved: () => void }) {
  const [batch, setBatch] = useState<GrammarBatchItem[] | null>(null);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<GrammarContentResult | null>(null);

  const load = useCallback(async () => {
    try {
      setBatch(await fetchGrammarBatch(level, BATCH_SIZE));
    } catch (e) {
      setError(getApiErrorMessage(e, "Không tải được batch."));
    }
  }, [level]);

  useEffect(() => {
    setBatch(null);
    setResult(null);
    void load();
  }, [load, version]);

  const prompt = useMemo(() => (batch && batch.length > 0 ? buildExplainPrompt(batch) : ""), [batch]);

  const save = async () => {
    setError(null);
    setResult(null);
    let entries: unknown[];
    try {
      entries = parseJsonArray(text);
    } catch (e) {
      setError(e instanceof Error ? e.message : "JSON không hợp lệ.");
      return;
    }
    try {
      setBusy(true);
      setResult(await saveGrammarContent(entries));
      setText("");
      onSaved();
    } catch (e) {
      setError(getApiErrorMessage(e, "Không lưu được nội dung."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title={`Bước 2. Viết nội dung N${level}`}>
      <p className="mt-1 text-sm text-neutral-500">
        Mỗi lần {BATCH_SIZE} mẫu chưa có nội dung. Copy prompt, dán vào AI, rồi dán câu trả lời xuống dưới. Mục lỗi không
        được lưu và sẽ tự vào lại batch sau. Mục AI đánh dấu không chắc thì bị gác lại (xem ở danh sách bên dưới).
      </p>
      {!batch && <p className="mt-3 text-sm text-neutral-400">Đang tải...</p>}
      {batch && batch.length === 0 && (
        <p className="mt-3 text-sm text-neutral-500">Không còn mẫu nào chờ viết nội dung ở cấp này.</p>
      )}
      {batch && batch.length > 0 && (
        <>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <p className="min-w-0 flex-1 text-xs text-neutral-500">
              {batch.map((b) => b.pattern).join("、")}
            </p>
            <CopyButton text={prompt} />
          </div>
          <PromptBox text={prompt} />
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            spellCheck={false}
            placeholder='Dán câu trả lời của AI: [{"id": "G1", "meaning": "...", ...}]'
            className={TEXTAREA}
          />
          <div className="mt-3 flex justify-end">
            <button type="button" onClick={() => void save()} disabled={busy || !text.trim()} className={BUTTON}>
              {busy ? "Đang kiểm tra..." : "Kiểm tra và lưu"}
            </button>
          </div>
        </>
      )}
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {result && (
        <div className="mt-3 rounded-xl bg-[#F5F3EE] p-3 text-sm text-neutral-700">
          <p>
            Đã lưu {result.saved} mẫu
            {result.invalid > 0 && `, ${result.invalid} mẫu AI đánh dấu không chắc`}
            {result.errors.length > 0 && `, ${result.errors.length} mục lỗi (chưa lưu)`}.
          </p>
          {result.errors.length > 0 && (
            <ul className="mt-2 space-y-1 text-xs text-red-700">
              {result.errors.map((x, i) => (
                <li key={i}>
                  <b>{x.id}</b> {x.pattern}: {x.messages.join("; ")}
                </li>
              ))}
            </ul>
          )}
          {result.warnings.length > 0 && (
            <ul className="mt-2 space-y-1 text-xs text-amber-700">
              {result.warnings.map((x, i) => (
                <li key={i}>
                  <b>{x.id}</b> {x.pattern}: {x.messages.join("; ")}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}

/** Download every pending pattern as one file for an agent, then import the result files it wrote. */
function AgentTools({ level, onSaved }: { level: number; onSaved: () => void }) {
  const [scope, setScope] = useState<"level" | "all">("level");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [result, setResult] = useState<GrammarContentResult | null>(null);
  const agentPrompt =
    "Trong thư mục này có file grammar_todo.md. Đọc phần hướng dẫn ở đầu file và làm theo cho TOÀN BỘ các batch trong một lần chạy, không dừng lại hỏi giữa chừng. Ghi kết quả mỗi batch vào results/<tên batch>.json.";

  const download = async () => {
    setMessage(null);
    try {
      setBusy(true);
      const items = await fetchGrammarPending(scope === "level" ? level : undefined);
      if (items.length === 0) {
        setMessage("Không còn mẫu nào chờ viết.");
        return;
      }
      // Chia theo cấp chính, mỗi batch BATCH_SIZE mẫu: G3-01, G3-02...
      const byLevel = new Map<string, GrammarBatchItem[]>();
      for (const it of items) {
        const lv = it.level.split(",")[0].replace("N", "");
        byLevel.set(lv, [...(byLevel.get(lv) ?? []), it]);
      }
      const batches: { name: string; items: GrammarBatchItem[] }[] = [];
      for (const [lv, list] of Array.from(byLevel.entries())) {
        for (let i = 0; i < list.length; i += BATCH_SIZE) {
          batches.push({ name: `G${lv}-${String(i / BATCH_SIZE + 1).padStart(2, "0")}`, items: list.slice(i, i + BATCH_SIZE) });
        }
      }
      const blob = new Blob([buildAgentFile(batches)], { type: "text/markdown;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "grammar_todo.md";
      a.click();
      URL.revokeObjectURL(url);
      setMessage(`Đã tạo grammar_todo.md: ${items.length} mẫu, ${batches.length} batch.`);
    } catch (e) {
      setMessage(getApiErrorMessage(e, "Không tạo được file."));
    } finally {
      setBusy(false);
    }
  };

  const importFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setMessage(null);
    setResult(null);
    const entries: unknown[] = [];
    const broken: string[] = [];
    for (const file of Array.from(files)) {
      try {
        entries.push(...parseJsonArray(await file.text()));
      } catch {
        broken.push(file.name);
      }
    }
    if (entries.length === 0) {
      setMessage(`Không đọc được JSON trong file nào${broken.length ? `: ${broken.join(", ")}` : ""}.`);
      return;
    }
    try {
      setBusy(true);
      const total: GrammarContentResult = { saved: 0, invalid: 0, errors: [], warnings: [] };
      // Gửi từng phần để mỗi lần không quá lớn
      for (let i = 0; i < entries.length; i += 100) {
        const r = await saveGrammarContent(entries.slice(i, i + 100));
        total.saved += r.saved;
        total.invalid += r.invalid;
        total.errors.push(...r.errors);
        total.warnings.push(...r.warnings);
      }
      setResult(total);
      if (broken.length) setMessage(`Bỏ qua file không đọc được: ${broken.join(", ")}`);
      onSaved();
    } catch (e) {
      setMessage(getApiErrorMessage(e, "Không lưu được."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Chạy bằng agent (nhiều batch một lượt)">
      <p className="mt-1 text-sm text-neutral-500">
        Tải file việc cần làm, đặt vào một thư mục và giao cho agent (ví dụ Antigravity) với prompt bên dưới. Agent ghi
        mỗi batch ra một file trong results/. Xong thì chọn tất cả file đó để nhập một lượt. Mục lỗi không được lưu, tải
        file lần sau sẽ chỉ còn các mẫu chưa xong.
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
        {(
          [
            ["level", `Chỉ N${level}`],
            ["all", "Mọi cấp"],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            onClick={() => setScope(id)}
            className={`rounded-full px-4 py-1.5 ${scope === id ? "bg-neutral-900 text-white" : "border border-black/10 text-neutral-600"}`}
          >
            {label}
          </button>
        ))}
        <button
          type="button"
          onClick={() => void download()}
          disabled={busy}
          className="flex items-center gap-1.5 rounded-full border border-black/10 px-4 py-1.5 text-neutral-700 hover:border-black/30 disabled:opacity-40"
        >
          <Download size={14} /> Tải grammar_todo.md
        </button>
        <label className="flex cursor-pointer items-center gap-1.5 rounded-full bg-neutral-900 px-4 py-1.5 font-medium text-white">
          <Upload size={14} /> Nhập file kết quả
          <input
            type="file"
            multiple
            accept=".json,application/json"
            className="hidden"
            disabled={busy}
            onChange={(e) => {
              void importFiles(e.target.files);
              e.target.value = "";
            }}
          />
        </label>
      </div>
      <div className="mt-3 flex items-start gap-2">
        <p className="min-w-0 flex-1 rounded-xl bg-[#FAF9F6] p-3 text-xs leading-5 text-neutral-600">{agentPrompt}</p>
        <CopyButton text={agentPrompt} />
      </div>
      {busy && <p className="mt-2 text-sm text-neutral-400">Đang xử lý...</p>}
      {message && <p className="mt-2 text-sm text-neutral-700">{message}</p>}
      {result && (
        <div className="mt-3 rounded-xl bg-[#F5F3EE] p-3 text-sm text-neutral-700">
          <p>
            Đã lưu {result.saved} mẫu
            {result.invalid > 0 && `, ${result.invalid} mẫu AI đánh dấu không chắc`}
            {result.errors.length > 0 && `, ${result.errors.length} mục lỗi (chưa lưu)`}
            {result.warnings.length > 0 && `, ${result.warnings.length} mục có cảnh báo`}.
          </p>
          {result.errors.length > 0 && (
            <ul className="mt-2 max-h-48 space-y-1 overflow-y-auto text-xs text-red-700">
              {result.errors.map((x, i) => (
                <li key={i}>
                  <b>{x.id}</b> {x.pattern}: {x.messages.join("; ")}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}

/** Same pattern at several levels: merge into one (shown at every level) or keep apart when the meanings differ. */
function DuplicatesSection({ version, onChanged }: { version: number; onChanged: () => void }) {
  const [groups, setGroups] = useState<GrammarDuplicateRow[][] | null>(null);
  const [keep, setKeep] = useState<Record<number, number>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchGrammarDuplicates()
      .then(setGroups)
      .catch((e) => setError(getApiErrorMessage(e, "Không tải được danh sách trùng.")));
  }, [version]);

  const act = async (fn: () => Promise<void>) => {
    try {
      setError(null);
      await fn();
      onChanged();
    } catch (e) {
      setError(getApiErrorMessage(e));
    }
  };

  if (!groups || groups.length === 0) {
    return error ? <p className="mt-3 text-sm text-red-600">{error}</p> : null;
  }

  return (
    <Card title={`Trùng giữa các cấp (${groups.length} nhóm)`}>
      <p className="mt-1 text-sm text-neutral-500">
        Cùng tên hoặc cùng cách đọc ở nhiều cấp. Cùng nghĩa thì <b>Gộp</b>: giữ một bản nội dung, cấp chính là cấp thấp
        nhất và mẫu vẫn hiện ở các cấp kia. Khác nghĩa (ví dụ 〜ように ở N4 và N3) thì <b>Giữ riêng</b>: prompt sẽ dặn AI
        chỉ viết nghĩa của từng cấp.
      </p>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      <ul className="mt-3 space-y-3">
        {groups.map((g) => {
          const groupKey = g[0].id;
          // Mặc định giữ mẫu đã có nội dung, nếu không thì mẫu ở cấp thấp nhất (đứng đầu nhóm)
          const chosen = keep[groupKey] ?? (g.find((r) => r.meaning)?.id ?? g[0].id);
          return (
            <li key={groupKey} className="rounded-xl border border-black/5 p-3">
              {g.map((r) => (
                <label key={r.id} className="flex items-start gap-2 py-1 text-sm">
                  <input
                    type="radio"
                    name={`keep-${groupKey}`}
                    checked={chosen === r.id}
                    onChange={() => setKeep((k) => ({ ...k, [groupKey]: r.id }))}
                    className="mt-1"
                  />
                  <span className="w-8 shrink-0 font-semibold text-neutral-900">N{r.level}</span>
                  <span className="min-w-0 flex-1">
                    <span className="text-neutral-900">{r.pattern}</span>
                    {r.reading && <span className="ml-2 text-xs text-neutral-400">{r.reading}</span>}
                    <span className="block truncate text-xs text-neutral-500">{r.meaning ?? "Chưa có nội dung"}</span>
                  </span>
                </label>
              ))}
              <div className="mt-2 flex flex-wrap justify-end gap-2">
                <button
                  type="button"
                  onClick={() => void act(() => markGrammarDistinct(g.map((r) => r.id)))}
                  className="rounded-full border border-black/10 px-4 py-1.5 text-xs text-neutral-700 hover:border-black/30"
                >
                  Khác nghĩa, giữ riêng
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (window.confirm("Gộp thành một mẫu? Các mẫu không được chọn sẽ bị xoá (nội dung của mẫu được chọn được giữ).")) {
                      void act(() => mergeGrammar(chosen, g.map((r) => r.id).filter((id) => id !== chosen)));
                    }
                  }}
                  className="rounded-full bg-neutral-900 px-4 py-1.5 text-xs font-medium text-white"
                >
                  Gộp (giữ mẫu đã chọn)
                </button>
              </div>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

function PointList({ points, onChanged }: { points: GrammarPointRow[]; onChanged: () => void }) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const act = async (fn: () => Promise<void>) => {
    try {
      setError(null);
      await fn();
      onChanged();
    } catch (e) {
      setError(getApiErrorMessage(e));
    }
  };

  return (
    <Card title={`Danh sách mẫu (${points.length})`}>
      <button type="button" onClick={() => setOpen((o) => !o)} className="mt-2 text-sm text-neutral-500 underline underline-offset-2">
        {open ? "Ẩn danh sách" : "Xem danh sách"}
      </button>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {open && (
        <ul className="mt-3 divide-y divide-black/5">
          {points.map((p) => (
            <li key={p.id} className="flex items-center gap-3 py-2 text-sm">
              <span className="w-14 shrink-0 font-mono text-xs text-neutral-400">G{p.id}</span>
              <span className="min-w-0 flex-1 truncate text-neutral-900">
                {p.pattern}
                {p.reading && <span className="ml-2 text-xs text-neutral-400">{p.reading}</span>}
                {p.category && <span className="ml-2 text-xs text-neutral-400">· {p.category}</span>}
                {p.extraLevels.length > 0 && (
                  <span className="ml-2 rounded-full bg-sky-50 px-1.5 text-[11px] text-sky-700">
                    + {p.extraLevels.map((l) => `N${l}`).join(", ")}
                  </span>
                )}
              </span>
              <span
                className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] ${
                  p.ready ? "bg-emerald-50 text-emerald-700" : p.invalid ? "bg-neutral-100 text-neutral-500" : "bg-amber-50 text-amber-700"
                }`}
              >
                {p.ready ? "Đã có nội dung" : p.invalid ? "AI không chắc" : "Chờ viết"}
              </span>
              {p.invalid && (
                <button
                  type="button"
                  onClick={() => void act(() => restoreGrammarPoint(p.id))}
                  title="Cho vào lại hàng chờ"
                  aria-label="Cho vào lại hàng chờ"
                  className="rounded-full p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700"
                >
                  <RotateCcw size={14} />
                </button>
              )}
              <button
                type="button"
                onClick={() => {
                  if (window.confirm(`Xoá mẫu ${p.pattern}? Nội dung và ví dụ của mẫu cũng bị xoá.`)) {
                    void act(() => deleteGrammarPoint(p.id));
                  }
                }}
                title="Xoá mẫu"
                aria-label="Xoá mẫu"
                className="rounded-full p-1.5 text-neutral-400 hover:bg-red-50 hover:text-red-600"
              >
                <Trash2 size={14} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

export default function AdminGrammarPage() {
  const [level, setLevel] = useState(3);
  const [summary, setSummary] = useState<GrammarLevelSummary[]>([]);
  const [points, setPoints] = useState<GrammarPointRow[]>([]);
  const [version, setVersion] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [s, p] = await Promise.all([fetchGrammarSummary(), fetchGrammarPoints(level)]);
      setSummary(s);
      setPoints(p);
    } catch (e) {
      setError(getApiErrorMessage(e, "Không tải được dữ liệu ngữ pháp."));
    }
  }, [level]);

  useEffect(() => {
    void reload();
  }, [reload, version]);

  const existing = useMemo(() => points.map((p) => p.pattern), [points]);
  const refresh = () => setVersion((v) => v + 1);

  return (
    <div className="mx-auto max-w-4xl">
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Ngữ pháp</h1>
      <p className="mt-1 text-sm text-neutral-500">
        Soạn ngữ pháp bằng AI: lấy danh sách tên mẫu, rồi viết nội dung từng batch. Nội dung do AI viết mới, không chép từ
        sách hay file tham khảo.
      </p>
      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      <div className="mt-5 grid grid-cols-5 gap-2">
        {summary.map((s) => (
          <button
            key={s.level}
            type="button"
            onClick={() => setLevel(s.level)}
            className={`rounded-2xl border p-3 text-left transition ${
              level === s.level ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/5 bg-white hover:border-black/20"
            }`}
          >
            <p className="text-lg font-semibold">N{s.level}</p>
            <p className={`mt-1 text-[11px] leading-4 ${level === s.level ? "text-neutral-300" : "text-neutral-500"}`}>
              {s.ready}/{s.total} đã xong
              {s.pending > 0 && <br />}
              {s.pending > 0 && `${s.pending} chờ viết`}
              {s.invalid > 0 && <br />}
              {s.invalid > 0 && `${s.invalid} AI không chắc`}
            </p>
          </button>
        ))}
      </div>

      <ListStep level={level} existing={existing} onSaved={refresh} />
      <ContentStep level={level} version={version} onSaved={refresh} />
      <AgentTools level={level} onSaved={refresh} />
      <DuplicatesSection version={version} onChanged={refresh} />
      <PointList points={points} onChanged={refresh} />
    </div>
  );
}
