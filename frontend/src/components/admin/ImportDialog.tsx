"use client";

import { useRef, useState } from "react";
import { Copy, FileAudio, X } from "lucide-react";
import { commitImport, previewImport, type ImportCommit, type ImportPreview } from "@/lib/api/admin";
import { IMPORT_TEMPLATE, substituteMedia } from "@/lib/importMedia";
import { uploadMedia } from "@/lib/media";

type Step = "edit" | "working" | "preview" | "done";

function describeError(e: unknown): string {
  const body = (e as { response?: { data?: { message?: string; error?: string } } })?.response?.data;
  return body?.message ?? body?.error ?? "Có lỗi xảy ra, thử lại nhé.";
}

/** Import JSON plus audio and image files: upload files, rewrite names, preview, then commit. */
export function ImportDialog({ onClose, onImported }: { onClose: () => void; onImported: () => void }) {
  const [text, setText] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [step, setStep] = useState<Step>("edit");
  const [progress, setProgress] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [onDuplicate, setOnDuplicate] = useState<"skip" | "overwrite">("skip");
  const [result, setResult] = useState<ImportCommit | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  async function check() {
    setError(null);
    let parsed: unknown;
    try {
      parsed = JSON.parse(text);
    } catch {
      setError("JSON không hợp lệ, kiểm tra lại dấu phẩy và ngoặc.");
      return;
    }
    setStep("working");
    try {
      const urls = new Map<string, string>();
      for (let i = 0; i < files.length; i++) {
        setProgress(`Đang tải lên file ${i + 1}/${files.length}: ${files[i].name}`);
        const media = await uploadMedia(files[i]);
        urls.set(files[i].name.trim().toLowerCase(), media.url);
      }
      setProgress("Đang kiểm tra đề...");
      setPreview(await previewImport(substituteMedia(parsed, urls)));
      setStep("preview");
    } catch (e) {
      setError(describeError(e));
      setStep("edit");
    }
  }

  async function commit() {
    if (!preview) return;
    setStep("working");
    setProgress("Đang lưu vào ngân hàng...");
    try {
      setResult(await commitImport(preview.previewId, onDuplicate));
      setStep("done");
      onImported();
    } catch (e) {
      setError(describeError(e));
      setStep("preview");
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: "rgba(0,0,0,0.35)", backdropFilter: "blur(4px)" }}
    >
      <div className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl bg-white p-6 shadow-xl">
        <div className="flex items-center justify-between">
          <h2 className="font-serif text-xl font-semibold text-neutral-900">Import JSON</h2>
          <button type="button" onClick={onClose} aria-label="Đóng" className="rounded-full p-1.5 hover:bg-black/5">
            <X size={18} />
          </button>
        </div>

        <div className="mt-4 min-h-0 flex-1 overflow-y-auto">
          {step === "edit" && (
            <>
              <p className="text-sm text-neutral-500">
                Dán JSON đề thi theo định dạng trong docs/JLPT_IMPORT_FORMAT.md. Với đề nghe, ghi tên file audio
                trong JSON rồi chọn các file đó ở dưới, hệ thống tự tải lên và thay bằng địa chỉ phát.
              </p>
              <textarea
                value={text}
                onChange={(e) => setText(e.target.value)}
                spellCheck={false}
                placeholder='{"test": {...}, "questions": [...]}'
                className="mt-3 h-56 w-full resize-y rounded-xl border border-black/10 p-3 font-mono text-xs outline-none focus:border-neutral-900"
              />
              <button
                type="button"
                onClick={() => setText(JSON.stringify(IMPORT_TEMPLATE, null, 2))}
                className="mt-2 flex items-center gap-1.5 text-xs text-neutral-500 hover:text-neutral-800"
              >
                <Copy size={13} /> Điền mẫu nhỏ
              </button>

              <div className="mt-4">
                <input
                  ref={fileInput}
                  type="file"
                  multiple
                  accept=".mp3,.m4a,.ogg,.wav,.png,.jpg,.jpeg,.webp"
                  className="hidden"
                  onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
                />
                <button
                  type="button"
                  onClick={() => fileInput.current?.click()}
                  className="flex items-center gap-2 rounded-full border border-black/10 px-4 py-2 text-sm text-neutral-600 hover:bg-black/5"
                >
                  <FileAudio size={16} /> Chọn file audio và ảnh
                </button>
                {files.length > 0 && (
                  <p className="mt-2 text-xs text-neutral-500">
                    {files.length} file: {files.map((f) => f.name).join(", ")}
                  </p>
                )}
                <p className="mt-1 text-xs text-neutral-400">
                  Chấp nhận mp3, m4a, ogg, wav (tối đa 60 MB) và png, jpg, webp (tối đa 5 MB).
                </p>
              </div>
            </>
          )}

          {step === "working" && <p className="py-10 text-center text-sm text-neutral-500">{progress}</p>}

          {step === "preview" && preview && (
            <div className="text-sm">
              <p className="text-neutral-700">
                {preview.testTitle ? `Đề: ${preview.testTitle}. ` : ""}
                {preview.questionCount} câu hỏi, {preview.groupCount} nhóm.
              </p>
              {preview.errors.length > 0 && (
                <div className="mt-3 rounded-xl bg-red-50 p-3 text-red-700">
                  <p className="font-medium">Có {preview.errors.length} lỗi, cần sửa JSON:</p>
                  <ul className="mt-1 max-h-40 list-disc overflow-y-auto pl-5 text-xs">
                    {preview.errors.map((e, i) => (
                      <li key={i}>
                        <span className="font-mono">{e.path}</span>: {e.message}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {preview.duplicates.length > 0 && (
                <div className="mt-3 rounded-xl bg-amber-50 p-3 text-amber-800">
                  <p className="font-medium">{preview.duplicates.length} câu đã có trong ngân hàng.</p>
                  <div className="mt-2 flex gap-4 text-xs">
                    <label className="flex items-center gap-1.5">
                      <input type="radio" checked={onDuplicate === "skip"} onChange={() => setOnDuplicate("skip")} />
                      Bỏ qua
                    </label>
                    <label className="flex items-center gap-1.5">
                      <input
                        type="radio"
                        checked={onDuplicate === "overwrite"}
                        onChange={() => setOnDuplicate("overwrite")}
                      />
                      Ghi đè
                    </label>
                  </div>
                </div>
              )}
              {preview.canCommit && preview.errors.length === 0 && (
                <p className="mt-3 text-emerald-600">Hợp lệ, sẵn sàng lưu.</p>
              )}
            </div>
          )}

          {step === "done" && result && (
            <p className="py-8 text-center text-sm text-emerald-700">
              Đã thêm {result.createdQuestions} câu và {result.createdGroups} nhóm
              {result.skipped > 0 ? `, bỏ qua ${result.skipped}` : ""}
              {result.overwritten > 0 ? `, ghi đè ${result.overwritten}` : ""}.
            </p>
          )}

          {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
        </div>

        <div className="mt-4 flex justify-end gap-2">
          {step === "edit" && (
            <button
              type="button"
              disabled={!text.trim()}
              onClick={check}
              className="rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white disabled:opacity-40"
            >
              Kiểm tra
            </button>
          )}
          {step === "preview" && (
            <>
              <button
                type="button"
                onClick={() => setStep("edit")}
                className="rounded-full border border-black/10 px-5 py-2 text-sm text-neutral-600"
              >
                Sửa lại
              </button>
              <button
                type="button"
                disabled={!preview?.canCommit}
                onClick={commit}
                className="rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white disabled:opacity-40"
              >
                Lưu vào ngân hàng
              </button>
            </>
          )}
          {step === "done" && (
            <button
              type="button"
              onClick={onClose}
              className="rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
            >
              Đóng
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
