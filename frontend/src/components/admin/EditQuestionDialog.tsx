"use client";

import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { fetchAdminQuestion, updateAdminQuestion } from "@/lib/api/admin";
import { getApiErrorMessage } from "@/lib/api/flashcard";

const LETTERS = ["A", "B", "C", "D", "E", "F"];

/** Edit one question of the bank: text, options, correct answer, explanation and (for listening) transcript. */
export function EditQuestionDialog({
  questionId,
  onClose,
  onSaved,
}: {
  questionId: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [question, setQuestion] = useState("");
  const [options, setOptions] = useState<string[]>([]);
  const [correct, setCorrect] = useState("A");
  const [explanation, setExplanation] = useState("");
  const [transcript, setTranscript] = useState("");
  const [meta, setMeta] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchAdminQuestion(questionId)
      .then((q) => {
        setQuestion(q.question);
        setOptions(q.options);
        setCorrect(q.correct);
        setExplanation(q.explanation ?? "");
        setTranscript(q.transcript ?? "");
        setMeta(`#${q.id} · N${q.level} · ${q.typeName ?? ""}${q.section === "chokai" ? " · nghe hiểu" : ""}`);
      })
      .catch((err) => setError(getApiErrorMessage(err, "Không tải được câu hỏi.")))
      .finally(() => setLoading(false));
  }, [questionId]);

  const save = async () => {
    try {
      setSaving(true);
      setError(null);
      await updateAdminQuestion(questionId, { question, options, correct, explanation, transcript });
      onSaved();
      onClose();
    } catch (err) {
      setError(getApiErrorMessage(err, "Không lưu được."));
    } finally {
      setSaving(false);
    }
  };

  const area = "w-full resize-y rounded-xl border border-black/10 bg-white px-4 py-3 text-sm outline-none focus:border-black/30";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between border-b border-black/5 px-6 py-4">
          <div>
            <h2 className="font-serif text-lg font-semibold text-neutral-900">Sửa câu hỏi</h2>
            <p className="text-xs text-neutral-400">{meta}</p>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng" className="rounded-full p-1.5 text-neutral-400 hover:bg-neutral-100">
            <X size={16} />
          </button>
        </div>

        <div className="flex-1 space-y-5 overflow-y-auto px-6 py-5">
          {loading ? (
            <p className="py-10 text-center text-sm text-neutral-400">Đang tải...</p>
          ) : (
            <>
              <label className="block">
                <span className="text-xs font-medium text-neutral-500">Câu hỏi</span>
                <textarea value={question} onChange={(e) => setQuestion(e.target.value)} rows={4} className={`${area} mt-1`} />
              </label>

              <div>
                <span className="text-xs font-medium text-neutral-500">Đáp án (chọn vòng tròn để đặt đáp án đúng)</span>
                <ul className="mt-1 space-y-2">
                  {options.map((o, i) => (
                    <li key={i} className="flex items-center gap-3">
                      <input
                        type="radio"
                        name="correct"
                        checked={correct === LETTERS[i]}
                        onChange={() => setCorrect(LETTERS[i])}
                        aria-label={`Đáp án đúng là ${i + 1}`}
                      />
                      <span className="w-5 text-xs text-neutral-400">{i + 1}</span>
                      <input
                        value={o}
                        onChange={(e) => setOptions(options.map((x, j) => (j === i ? e.target.value : x)))}
                        className="flex-1 rounded-xl border border-black/10 px-4 py-2 text-sm outline-none focus:border-black/30"
                      />
                    </li>
                  ))}
                </ul>
              </div>

              <label className="block">
                <span className="text-xs font-medium text-neutral-500">Giải thích (hiện sau khi nộp bài)</span>
                <textarea value={explanation} onChange={(e) => setExplanation(e.target.value)} rows={3} className={`${area} mt-1`} />
              </label>

              <label className="block">
                <span className="text-xs font-medium text-neutral-500">
                  Transcript (bài nghe, tiếng Nhật, mỗi lượt nói một dòng)
                </span>
                <textarea
                  value={transcript}
                  onChange={(e) => setTranscript(e.target.value)}
                  rows={6}
                  placeholder="男：...&#10;女：..."
                  className={`${area} mt-1`}
                />
              </label>
            </>
          )}
          {error && <p className="text-xs text-red-600">{error}</p>}
        </div>

        <div className="flex justify-end gap-2 border-t border-black/5 px-6 py-4">
          <button type="button" onClick={onClose} className="rounded-full border border-black/10 px-5 py-2 text-sm text-neutral-600">
            Hủy
          </button>
          <button
            type="button"
            disabled={loading || saving}
            onClick={() => void save()}
            className="rounded-full bg-neutral-900 px-6 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {saving ? "Đang lưu..." : "Lưu"}
          </button>
        </div>
      </div>
    </div>
  );
}
