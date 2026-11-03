"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, MessageSquarePlus, ShieldCheck, User } from "lucide-react";
import { NAV_ITEMS } from "@/lib/navigation";
import { useAuthStore } from "@/store/authStore";
import { useAuthModalStore } from "@/store/authModalStore";
import { useProfileStore } from "@/store/profileStore";
import { useFeedbackStore } from "@/store/feedbackStore";

/** App-wide sidebar. */
export function Sidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);

  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const tokenUser = useAuthStore((s) => s.user);
  const openAuthModal = useAuthModalStore((s) => s.open);
  const profile = useProfileStore((s) => s.profile);
  const loadProfile = useProfileStore((s) => s.load);
  const clearProfile = useProfileStore((s) => s.clear);
  const openFeedback = useFeedbackStore((s) => s.open);

  useEffect(() => {
    if (isAuthenticated) void loadProfile();
    else clearProfile();
  }, [isAuthenticated, loadProfile, clearProfile]);

  const displayName = profile?.name ?? tokenUser?.email ?? "";
  const userRole = profile?.role ?? tokenUser?.role;
  const isAdmin = userRole === "ADMIN" || userRole === "MODERATOR";

  return (
    <aside
      className={`flex h-screen flex-col border-r border-black/5 bg-[#F5F3EE] transition-[width] duration-200 ease-out ${
        collapsed ? "w-[76px]" : "w-[220px]"
      }`}
    >
      <div className="flex items-center justify-between px-5 py-5">
        {!collapsed && (
          <span className="font-serif text-lg font-semibold tracking-wide text-neutral-900">
            日本語
          </span>
        )}
        <button
          type="button"
          onClick={() => setCollapsed((v) => !v)}
          aria-label={collapsed ? "Mở rộng sidebar" : "Thu nhỏ sidebar"}
          className="flex h-6 w-6 items-center justify-center rounded-full text-neutral-400 transition hover:bg-black/5 hover:text-neutral-700"
        >
          {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
        </button>
      </div>

      {/* Nav items */}
      <nav className="flex flex-1 flex-col gap-1 px-3">
        {NAV_ITEMS.map((item) => {
          const isActive = item.href === pathname;
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              title={collapsed ? item.label : undefined}
              className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${
                isActive
                  ? "bg-neutral-900 text-white"
                  : "text-neutral-500 hover:bg-black/5 hover:text-neutral-800"
              } ${collapsed ? "justify-center" : ""}`}
            >
              <Icon size={18} strokeWidth={1.75} className="shrink-0" />
              {!collapsed && <span className="truncate">{item.label}</span>}
            </Link>
          );
        })}
      </nav>

      <div className="px-3 pb-2">
        <button
          type="button"
          onClick={() => openFeedback()}
          title={collapsed ? "Góp ý" : undefined}
          className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-neutral-500 transition-colors hover:bg-black/5 hover:text-neutral-800 ${
            collapsed ? "justify-center" : ""
          }`}
        >
          <MessageSquarePlus size={18} strokeWidth={1.75} className="shrink-0" />
          {!collapsed && <span className="truncate">Góp ý</span>}
        </button>
      </div>

      {/* User footer: opens the profile page, admins also get a shortcut to the console */}
      <div className="border-t border-black/5 px-3 py-4">
        <div className={`flex items-center gap-1 ${collapsed ? "flex-col" : ""}`}>
          {isAuthenticated ? (
            <Link
              href="/profile"
              title={collapsed ? displayName : undefined}
              className={`flex min-w-0 flex-1 items-center gap-3 rounded-xl px-2 py-2 transition-colors hover:bg-black/5 ${
                pathname === "/profile" ? "bg-black/5" : ""
              } ${collapsed ? "justify-center" : ""}`}
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-neutral-200 text-neutral-500">
                <User size={16} strokeWidth={1.75} />
              </div>
              {!collapsed && (
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-neutral-800">{displayName}</p>
                  <p className="truncate text-xs text-neutral-400">
                    {profile?.targetLevel ? `Mục tiêu N${profile.targetLevel}` : "Chưa chọn mục tiêu"}
                  </p>
                </div>
              )}
            </Link>
          ) : (
            <button
              type="button"
              onClick={() => openAuthModal("login")}
              title={collapsed ? "Đăng nhập" : undefined}
              className={`flex min-w-0 flex-1 items-center gap-3 rounded-xl px-2 py-2 text-left transition-colors hover:bg-black/5 ${
                collapsed ? "justify-center" : ""
              }`}
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-neutral-200 text-neutral-500">
                <User size={16} strokeWidth={1.75} />
              </div>
              {!collapsed && <span className="truncate text-sm text-neutral-500">Đăng nhập</span>}
            </button>
          )}
          {isAuthenticated && isAdmin && (
            <Link
              href="/admin"
              title="Trang quản trị"
              aria-label="Trang quản trị"
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-neutral-400 transition hover:bg-neutral-900 hover:text-white"
            >
              <ShieldCheck size={16} strokeWidth={1.75} />
            </Link>
          )}
        </div>
      </div>
    </aside>
  );
}
