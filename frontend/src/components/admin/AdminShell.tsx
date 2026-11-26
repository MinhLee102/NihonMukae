"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { ArrowLeft, BookOpen, Database, HandHeart, MessageSquare, Server, Sparkles, Users } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useAuthModalStore } from "@/store/authModalStore";
import type { NotificationKind } from "@/lib/api/adminNotifications";
import { NotificationBell, countOf, useAdminNotifications } from "@/components/admin/AdminNotifications";

// adminOnly: MODERATOR (chưa gán cho ai) chỉ thấy các mục không đánh dấu. notify: số việc chờ hiện cạnh mục
const ADMIN_NAV: {
  href: string;
  label: string;
  icon: typeof Users;
  adminOnly: boolean;
  notify?: NotificationKind;
}[] = [
  { href: "/admin/users", label: "Người dùng", icon: Users, adminOnly: true, notify: "users" },
  { href: "/admin/server", label: "Trạng thái server", icon: Server, adminOnly: true },
  { href: "/admin/bank", label: "Ngân hàng đề", icon: Database, adminOnly: false },
  { href: "/admin/prompts", label: "Prompt AI", icon: Sparkles, adminOnly: false },
  { href: "/admin/dictionary", label: "Từ điển", icon: BookOpen, adminOnly: false },
  { href: "/admin/contributions", label: "Đóng góp từ điển", icon: HandHeart, adminOnly: false, notify: "contributions" },
  { href: "/admin/feedback", label: "Góp ý", icon: MessageSquare, adminOnly: false, notify: "feedback" },
];

/** Sidebar plus the access check: ADMIN sees everything, MODERATOR only the question bank and feedback. */
export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const role = useAuthStore((s) => s.user?.role);
  const openAuthModal = useAuthModalStore((s) => s.open);
  const canAdmin = !isInitializing && isAuthenticated && (role === "ADMIN" || role === "MODERATOR");
  const { data: notifications, fresh, dismissFresh, refresh } = useAdminNotifications(canAdmin);

  if (isInitializing) return null;

  if (!isAuthenticated || (role !== "ADMIN" && role !== "MODERATOR")) {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <div className="max-w-sm rounded-2xl border border-black/5 bg-white p-8 text-center">
          <p className="font-serif text-lg font-semibold text-neutral-900">Khu vực quản trị</p>
          <p className="mt-2 text-sm text-neutral-500">
            {isAuthenticated
              ? "Tài khoản của bạn không có quyền quản trị. Nếu vừa được cấp quyền, hãy đăng xuất rồi đăng nhập lại."
              : "Hãy đăng nhập bằng tài khoản quản trị."}
          </p>
          <div className="mt-5 flex justify-center gap-2">
            {!isAuthenticated && (
              <button
                type="button"
                onClick={() => openAuthModal("login")}
                className="rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
              >
                Đăng nhập
              </button>
            )}
            <Link href="/" className="rounded-full border border-black/10 px-5 py-2 text-sm text-neutral-600">
              Về trang chủ
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full">
      <aside className="flex w-[230px] shrink-0 flex-col border-r border-black/5 bg-[#F5F3EE] px-3 py-5">
        <div className="flex items-start justify-between gap-2 px-3 pb-5">
          <div>
            <p className="font-serif text-lg font-semibold tracking-wide text-neutral-900">管理室</p>
            <p className="text-[11px] uppercase tracking-widest text-neutral-400">Admin console</p>
          </div>
          <NotificationBell data={notifications} onOpen={() => void refresh()} />
        </div>
        <nav className="flex flex-1 flex-col gap-1">
          {ADMIN_NAV.filter((item) => !item.adminOnly || role === "ADMIN").map((item) => {
            const Icon = item.icon;
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${
                  active ? "bg-neutral-900 text-white" : "text-neutral-500 hover:bg-black/5 hover:text-neutral-800"
                }`}
              >
                <Icon size={18} strokeWidth={1.75} />
                <span className="min-w-0 flex-1 truncate">{item.label}</span>
                {item.notify && countOf(notifications, item.notify) > 0 && (
                  <span
                    className={`rounded-full px-1.5 text-[11px] font-semibold tabular-nums ${
                      active ? "bg-white text-neutral-900" : "bg-red-500 text-white"
                    }`}
                  >
                    {countOf(notifications, item.notify)}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>
        <Link
          href="/"
          className="flex items-center gap-2 rounded-xl px-3 py-2.5 text-sm text-neutral-500 transition hover:bg-black/5"
        >
          <ArrowLeft size={16} />
          Về trang học
        </Link>
      </aside>
      <main className="min-w-0 flex-1 overflow-y-auto px-8 py-8">{children}</main>
      {fresh && (
        <div className="fixed bottom-5 right-5 z-50 flex max-w-sm items-start gap-3 rounded-2xl bg-neutral-900 px-4 py-3 text-sm text-white shadow-xl">
          <span className="min-w-0 flex-1">{fresh}</span>
          <button type="button" onClick={dismissFresh} aria-label="Đóng" className="text-neutral-400 hover:text-white">
            ×
          </button>
        </div>
      )}
    </div>
  );
}
