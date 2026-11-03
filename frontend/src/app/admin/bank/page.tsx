"use client";

import { useCallback, useEffect, useState } from "react";
import { Headphones, Pencil, Search, Trash2, Upload } from "lucide-react";
import {
  deleteAdminTest,
  deleteBankQuestion,
  fetchAdminTests,
  purgeHiddenQuestions,
  type AdminTest,
  fetchBankQuestions,
  fetchBankSummary,
  type AdminQuestion,
  type BankSummary,
  type Page,
} from "@/lib/api/admin";
import { SECTION_LABELS, SECTION_ORDER, type Section } from "@/lib/api/jlpt";
import { ImportDialog } from "@/components/admin/ImportDialog";
import { EditQuestionDialog } from "@/components/admin/EditQuestionDialog";

const PAGE_SIZE = 20;
const LEVELS = [5, 4, 3, 2, 1];

/** Question text carries markup such as <u>; show it as plain text in the table. */
function plain(text: string) {
  return text.replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();
}

export default function AdminBankPage() {
  const [summary, setSummary] = useState<BankSummary | null>(null);
  const [page, setPage] = useState<Page<AdminQuestion> | null>(null);
  const [level, setLevel] = useState<number | undefined>();
  const [section, setSection] = useState<string | undefined>();
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [pageIndex, setPageIndex] = useState(0);
  const [importing, setImporting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [tests, setTests] = useState<AdminTest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [s, p, t] = await Promise.all([
        fetchBankSummary(),
        fetchBankQuestions({ level, section, q: search || undefined, page: pageIndex, size: PAGE_SIZE }),
        fetchAdminTests(),
      ]);
      setSummary(s);
      setTests(t);
      setPage(p);
      setError(null);
    } catch {
      setError("Không tải được ngân hàng đề.");
    }
  }, [level, section, search, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(q: AdminQuestion) {
    if (!window.confirm(`Xóa câu #${q.id}? Câu này sẽ không còn xuất hiện trong đề luyện tập.`)) return;
    try {
      await deleteBankQuestion(q.id);
      await load();
    } catch {
      setError("Không xóa được câu hỏi này.");
    }
  }

  async function removeTest(t: AdminTest) {
    const name = t.title || `N${t.level} ${t.year ?? ""}/${t.month ?? ""}`;
    if (
      !window.confirm(
        `Xóa hẳn đề "${name}" cùng ${t.questionCount} câu hỏi? Không thể hoàn tác. Lịch sử làm bài của người học vẫn được giữ.`
      )
    )
      return;
    try {
      await deleteAdminTest(t.id);
      setNotice(`Đã xóa đề "${name}".`);
      setPageIndex(0);
      await load();
    } catch {
      setError("Không xóa được đề này.");
    }
  }

  async function purgeHidden() {
    if (!window.confirm("Xóa hẳn mọi câu hỏi đã bị ẩn trước đó? Không thể hoàn tác.")) return;
    try {
      const n = await purgeHiddenQuestions();
      setNotice(`Đã dọn ${n} câu đã ẩn.`);
      await load();
    } catch {
      setError("Không dọn được câu đã ẩn.");
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-serif text-2xl font-semibold text-neutral-900">Ngân hàng đề</h1>
        <button
          type="button"
          onClick={() => setImporting(true)}
          className="flex items-center gap-2 rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
        >
          <Upload size={15} /> Import JSON
        </button>
      </div>

      {notice && <p className="mt-4 text-sm text-emerald-700">{notice}</p>}

      <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-6">
        <div className="rounded-2xl border border-black/5 bg-white p-5">
          <p className="text-xs text-neutral-400">Tổng số câu</p>
          <p className="mt-1 text-2xl font-semibold text-neutral-900">{summary?.total ?? "-"}</p>
        </div>
        {SECTION_ORDER.map((id: Section) => (
          <button
            key={id}
            type="button"
            onClick={() => {
              setSection(section === id ? undefined : id);
              setPageIndex(0);
            }}
            className={`rounded-2xl border p-5 text-left transition ${
              section === id ? "border-neutral-900 bg-white" : "border-black/5 bg-white hover:border-black/20"
            }`}
          >
            <p className="text-xs text-neutral-400">
              {SECTION_LABELS[id].ja} {SECTION_LABELS[id].vi}
            </p>
            <p className="mt-1 text-2xl font-semibold text-neutral-900">{summary?.bySection[id] ?? 0}</p>
          </button>
        ))}
      </div>

      <div className="mt-6 rounded-2xl border border-black/5 bg-white">
        <div className="flex items-center justify-between border-b border-black/5 px-4 py-3">
          <p className="text-sm font-medium text-neutral-800">Đề thi ({tests.length})</p>
          <button
            type="button"
            onClick={() => void purgeHidden()}
            className="rounded-full border border-black/10 px-3.5 py-1.5 text-xs text-neutral-500 transition hover:border-red-300 hover:text-red-600"
          >
            Dọn câu đã ẩn
          </button>
        </div>
        {tests.length === 0 ? (
          <p className="px-4 py-6 text-center text-sm text-neutral-400">Chưa có đề nào.</p>
        ) : (
          <ul>
            {tests.map((t) => (
              <li key={t.id} className="flex items-center gap-4 border-b border-black/5 px-4 py-3 last:border-0">
                <span className="w-10 text-sm font-medium text-neutral-800">N{t.level}</span>
                <span className="min-w-0 flex-1 truncate text-sm text-neutral-700">
                  {t.title || `Đề ${t.year ?? "?"}/${t.month ?? "?"}`}
                </span>
                {t.hasAudio && <Headphones size={14} className="text-neutral-400" />}
                <span className="text-xs text-neutral-400">
                  {t.activeCount}/{t.questionCount} câu
                </span>
                <button
                  type="button"
                  onClick={() => void removeTest(t)}
                  title="Xóa cả đề"
                  className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs text-neutral-400 transition hover:bg-red-50 hover:text-red-600"
                >
                  <Trash2 size={14} /> Xóa cả đề
                </button>
              </li>
            ))}
          </ul>
        )}
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
            placeholder="Tìm trong nội dung câu hỏi, nhấn Enter"
            className="min-w-0 flex-1 bg-transparent text-sm outline-none"
          />
        </form>
        <div className="flex gap-1">
          <button
            type="button"
            onClick={() => {
              setLevel(undefined);
              setPageIndex(0);
            }}
            className={`rounded-full px-3.5 py-1.5 text-sm transition ${
              level === undefined ? "bg-neutral-900 text-white" : "text-neutral-500 hover:bg-black/5"
            }`}
          >
            Tất cả
          </button>
          {LEVELS.map((l) => (
            <button
              key={l}
              type="button"
              onClick={() => {
                setLevel(l);
                setPageIndex(0);
              }}
              className={`rounded-full px-3.5 py-1.5 text-sm transition ${
                level === l ? "bg-neutral-900 text-white" : "text-neutral-500 hover:bg-black/5"
              }`}
            >
              N{l} <span className="opacity-60">{summary?.byLevel[l] ?? 0}</span>
            </button>
          ))}
        </div>
      </div>

      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      <div className="mt-4 overflow-x-auto rounded-2xl border border-black/5 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-black/5 text-xs uppercase tracking-wide text-neutral-400">
            <tr>
              <th className="px-4 py-3">ID</th>
              <th className="px-4 py-3">Cấp</th>
              <th className="px-4 py-3">Dạng bài</th>
              <th className="px-4 py-3">Nội dung</th>
              <th className="px-4 py-3">Đáp án</th>
              <th className="px-4 py-3">Nguồn</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {page?.content.map((q) => (
              <tr key={q.id} className="border-b border-black/5 last:border-0">
                <td className="px-4 py-3 text-neutral-400">{q.id}</td>
                <td className="px-4 py-3 font-medium text-neutral-800">N{q.level}</td>
                <td className="px-4 py-3 text-neutral-600">{q.typeName}</td>
                <td className="max-w-md px-4 py-3 text-neutral-700">
                  <span className="line-clamp-2">{plain(q.question) || "(nội dung nằm ở nhóm)"}</span>
                  {q.hasAudio && <Headphones size={14} className="mt-1 inline text-neutral-400" />}
                </td>
                <td className="px-4 py-3 text-neutral-600">{q.correct}</td>
                <td className="px-4 py-3 text-xs text-neutral-400">{q.source ?? "-"}</td>
                <td className="whitespace-nowrap px-4 py-3 text-right">
                  <button
                    type="button"
                    onClick={() => setEditingId(q.id)}
                    title="Sửa câu hỏi"
                    className="rounded-full p-2 text-neutral-400 transition hover:bg-neutral-100 hover:text-neutral-800"
                  >
                    <Pencil size={16} />
                  </button>
                  <button
                    type="button"
                    onClick={() => remove(q)}
                    title="Xóa câu hỏi"
                    className="rounded-full p-2 text-neutral-400 transition hover:bg-red-50 hover:text-red-600"
                  >
                    <Trash2 size={16} />
                  </button>
                </td>
              </tr>
            ))}
            {page && page.content.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-neutral-400">
                  Không có câu hỏi nào khớp bộ lọc.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {page && page.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-neutral-500">
          <span>
            Trang {page.page + 1}/{page.totalPages} ({page.totalElements} câu)
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

      {editingId !== null && (
        <EditQuestionDialog questionId={editingId} onClose={() => setEditingId(null)} onSaved={() => void load()} />
      )}
      {importing && <ImportDialog onClose={() => setImporting(false)} onImported={() => void load()} />}
    </div>
  );
}
