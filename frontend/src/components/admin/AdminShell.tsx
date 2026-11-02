"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { ArrowLeft, Database, Server, Users } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useAuthModalStore } from "@/store/authModalStore";

const ADMIN_NAV = [
  { href: "/admin/users", label: "Người dùng", icon: Users },
  { href: "/admin/server", label: "Trạng thái server", icon: Server },
  { href: "/admin/bank", label: "Ngân hàng đề", icon: Database },
];

/** Sidebar plus the access check: only a logged-in ADMIN sees the pages. */
export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const role = useAuthStore((s) => s.user?.role);
  const openAuthModal = useAuthModalStore((s) => s.open);

  if (isInitializing) return null;

  if (!isAuthenticated || role !== "ADMIN") {
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
        <div className="px-3 pb-5">
          <p className="font-serif text-lg font-semibold tracking-wide text-neutral-900">管理室</p>
          <p className="text-[11px] uppercase tracking-widest text-neutral-400">Admin console</p>
        </div>
        <nav className="flex flex-1 flex-col gap-1">
          {ADMIN_NAV.map((item) => {
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
                {item.label}
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
    </div>
  );
}
