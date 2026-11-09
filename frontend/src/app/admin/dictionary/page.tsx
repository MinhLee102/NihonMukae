"use client";

import { useCallback, useEffect, useState } from "react";
import { Pencil, Plus, Search } from "lucide-react";
import { DictEntryDialog } from "@/components/admin/DictEntryDialog";
import {
  fetchDictEntries,
  fetchDictSummary,
  type DictEntryRow,
  type DictSummary,
  type Page,
} from "@/lib/api/admin";

const PAGE_SIZE = 20;

const FILTERS = [
  { id: "", label: "Tất cả" },
  { id: "no_vi", label: "Chưa có nghĩa Việt" },
  { id: "edited", label: "Đã sửa tay" },
  { id: "custom", label: "Tự thêm" },
];

export default function AdminDictionaryPage() {
  const [summary, setSummary] = useState<DictSummary | null>(null);
  const [page, setPage] = useState<Page<DictEntryRow> | null>(null);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("");
  const [pageIndex, setPageIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);
  // undefined = đóng, null = thêm mới, số = sửa mục có id đó
  const [editing, setEditing] = useState<number | null | undefined>(undefined);

  const load = useCallback(async () => {
    try {
      const [s, p] = await Promise.all([
        fetchDictSummary(),
        fetchDictEntries({ q: search || undefined, filter: filter || undefined, page: pageIndex, size: PAGE_SIZE }),
      ]);
      setSummary(s);
      setPage(p);
      setError(null);
    } catch {
      setError("Không tải được dữ liệu từ điển.");
    }
  }, [search, filter, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  const cards = [
    { label: "Tổng số mục", value: summary?.entries },
    { label: "Có nghĩa tiếng Việt", value: summary?.withVietnamese },
    { label: "Đã sửa tay", value: summary?.edited },
    { label: "Tự thêm", value: summary?.custom },
  ];

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-serif text-2xl font-semibold text-neutral-900">Từ điển</h1>
        <button
          type="button"
          onClick={() => setEditing(null)}
          className="flex items-center gap-1.5 rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
        >
          <Plus size={15} /> Thêm mục từ
        </button>
      </div>

      <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {cards.map((c) => (
          <div key={c.label} className="rounded-2xl border border-black/5 bg-white p-5">
            <p className="text-xs text-neutral-400">{c.label}</p>
            <p className="mt-1 text-2xl font-semibold text-neutral-900">{c.value?.toLocaleString("vi-VN") ?? "-"}</p>
          </div>
        ))}
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setPageIndex(0);
            setSearch(query.trim());
          }}
          className="flex min-w-[260px] flex-1 items-center gap-2 rounded-full border border-black/10 bg-white px-4 py-2"
        >
          <Search size={16} className="text-neutral-400" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Tìm theo chữ Hán, kana, nghĩa, Hán Việt hoặc id, nhấn Enter"
            className="min-w-0 flex-1 bg-transparent text-sm outline-none"
          />
        </form>
        <div className="flex gap-1">
          {FILTERS.map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => {
                setFilter(f.id);
                setPageIndex(0);
              }}
              className={`rounded-full px-3.5 py-1.5 text-sm transition ${
                filter === f.id ? "bg-neutral-900 text-white" : "text-neutral-500 hover:bg-black/5"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      <div className="mt-4 overflow-x-auto rounded-2xl border border-black/5 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-black/5 text-xs uppercase tracking-wide text-neutral-400">
            <tr>
              <th className="px-4 py-3">Từ</th>
              <th className="px-4 py-3">Nghĩa</th>
              <th className="px-4 py-3">JLPT</th>
              <th className="px-4 py-3">Nhóm</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {page?.content.map((e) => (
              <tr key={e.id} className="border-b border-black/5 last:border-0">
                <td className="px-4 py-3">
                  <p className="font-medium text-neutral-900">{e.headword}</p>
                  <p className="text-xs text-neutral-400">
                    {e.reading !== e.headword && `${e.reading} · `}#{e.id}
                  </p>
                </td>
                <td className="max-w-md px-4 py-3">
                  <p className={`line-clamp-2 ${e.hasVietnamese ? "text-neutral-700" : "text-neutral-400"}`}>
                    {e.preview ?? "-"}
                  </p>
                  {!e.hasVietnamese && <p className="text-[11px] text-amber-600">Chưa có bản dịch Việt (hiện nghĩa Anh)</p>}
                </td>
                <td className="px-4 py-3 text-neutral-600">{e.jlptLevel ? `N${e.jlptLevel}` : "-"}</td>
                <td className="px-4 py-3 text-xs text-neutral-500">
                  {e.priorityGroup}
                  {e.edited && <span className="ml-1.5 rounded-full bg-sky-50 px-2 py-0.5 text-sky-700">sửa tay</span>}
                  {e.custom && <span className="ml-1.5 rounded-full bg-violet-50 px-2 py-0.5 text-violet-700">tự thêm</span>}
                </td>
                <td className="px-4 py-3 text-right">
                  <button
                    type="button"
                    onClick={() => setEditing(e.id)}
                    title="Sửa"
                    className="rounded-full p-2 text-neutral-400 transition hover:bg-black/5 hover:text-neutral-800"
                  >
                    <Pencil size={16} />
                  </button>
                </td>
              </tr>
            ))}
            {page && page.content.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-neutral-400">
                  Không có mục nào khớp.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {page && page.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-neutral-500">
          <span>
            Trang {page.page + 1}/{page.totalPages} ({page.totalElements.toLocaleString("vi-VN")} mục)
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={pageIndex === 0}
              onClick={() => setPageIndex(pageIndex - 1)}
              className="rounded-full border border-black/10 px-4 py-1.5 disabled:opacity-40"
            >
              Trước
            </button>
            <button
              type="button"
              disabled={pageIndex + 1 >= page.totalPages}
              onClick={() => setPageIndex(pageIndex + 1)}
              className="rounded-full border border-black/10 px-4 py-1.5 disabled:opacity-40"
            >
              Sau
            </button>
          </div>
        </div>
      )}

      {editing !== undefined && (
        <DictEntryDialog entryId={editing} onClose={() => setEditing(undefined)} onSaved={() => void load()} />
      )}
    </div>
  );
}
