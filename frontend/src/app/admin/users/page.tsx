"use client";

import { useCallback, useEffect, useState } from "react";
import { Lock, LockOpen, Search } from "lucide-react";
import {
  fetchUsers,
  fetchUserSummary,
  setUserEnabled,
  type AdminUser,
  type Page,
  type UserSummary,
} from "@/lib/api/admin";

const PAGE_SIZE = 20;

const STATUS_FILTERS = [
  { id: "", label: "Tất cả" },
  { id: "active", label: "Đang hoạt động" },
  { id: "locked", label: "Đã khóa" },
];

function formatDate(iso: string | null) {
  return iso ? new Date(iso).toLocaleDateString("vi-VN") : "Chưa đăng nhập";
}

export default function AdminUsersPage() {
  const [summary, setSummary] = useState<UserSummary | null>(null);
  const [page, setPage] = useState<Page<AdminUser> | null>(null);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [pageIndex, setPageIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [s, p] = await Promise.all([
        fetchUserSummary(),
        fetchUsers({ q: search || undefined, status: status || undefined, page: pageIndex, size: PAGE_SIZE }),
      ]);
      setSummary(s);
      setPage(p);
      setError(null);
    } catch {
      setError("Không tải được danh sách người dùng.");
    }
  }, [search, status, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggle(user: AdminUser) {
    const verb = user.enabled ? "khóa" : "mở khóa";
    if (!window.confirm(`Bạn muốn ${verb} tài khoản ${user.email}?`)) return;
    try {
      await setUserEnabled(user.id, !user.enabled);
      await load();
    } catch {
      setError("Không đổi được trạng thái tài khoản này.");
    }
  }

  const cards = [
    { label: "Tổng người dùng", value: summary?.total },
    { label: "Đang hoạt động (30 ngày)", value: summary?.active },
    { label: "Đã khóa", value: summary?.locked },
    { label: "Mới 30 ngày qua", value: summary?.newLast30Days },
  ];

  return (
    <div>
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Người dùng</h1>

      <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {cards.map((c) => (
          <div key={c.label} className="rounded-2xl border border-black/5 bg-white p-5">
            <p className="text-xs text-neutral-400">{c.label}</p>
            <p className="mt-1 text-2xl font-semibold text-neutral-900">{c.value ?? "-"}</p>
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
            placeholder="Tìm theo tên hoặc email, nhấn Enter"
            className="min-w-0 flex-1 bg-transparent text-sm outline-none"
          />
        </form>
        <div className="flex gap-1">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => {
                setStatus(f.id);
                setPageIndex(0);
              }}
              className={`rounded-full px-3.5 py-1.5 text-sm transition ${
                status === f.id ? "bg-neutral-900 text-white" : "text-neutral-500 hover:bg-black/5"
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
              <th className="px-4 py-3">Người dùng</th>
              <th className="px-4 py-3">Vai trò</th>
              <th className="px-4 py-3">Mục tiêu</th>
              <th className="px-4 py-3">Ngày tạo</th>
              <th className="px-4 py-3">Đăng nhập gần nhất</th>
              <th className="px-4 py-3">Trạng thái</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {page?.content.map((u) => (
              <tr key={u.id} className="border-b border-black/5 last:border-0">
                <td className="px-4 py-3">
                  <p className="font-medium text-neutral-800">{u.name}</p>
                  <p className="text-xs text-neutral-400">{u.email}</p>
                </td>
                <td className="px-4 py-3 text-neutral-600">{u.role}</td>
                <td className="px-4 py-3 text-neutral-600">{u.targetLevel ? `N${u.targetLevel}` : "-"}</td>
                <td className="px-4 py-3 text-neutral-600">{formatDate(u.createdAt)}</td>
                <td className="px-4 py-3 text-neutral-600">{formatDate(u.lastLoginAt)}</td>
                <td className="px-4 py-3">
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs ${
                      u.enabled ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-700"
                    }`}
                  >
                    {u.enabled ? "Hoạt động" : "Đã khóa"}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  {u.role !== "ADMIN" && (
                    <button
                      type="button"
                      onClick={() => toggle(u)}
                      title={u.enabled ? "Khóa tài khoản" : "Mở khóa"}
                      className="rounded-full p-2 text-neutral-400 transition hover:bg-black/5 hover:text-neutral-800"
                    >
                      {u.enabled ? <Lock size={16} /> : <LockOpen size={16} />}
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {page && page.content.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-neutral-400">
                  Không có người dùng nào khớp.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {page && page.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-neutral-500">
          <span>
            Trang {page.page + 1}/{page.totalPages} ({page.totalElements} người)
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
    </div>
  );
}
