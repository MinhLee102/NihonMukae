"use client";

import { useEffect, useState } from "react";
import { Plus, Trash2, X } from "lucide-react";
import {
  createDictEntry,
  deleteDictEntry,
  fetchDictEntry,
  updateDictEntry,
  type DictEntryInput,
  type DictExampleInput,
} from "@/lib/api/admin";
import { getApiErrorMessage } from "@/lib/api/flashcard";

const input = "w-full rounded-xl border border-black/10 bg-white px-3 py-2 text-sm outline-none focus:border-black/30";
const label = "text-xs font-medium text-neutral-500";

const splitList = (s: string) =>
  s
    .split(/[,、;]/)
    .map((x) => x.trim())
    .filter(Boolean);

/** Thêm mới (entryId = null) hoặc sửa một mục từ điển: mặt chữ, cách đọc, Hán Việt, nghĩa và ví dụ. */
export function DictEntryDialog({
  entryId,
  onClose,
  onSaved,
}: {
  entryId: number | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const creating = entryId === null;
  const [word, setWord] = useState("");
  const [reading, setReading] = useState("");
  const [forms, setForms] = useState("");
  const [readings, setReadings] = useState("");
  const [usuallyKana, setUsuallyKana] = useState(false);
  const [pos, setPos] = useState("");
  const [jlpt, setJlpt] = useState("");
  const [hanViet, setHanViet] = useState("");
  const [meanings, setMeanings] = useState<string[]>([""]);
  const [examples, setExamples] = useState<DictExampleInput[]>([]);
  const [glossEn, setGlossEn] = useState<string[]>([]);
  const [meta, setMeta] = useState(creating ? "Mục mới" : "");
  const [loading, setLoading] = useState(!creating);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (entryId === null) return;
    fetchDictEntry(entryId)
      .then((d) => {
        const e = d.entry;
        setWord(e.kanji ?? "");
        setReading(e.kana);
        setForms(e.otherForms.join(", "));
        setReadings(e.otherReadings.join(", "));
        setUsuallyKana(d.usuallyKana);
        setPos(e.pos ?? "");
        setJlpt(e.jlptLevel ? String(e.jlptLevel) : "");
        setHanViet(e.hanViet ?? "");
        setMeanings(e.hasVietnamese ? e.meanings.map((m) => m.meaning) : [""]);
        setExamples(
          e.examples.map((x) => ({ ja: x.sentence, vi: x.translation, source: x.source, sourceId: x.sourceId }))
        );
        setGlossEn(d.glossEn);
        setMeta(`#${e.id} · ${d.priorityGroup}${d.edited ? " · đã sửa tay" : ""}`);
      })
      .catch((err) => setError(getApiErrorMessage(err, "Không tải được mục từ.")))
      .finally(() => setLoading(false));
  }, [entryId]);

  const payload = (): DictEntryInput => ({
    word: word.trim() || null,
    reading: reading.trim(),
    otherForms: splitList(forms),
    otherReadings: splitList(readings),
    usuallyKana,
    pos: pos.trim() || null,
    jlptLevel: jlpt ? Number(jlpt) : null,
    hanViet: hanViet.trim() || null,
    meanings: meanings.map((m) => m.trim()).filter(Boolean),
    examples: examples.filter((x) => x.ja.trim() || x.vi.trim()),
  });

  const save = async () => {
    try {
      setSaving(true);
      setError(null);
      if (entryId === null) await createDictEntry(payload());
      else await updateDictEntry(entryId, payload());
      onSaved();
      onClose();
    } catch (err) {
      setError(getApiErrorMessage(err, "Không lưu được."));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (entryId === null) return;
    if (!window.confirm("Xoá hẳn mục từ này cùng nghĩa, ví dụ và đóng góp của nó? Không hoàn tác được.")) return;
    try {
      setSaving(true);
      await deleteDictEntry(entryId);
      onSaved();
      onClose();
    } catch (err) {
      setError(getApiErrorMessage(err, "Không xoá được."));
      setSaving(false);
    }
  };

  /** Sửa chữ của một câu lấy từ Tatoeba thì không còn là câu Tatoeba nguyên bản nữa. */
  const editExample = (i: number, patch: Partial<DictExampleInput>) =>
    setExamples(
      examples.map((x, j) =>
        j !== i ? x : { ...x, ...patch, ...(x.source === "tatoeba" ? { source: "admin", sourceId: null } : {}) }
      )
    );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        className="flex max-h-[92vh] w-full max-w-3xl flex-col rounded-2xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between border-b border-black/5 px-6 py-4">
          <div>
            <h2 className="font-serif text-lg font-semibold text-neutral-900">
              {creating ? "Thêm mục từ" : "Sửa mục từ"}
            </h2>
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
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <label className="block">
                  <span className={label}>Chữ Hán / mặt chữ (bỏ trống nếu từ thuần kana)</span>
                  <input value={word} onChange={(e) => setWord(e.target.value)} className={`${input} mt-1`} />
                </label>
                <label className="block">
                  <span className={label}>Cách đọc (kana) *</span>
                  <input value={reading} onChange={(e) => setReading(e.target.value)} className={`${input} mt-1`} />
                </label>
                <label className="block">
                  <span className={label}>Dạng viết khác (ngăn cách bằng dấu phẩy)</span>
                  <input value={forms} onChange={(e) => setForms(e.target.value)} className={`${input} mt-1`} />
                </label>
                <label className="block">
                  <span className={label}>Cách đọc khác (ngăn cách bằng dấu phẩy)</span>
                  <input value={readings} onChange={(e) => setReadings(e.target.value)} className={`${input} mt-1`} />
                </label>
                <label className="block">
                  <span className={label}>Hán Việt</span>
                  <input value={hanViet} onChange={(e) => setHanViet(e.target.value)} className={`${input} mt-1`} />
                </label>
                <label className="block">
                  <span className={label}>Loại từ</span>
                  <input value={pos} onChange={(e) => setPos(e.target.value)} className={`${input} mt-1`} placeholder="danh từ, động từ..." />
                </label>
                <label className="block">
                  <span className={label}>Cấp JLPT</span>
                  <select value={jlpt} onChange={(e) => setJlpt(e.target.value)} className={`${input} mt-1`}>
                    <option value="">Không có</option>
                    {[5, 4, 3, 2, 1].map((n) => (
                      <option key={n} value={n}>
                        N{n}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="flex items-center gap-2 self-end pb-2 text-sm text-neutral-600">
                  <input type="checkbox" checked={usuallyKana} onChange={(e) => setUsuallyKana(e.target.checked)} />
                  Thường viết bằng kana
                </label>
              </div>

              <div>
                <div className="flex items-center justify-between">
                  <span className={label}>Nghĩa tiếng Việt (mỗi dòng một nghĩa)</span>
                  <button
                    type="button"
                    onClick={() => setMeanings([...meanings, ""])}
                    className="flex items-center gap-1 text-xs text-neutral-500 hover:text-neutral-800"
                  >
                    <Plus size={13} /> Thêm nghĩa
                  </button>
                </div>
                {glossEn.length > 0 && (
                  <p className="mt-1 rounded-lg bg-[#F5F3EE] px-3 py-2 text-xs text-neutral-500">
                    JMdict: {glossEn.join(" | ")}
                  </p>
                )}
                <ul className="mt-2 space-y-2">
                  {meanings.map((m, i) => (
                    <li key={i} className="flex items-center gap-2">
                      <input
                        value={m}
                        onChange={(e) => setMeanings(meanings.map((x, j) => (j === i ? e.target.value : x)))}
                        className={input}
                      />
                      <button
                        type="button"
                        onClick={() => setMeanings(meanings.filter((_, j) => j !== i))}
                        aria-label="Xoá nghĩa"
                        className="rounded-full p-2 text-neutral-400 hover:bg-black/5 hover:text-red-600"
                      >
                        <Trash2 size={15} />
                      </button>
                    </li>
                  ))}
                </ul>
              </div>

              <div>
                <div className="flex items-center justify-between">
                  <span className={label}>Câu ví dụ</span>
                  <button
                    type="button"
                    onClick={() => setExamples([...examples, { ja: "", vi: "", source: "admin", sourceId: null }])}
                    className="flex items-center gap-1 text-xs text-neutral-500 hover:text-neutral-800"
                  >
                    <Plus size={13} /> Thêm câu
                  </button>
                </div>
                <ul className="mt-2 space-y-3">
                  {examples.map((x, i) => (
                    <li key={i} className="flex items-start gap-2 rounded-xl border border-black/5 p-3">
                      <div className="flex-1 space-y-2">
                        <input
                          value={x.ja}
                          onChange={(e) => editExample(i, { ja: e.target.value })}
                          placeholder="Câu tiếng Nhật"
                          className={input}
                        />
                        <input
                          value={x.vi}
                          onChange={(e) => editExample(i, { vi: e.target.value })}
                          placeholder="Bản dịch tiếng Việt"
                          className={input}
                        />
                        <p className="text-[11px] text-neutral-400">
                          Nguồn: {x.source === "tatoeba" ? `Tatoeba #${x.sourceId}` : x.source === "ai" ? "AI viết" : "Admin"}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => setExamples(examples.filter((_, j) => j !== i))}
                        aria-label="Xoá câu"
                        className="rounded-full p-2 text-neutral-400 hover:bg-black/5 hover:text-red-600"
                      >
                        <Trash2 size={15} />
                      </button>
                    </li>
                  ))}
                  {examples.length === 0 && <li className="text-xs text-neutral-400">Chưa có câu ví dụ.</li>}
                </ul>
              </div>
            </>
          )}
          {error && <p className="text-xs text-red-600">{error}</p>}
        </div>

        <div className="flex items-center justify-between gap-2 border-t border-black/5 px-6 py-4">
          <div>
            {!creating && (
              <button
                type="button"
                disabled={saving}
                onClick={() => void remove()}
                className="flex items-center gap-1.5 rounded-full px-4 py-2 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
              >
                <Trash2 size={14} /> Xoá mục từ
              </button>
            )}
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={onClose} className="rounded-full border border-black/10 px-5 py-2 text-sm text-neutral-600">
              Hủy
            </button>
            <button
              type="button"
              disabled={loading || saving || !reading.trim()}
              onClick={() => void save()}
              className="rounded-full bg-neutral-900 px-6 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {saving ? "Đang lưu..." : "Lưu"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
