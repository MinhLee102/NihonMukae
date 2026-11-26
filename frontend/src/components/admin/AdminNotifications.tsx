"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Bell, X } from "lucide-react";
import {
  fetchAdminNotifications,
  type AdminNotifications,
  type NotificationKind,
} from "@/lib/api/adminNotifications";

const POLL_MS = 60_000;

/**
 * Pending work for admins, refreshed every minute and when the tab comes back into view.
 * `fresh` lists what grew since the previous check, for a short toast.
 */
export function useAdminNotifications(enabled: boolean) {
  const [data, setData] = useState<AdminNotifications | null>(null);
  const [fresh, setFresh] = useState<string | null>(null);
  const previous = useRef<Map<NotificationKind, number> | null>(null);

  const refresh = useCallback(async () => {
    try {
      const next = await fetchAdminNotifications();
      const before = previous.current;
      if (before) {
        const grown = next.groups
          .map((g) => ({ g, diff: g.count - (before.get(g.kind) ?? 0) }))
          .filter((x) => x.diff > 0)
          .map((x) => `${x.diff} ${x.g.label.toLowerCase()}`);
        if (grown.length > 0) setFresh(`Có thêm ${grown.join(", ")}`);
      }
      previous.current = new Map(next.groups.map((g) => [g.kind, g.count]));
      setData(next);
    } catch {
      // keep the last numbers; the next poll tries again
    }
  }, []);

  useEffect(() => {
    if (!enabled) return;
    void refresh();
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") void refresh();
    }, POLL_MS);
    const onVisible = () => document.visibilityState === "visible" && void refresh();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [enabled, refresh]);

  // The count in the tab title is visible even while working in another tab
  useEffect(() => {
    if (!data) return;
    const base = document.title.replace(/^\(\d+\)\s*/, "");
    document.title = data.total > 0 ? `(${data.total}) ${base}` : base;
  }, [data]);

  useEffect(() => {
    if (!fresh) return;
    const t = window.setTimeout(() => setFresh(null), 6000);
    return () => window.clearTimeout(t);
  }, [fresh]);

  return { data, fresh, dismissFresh: () => setFresh(null), refresh };
}

export function countOf(data: AdminNotifications | null, kind: NotificationKind): number {
  return data?.groups.find((g) => g.kind === kind)?.count ?? 0;
}

function timeAgo(iso: string | null): string {
  if (!iso) return "";
  const minutes = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (minutes < 1) return "vừa xong";
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  return `${Math.round(hours / 24)} ngày trước`;
}

/** Bell with the total; opens a panel listing what waits, grouped, each linking to its page. */
export function NotificationBell({ data, onOpen }: { data: AdminNotifications | null; onOpen: () => void }) {
  const [open, setOpen] = useState(false);
  const total = data?.total ?? 0;

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => {
          setOpen((o) => !o);
          onOpen();
        }}
        aria-label={total > 0 ? `${total} việc chờ xử lý` : "Thông báo"}
        className="relative flex h-8 w-8 items-center justify-center rounded-full text-neutral-500 transition hover:bg-black/5 hover:text-neutral-800"
      >
        <Bell size={17} strokeWidth={1.75} />
        {total > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white">
            {total > 99 ? "99+" : total}
          </span>
        )}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-10 z-40 w-[320px] rounded-2xl border border-black/5 bg-white p-3 shadow-xl">
            <div className="flex items-center justify-between px-1 pb-2">
              <p className="text-sm font-semibold text-neutral-900">Chờ xử lý</p>
              <button type="button" onClick={() => setOpen(false)} aria-label="Đóng" className="text-neutral-400 hover:text-neutral-700">
                <X size={15} />
              </button>
            </div>
            {!data && <p className="px-1 py-4 text-sm text-neutral-400">Đang tải...</p>}
            {data && total === 0 && <p className="px-1 py-4 text-sm text-neutral-500">Không có việc nào đang chờ.</p>}
            {data?.groups
              .filter((g) => g.count > 0)
              .map((g) => (
                <div key={g.kind} className="border-t border-black/5 py-2 first:border-t-0">
                  <Link
                    href={g.href}
                    onClick={() => setOpen(false)}
                    className="flex items-center justify-between rounded-lg px-1 py-1 text-sm font-medium text-neutral-800 hover:bg-black/5"
                  >
                    {g.label}
                    <span className="rounded-full bg-red-50 px-2 text-xs font-semibold text-red-600">{g.count}</span>
                  </Link>
                  <ul className="mt-1">
                    {g.latest.map((item) => (
                      <li key={item.id} className="px-1 py-1 text-xs leading-5">
                        <p className="truncate text-neutral-700">
                          {item.title}
                          <span className="ml-2 text-neutral-400">{timeAgo(item.createdAt)}</span>
                        </p>
                        {item.detail && <p className="truncate text-neutral-400">{item.detail}</p>}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
          </div>
        </>
      )}
    </div>
  );
}
