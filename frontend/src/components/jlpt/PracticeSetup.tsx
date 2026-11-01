"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Check, ChevronDown, Clock, Minus, Plus, TimerOff } from "lucide-react";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import {
  SECTION_LABELS,
  SECTION_ORDER,
  getQuestionTypes,
  startSession,
  stashSessionWarnings,
  type FeedbackMode,
  type QuestionTypeDto,
  type Section,
} from "@/lib/api/jlpt";

const LEVELS = [5, 4, 3, 2, 1];
const COUNT_PRESETS = [10, 20, 30, 50];
const MAX_COUNT = 200;
const DEFAULT_SECTIONS: Section[] = ["goi", "bunpou"];

/** "Luyện theo dạng bài": pick a level, any mix of section types, a free question count and a timer. */
export function PracticeSetup({ initialLevel = 3 }: { initialLevel?: number }) {
  const router = useRouter();
  const [level, setLevel] = useState(initialLevel);
  const [types, setTypes] = useState<QuestionTypeDto[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [expanded, setExpanded] = useState<Set<Section>>(new Set(DEFAULT_SECTIONS));
  const [count, setCount] = useState(10);
  const [limited, setLimited] = useState(true);
  const [minutes, setMinutes] = useState(30);
  const [feedback, setFeedback] = useState<FeedbackMode>("after");
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getQuestionTypes(level)
      .then((list) => {
        if (cancelled) return;
        setTypes(list);
        setSelected(new Set(list.filter((t) => DEFAULT_SECTIONS.includes(t.section) && t.available > 0).map((t) => t.id)));
      })
      .catch((err) => !cancelled && setError(getApiErrorMessage(err, "Không tải được danh sách dạng bài.")))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [level]);

  const bySection = useMemo(() => {
    const map = new Map<Section, QuestionTypeDto[]>();
    for (const section of SECTION_ORDER) {
      const list = types.filter((t) => t.section === section);
      if (list.length > 0) map.set(section, list);
    }
    return map;
  }, [types]);

  const availableTotal = useMemo(
    () => types.filter((t) => selected.has(t.id)).reduce((sum, t) => sum + t.available, 0),
    [types, selected]
  );

  const toggleType = (id: number) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const toggleSection = (section: Section) => {
    const usable = (bySection.get(section) ?? []).filter((t) => t.available > 0);
    const allOn = usable.length > 0 && usable.every((t) => selected.has(t.id));
    setSelected((prev) => {
      const next = new Set(prev);
      usable.forEach((t) => (allOn ? next.delete(t.id) : next.add(t.id)));
      return next;
    });
  };

  const toggleExpanded = (section: Section) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(section)) next.delete(section);
      else next.add(section);
      return next;
    });

  const setCountClamped = (value: number) => setCount(Math.min(Math.max(Number.isFinite(value) ? value : 1, 1), MAX_COUNT));

  const start = async () => {
    try {
      setStarting(true);
      setError(null);
      const session = await startSession({
        mode: "practice",
        level,
        typeIds: Array.from(selected),
        questionCount: count,
        timeLimitMin: limited ? minutes : null,
        feedbackMode: feedback,
      });
      stashSessionWarnings(session.id, session.warnings);
      router.push(`/jlpt/session/${session.id}`);
    } catch (err) {
      setError(getApiErrorMessage(err));
      setStarting(false);
    }
  };

  const canStart = selected.size > 0 && availableTotal > 0 && !starting;

  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1fr_320px]">
      <div className="rounded-2xl border border-black/5 bg-white p-6">
        <p className="mb-3 text-xs font-semibold text-neutral-900">1. Chọn cấp độ</p>
        <div className="grid grid-cols-5 gap-2">
          {LEVELS.map((l) => (
            <button
              key={l}
              type="button"
              onClick={() => setLevel(l)}
              className={`rounded-xl border py-2.5 text-sm font-medium transition ${
                level === l ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/10 text-neutral-600 hover:border-black/30"
              }`}
            >
              N{l}
            </button>
          ))}
        </div>

        <p className="mb-3 mt-7 text-xs font-semibold text-neutral-900">2. Chọn một hoặc nhiều dạng bài</p>
        {loading && <p className="py-6 text-sm text-neutral-400">Đang tải...</p>}

        <div className="flex flex-col gap-2.5">
          {Array.from(bySection.entries()).map(([section, list]) => {
            const usable = list.filter((t) => t.available > 0);
            const chosen = list.filter((t) => selected.has(t.id)).length;
            const allOn = usable.length > 0 && usable.every((t) => selected.has(t.id));
            const isOpen = expanded.has(section);
            const sectionAvailable = list.reduce((sum, t) => sum + t.available, 0);
            return (
              <div
                key={section}
                className={`rounded-2xl border transition ${chosen > 0 ? "border-black/25 bg-[#F5F3EE]" : "border-black/5 bg-white"}`}
              >
                <div className="flex items-center gap-3 px-4 py-3">
                  <button
                    type="button"
                    onClick={() => toggleSection(section)}
                    disabled={usable.length === 0}
                    aria-label={`Chọn tất cả ${SECTION_LABELS[section].vi}`}
                    className={`flex h-5 w-5 shrink-0 items-center justify-center rounded border transition disabled:opacity-40 ${
                      chosen > 0 ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/20 bg-white"
                    }`}
                  >
                    {allOn ? <Check size={13} /> : chosen > 0 ? <Minus size={13} /> : null}
                  </button>
                  <button
                    type="button"
                    onClick={() => toggleExpanded(section)}
                    className="flex min-w-0 flex-1 items-center gap-3 text-left"
                  >
                    <span className="text-sm font-medium text-neutral-900">{SECTION_LABELS[section].vi}</span>
                    <span className="text-xs text-neutral-400">{SECTION_LABELS[section].ja}</span>
                    <span className="ml-auto text-xs text-neutral-400">
                      {chosen > 0 ? `${chosen}/${list.length} dạng · ` : ""}
                      {sectionAvailable} câu
                    </span>
                    <ChevronDown size={16} className={`text-neutral-400 transition ${isOpen ? "rotate-180" : ""}`} />
                  </button>
                </div>

                {isOpen && (
                  <div className="grid grid-cols-1 gap-1.5 border-t border-black/5 px-4 py-3 sm:grid-cols-2">
                    {list.map((t) => {
                      const on = selected.has(t.id);
                      const empty = t.available === 0;
                      return (
                        <button
                          key={t.id}
                          type="button"
                          disabled={empty}
                          onClick={() => toggleType(t.id)}
                          className={`flex items-center gap-2.5 rounded-xl px-3 py-2 text-left text-sm transition disabled:cursor-not-allowed disabled:opacity-40 ${
                            on ? "bg-white shadow-sm" : "hover:bg-white/70"
                          }`}
                        >
                          <span
                            className={`flex h-4 w-4 shrink-0 items-center justify-center rounded border ${
                              on ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/20 bg-white"
                            }`}
                          >
                            {on && <Check size={11} />}
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-neutral-800">{t.nameVi}</span>
                            {t.nameJa && <span className="block truncate text-[11px] text-neutral-400">{t.nameJa}</span>}
                          </span>
                          <span className="shrink-0 text-[11px] text-neutral-400">{empty ? "chưa có" : `${t.available} câu`}</span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      <div className="h-fit rounded-2xl border border-black/5 bg-white p-6 lg:sticky lg:top-0">
        <p className="mb-5 text-sm font-semibold text-neutral-900">Thiết lập phiên luyện</p>

        <p className="mb-2 text-[10px] tracking-widest text-neutral-400">SỐ CÂU HỎI</p>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setCountClamped(count - 1)}
            aria-label="Giảm số câu"
            className="flex h-9 w-9 items-center justify-center rounded-lg border border-black/10 text-neutral-600 hover:border-black/30"
          >
            <Minus size={14} />
          </button>
          <input
            type="number"
            min={1}
            max={MAX_COUNT}
            value={count}
            onChange={(e) => setCountClamped(parseInt(e.target.value, 10))}
            aria-label="Số câu hỏi"
            className="h-9 w-full min-w-0 rounded-lg border border-black/10 text-center text-sm font-medium outline-none focus:border-black/40"
          />
          <button
            type="button"
            onClick={() => setCountClamped(count + 1)}
            aria-label="Tăng số câu"
            className="flex h-9 w-9 items-center justify-center rounded-lg border border-black/10 text-neutral-600 hover:border-black/30"
          >
            <Plus size={14} />
          </button>
        </div>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {COUNT_PRESETS.map((n) => (
            <button
              key={n}
              type="button"
              onClick={() => setCount(n)}
              className={`rounded-lg border px-3 py-1 text-xs transition ${
                count === n ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/10 text-neutral-500 hover:border-black/30"
              }`}
            >
              {n}
            </button>
          ))}
        </div>
        <p className={`mt-2 text-[11px] ${count > availableTotal ? "text-amber-600" : "text-neutral-400"}`}>
          Kho có {availableTotal} câu cho các dạng đã chọn
          {count > availableTotal && availableTotal > 0 ? `, sẽ lấy tối đa ${availableTotal} câu` : ""}.
        </p>

        <p className="mb-2 mt-6 text-[10px] tracking-widest text-neutral-400">THỜI GIAN</p>
        <button
          type="button"
          onClick={() => setLimited((v) => !v)}
          className={`flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-left transition ${
            limited ? "border-black/25 bg-[#F5F3EE]" : "border-black/10 bg-white"
          }`}
        >
          <span className="flex h-9 w-9 items-center justify-center rounded-full bg-white shadow-sm">
            {limited ? <Clock size={16} /> : <TimerOff size={16} />}
          </span>
          <span>
            <span className="block text-sm font-medium text-neutral-900">{limited ? "Có giới hạn" : "Không hết hạn"}</span>
            <span className="block text-[11px] text-neutral-400">Nhấn để chuyển chế độ</span>
          </span>
        </button>
        {limited && (
          <div className="mt-3 flex items-center gap-3">
            <input
              type="range"
              min={5}
              max={180}
              step={5}
              value={minutes}
              onChange={(e) => setMinutes(parseInt(e.target.value, 10))}
              aria-label="Số phút"
              className="h-1 flex-1 accent-neutral-900"
            />
            <span className="w-14 shrink-0 text-right text-xs font-medium text-neutral-700">{minutes} phút</span>
          </div>
        )}

        <p className="mb-2 mt-6 text-[10px] tracking-widest text-neutral-400">ĐÁP ÁN</p>
        <div className="grid grid-cols-2 gap-1.5">
          {(
            [
              ["after", "Sau khi nộp"],
              ["instant", "Hiện ngay"],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              onClick={() => setFeedback(value)}
              className={`rounded-lg border py-2 text-xs transition ${
                feedback === value ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/10 text-neutral-600 hover:border-black/30"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        {feedback === "instant" && (
          <p className="mt-1.5 text-[11px] text-neutral-400">Câu đã xem đáp án sẽ bị khóa, không đổi được.</p>
        )}

        <dl className="mt-6 space-y-2 border-t border-black/5 pt-4 text-xs">
          <div className="flex justify-between">
            <dt className="text-neutral-400">Cấp độ</dt>
            <dd className="font-medium text-neutral-800">N{level}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-neutral-400">Dạng bài</dt>
            <dd className="font-medium text-neutral-800">{selected.size} mục</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-neutral-400">Thời lượng</dt>
            <dd className="font-medium text-neutral-800">{limited ? `${minutes} phút` : "Không giới hạn"}</dd>
          </div>
        </dl>

        {error && <p className="mt-3 text-xs text-red-600">{error}</p>}

        <button
          type="button"
          onClick={start}
          disabled={!canStart}
          className="mt-4 w-full rounded-full bg-neutral-900 py-3 text-sm font-medium text-white transition disabled:opacity-40"
        >
          {starting ? "Đang tạo phiên..." : "Bắt đầu làm bài"}
        </button>
      </div>
    </div>
  );
}
