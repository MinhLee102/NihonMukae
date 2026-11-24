"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, MessageSquarePlus, ShieldCheck, User, X } from "lucide-react";
import { NAV_ITEMS } from "@/lib/navigation";
import { useAuthStore } from "@/store/authStore";
import { useMobileNavStore } from "@/store/mobileNavStore";
import { useAuthModalStore } from "@/store/authModalStore";
import { useProfileStore } from "@/store/profileStore";
import { useFeedbackStore } from "@/store/feedbackStore";

/**
 * App-wide sidebar. From md up it is a fixed column that can be collapsed; below md it is a drawer that slides in
 * from the left (opened by the button in the header) and always shows the labels.
 */
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
  const drawerOpen = useMobileNavStore((s) => s.open);
  const hideDrawer = useMobileNavStore((s) => s.hide);

  // Close the drawer after navigating, and with Escape
  useEffect(() => {
    hideDrawer();
  }, [pathname, hideDrawer]);
  useEffect(() => {
    if (!drawerOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && hideDrawer();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [drawerOpen, hideDrawer]);

  // Collapsing only exists from md up: in the drawer the labels always show
  const hideLabel = collapsed ? "md:hidden" : "";
  const centered = collapsed ? "md:justify-center" : "";

  useEffect(() => {
    if (isAuthenticated) void loadProfile();
    else clearProfile();
  }, [isAuthenticated, loadProfile, clearProfile]);

  const displayName = profile?.name ?? tokenUser?.email ?? "";
  const userRole = profile?.role ?? tokenUser?.role;
  const isAdmin = userRole === "ADMIN" || userRole === "MODERATOR";

  return (
    <>
      {drawerOpen && (
        <div className="fixed inset-0 z-40 bg-black/30 md:hidden" onClick={hideDrawer} aria-hidden="true" />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-50 flex h-dvh w-[260px] flex-col border-r border-black/5 bg-[#F5F3EE] transition-transform duration-200 ease-out md:static md:z-auto md:h-screen md:translate-x-0 md:transition-[width] ${
          drawerOpen ? "translate-x-0" : "-translate-x-full"
        } ${collapsed ? "md:w-[76px]" : "md:w-[220px]"}`}
      >
      <div className="flex items-center justify-between px-5 py-5">
        <span className={`font-serif text-lg font-semibold tracking-wide text-neutral-900 ${hideLabel}`}>
          日本語
        </span>
        <button
          type="button"
          onClick={hideDrawer}
          aria-label="Đóng menu"
          className="flex h-8 w-8 items-center justify-center rounded-full text-neutral-500 hover:bg-black/5 md:hidden"
        >
          <X size={18} />
        </button>
        <button
          type="button"
          onClick={() => setCollapsed((v) => !v)}
          aria-label={collapsed ? "Mở rộng sidebar" : "Thu nhỏ sidebar"}
          className="hidden h-6 w-6 items-center justify-center rounded-full text-neutral-400 transition hover:bg-black/5 hover:text-neutral-700 md:flex"
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
              } ${centered}`}
            >
              <Icon size={18} strokeWidth={1.75} className="shrink-0" />
              <span className={`truncate ${hideLabel}`}>{item.label}</span>
            </Link>
          );
        })}
      </nav>

      <div className="px-3 pb-2">
        <button
          type="button"
          onClick={() => openFeedback()}
          title={collapsed ? "Góp ý" : undefined}
          className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-neutral-500 transition-colors hover:bg-black/5 hover:text-neutral-800 ${centered}`}
        >
          <MessageSquarePlus size={18} strokeWidth={1.75} className="shrink-0" />
          <span className={`truncate ${hideLabel}`}>Góp ý</span>
        </button>
      </div>

      {/* User footer: opens the profile page, admins also get a shortcut to the console */}
      <div className="border-t border-black/5 px-3 py-4">
        <div className={`flex items-center gap-1 ${collapsed ? "md:flex-col" : ""}`}>
          {isAuthenticated ? (
            <Link
              href="/profile"
              title={collapsed ? displayName : undefined}
              className={`flex min-w-0 flex-1 items-center gap-3 rounded-xl px-2 py-2 transition-colors hover:bg-black/5 ${
                pathname === "/profile" ? "bg-black/5" : ""
              } ${centered}`}
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-neutral-200 text-neutral-500">
                <User size={16} strokeWidth={1.75} />
              </div>
              {(
                <div className={`min-w-0 ${hideLabel}`}>
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
              className={`flex min-w-0 flex-1 items-center gap-3 rounded-xl px-2 py-2 text-left transition-colors hover:bg-black/5 ${centered}`}
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-neutral-200 text-neutral-500">
                <User size={16} strokeWidth={1.75} />
              </div>
              <span className={`truncate text-sm text-neutral-500 ${hideLabel}`}>Đăng nhập</span>
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
    </>
  );
}
