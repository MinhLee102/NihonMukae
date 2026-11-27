"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { BookOpen, Search, X } from "lucide-react";
import { GrammarDetailView } from "@/components/grammar/GrammarDetailView";
import { fetchGrammarLevel, type GrammarSummary } from "@/lib/api/grammar";

const LEVELS = [5, 4, 3, 2, 1];

/** Cấp được chọn lần trước, để quay lại trang không phải chọn lại. */
const LEVEL_KEY = "grammar-level";

function readLevel(): number {
  try {
    const v = Number(window.localStorage.getItem(LEVEL_KEY));
    return LEVELS.includes(v) ? v : 5;
  } catch {
    return 5;
  }
}

/** Trang Ngữ pháp: duyệt mẫu theo cấp N5 đến N1, gom theo nhóm ý nghĩa, xem chi tiết từng mẫu. */
export default function GrammarPage() {
  const [level, setLevel] = useState(5);
  const [points, setPoints] = useState<GrammarSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [filter, setFilter] = useState("");
  const [selected, setSelected] = useState<number | null>(null);
  const detailRef = useRef<HTMLDivElement>(null);

  // Trên điện thoại phần chi tiết nằm dưới danh sách: cuộn xuống khi chọn mẫu
  const select = (id: number) => {
    setSelected(id);
    if (window.matchMedia("(max-width: 767px)").matches) {
      requestAnimationFrame(() => detailRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
    }
  };

  useEffect(() => setLevel(readLevel()), []);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setFailed(false);
    setSelected(null);
    try {
      window.localStorage.setItem(LEVEL_KEY, String(level));
    } catch {
      // không lưu được thì lần sau mở lại N5
    }
    fetchGrammarLevel(level)
      .then((list) => alive && setPoints(list))
      .catch(() => alive && setFailed(true))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [level]);

  // Gom theo nhóm ý nghĩa, giữ thứ tự xuất hiện; mẫu chính của cấp khác (cấp phụ) gom cuối
  const groups = useMemo(() => {
    const q = filter.trim().toLowerCase();
    const shown = q
      ? points.filter(
          (p) =>
            p.pattern.toLowerCase().includes(q) ||
            (p.reading ?? "").includes(q) ||
            p.meaning.toLowerCase().includes(q)
        )
      : points;
    const map = new Map<string, GrammarSummary[]>();
    for (const p of shown) {
      const key = p.level !== level ? `Cũng dùng ở N${level}` : p.category || "Khác";
      const list = map.get(key) ?? [];
      list.push(p);
      map.set(key, list);
    }
    return Array.from(map.entries());
  }, [points, filter, level]);

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-5 p-4">
      <div className="flex flex-col gap-1">
        <h1 className="font-serif text-2xl font-semibold text-neutral-900">Ngữ pháp</h1>
        <p className="text-sm text-neutral-500">
          Các mẫu ngữ pháp theo cấp độ JLPT, kèm cách chia, giải thích và ví dụ. Muốn tìm một mẫu cụ thể, dùng tab Ngữ
          pháp ở trang Từ điển.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {LEVELS.map((l) => (
          <button
            key={l}
            type="button"
            onClick={() => setLevel(l)}
            className={`rounded-full px-4 py-1.5 text-sm font-semibold transition ${
              level === l ? "bg-neutral-900 text-white" : "border border-black/10 bg-white text-neutral-600 hover:bg-neutral-50"
            }`}
          >
            N{l}
          </button>
        ))}
        {!loading && !failed && <span className="text-xs text-neutral-400">{points.length} mẫu</span>}
        <div className="flex w-full items-center gap-2 rounded-full border border-black/10 bg-white px-3 py-1.5 sm:ml-auto sm:w-64">
          <Search size={14} className="shrink-0 text-neutral-400" />
          <input
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder={`Lọc trong N${level}...`}
            className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-neutral-400"
          />
          {filter && (
            <button type="button" onClick={() => setFilter("")} aria-label="Xoá" className="text-neutral-400">
              <X size={14} />
            </button>
          )}
        </div>
      </div>

      {loading ? (
        <p className="py-16 text-center text-sm text-neutral-400">Đang tải...</p>
      ) : failed ? (
        <p className="py-16 text-center text-sm text-neutral-400">Không tải được danh sách. Thử tải lại trang.</p>
      ) : points.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-2 py-16 text-neutral-400">
          <BookOpen size={36} strokeWidth={1.5} />
          <p className="text-sm">N{level} chưa có mẫu ngữ pháp nào.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 items-start gap-5 md:grid-cols-5">
          <div className="flex max-h-[70vh] flex-col gap-4 overflow-y-auto rounded-2xl border border-black/5 bg-white p-4 shadow-sm md:col-span-2">
            {groups.length === 0 && <p className="py-6 text-center text-sm text-neutral-400">Không có mẫu nào khớp.</p>}
            {groups.map(([name, list]) => (
              <div key={name}>
                <p className="mb-1.5 px-2 text-xs font-bold uppercase tracking-wider text-neutral-400">{name}</p>
                <div className="flex flex-col">
                  {list.map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      onClick={() => select(p.id)}
                      className={`flex flex-col items-start rounded-xl px-3 py-2 text-left transition ${
                        p.id === selected ? "bg-sky-50/70 ring-1 ring-sky-200" : "hover:bg-neutral-50"
                      }`}
                    >
                      <span className="font-bold text-neutral-900">{p.pattern}</span>
                      <span className="line-clamp-1 text-xs text-neutral-500">{p.meaning}</span>
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <div ref={detailRef} className="scroll-mt-4 rounded-2xl border border-black/5 bg-white p-6 shadow-sm md:col-span-3">
            {selected ? (
              <GrammarDetailView
                id={selected}
                onSearch={(pattern) => {
                  setFilter(pattern.replace(/[〜～]/g, ""));
                }}
              />
            ) : (
              <p className="py-10 text-center text-sm text-neutral-400">Chọn một mẫu ở danh sách để xem chi tiết.</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
