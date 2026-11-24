"use client";

import { usePathname } from "next/navigation";
import { ChevronDown, Globe, LogOut, Menu } from "lucide-react";
import { getActiveNavItem } from "@/lib/navigation";
import { useAuthModalStore } from '@/store/authModalStore';
import { useAuth } from '@/hooks/useAuth';
import { useProfileStore } from '@/store/profileStore';
import { useMobileNavStore } from '@/store/mobileNavStore';

/**
 * Top header. Breadcrumb title updates based on the active route.
 * Auth buttons are static placeholders — wiring to open the login/register
 * modal happens once AUTH-07 is built.
 */
// Exam and result screens use the whole height for the paper, so the header steps aside there
const FOCUS_ROUTES = /^\/jlpt\/(session|result)\//;

export function Header() {
  const pathname = usePathname();
  const active = getActiveNavItem(pathname);

  const openAuthModal = useAuthModalStore((s) => s.open);
  const { isAuthenticated, user, isInitializing,logout } = useAuth();
  const profileName = useProfileStore((s) => s.profile?.name);
  const showDrawer = useMobileNavStore((s) => s.show);

  if (FOCUS_ROUTES.test(pathname)) return null;

  return (
    <header className="flex h-14 items-center justify-between gap-2 border-b border-black/5 bg-[#F5F3EE] px-4 md:h-[73px] md:px-8">
      {/* Menu button (small screens) and breadcrumb */}
      <div className="flex min-w-0 items-center gap-2 text-sm text-neutral-400">
        <button
          type="button"
          onClick={showDrawer}
          aria-label="Mở menu"
          className="-ml-1.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-neutral-600 hover:bg-black/5 md:hidden"
        >
          <Menu size={20} />
        </button>
        <span className="hidden md:inline">{active.breadcrumbJa}</span>
        <span className="hidden text-neutral-300 md:inline">/</span>
        <span className="truncate font-medium text-neutral-800">{active.breadcrumbVi}</span>
      </div>

      {/* Right controls */}
      <div className="flex shrink-0 items-center gap-2 md:gap-3">
        <button
          type="button"
          disabled
          title="Đang phát triển"
          className="hidden cursor-not-allowed items-center gap-1.5 rounded-full border border-black/10 bg-white px-3.5 py-1.5 text-sm text-neutral-400 opacity-70 sm:flex"
        >
          <Globe size={14} strokeWidth={1.75} />
          Nhật - Việt
          <ChevronDown size={14} strokeWidth={1.75} className="text-neutral-400" />
        </button>

        {isInitializing ? (
          // Avoid flashing "Đăng nhập" for a split second while the silent
          // refresh on page load is still resolving.
          <div className="h-8 w-24 animate-pulse rounded-full bg-black/5" />
        ) : isAuthenticated ? (
          <div className="flex items-center gap-2">
            <span className="hidden max-w-[160px] truncate text-sm text-neutral-700 sm:inline">
              {profileName ?? user?.name ?? user?.email}
            </span>
            <button
              type="button"
              onClick={logout}
              aria-label="Đăng xuất"
              title="Đăng xuất"
              className="flex items-center gap-1.5 rounded-full border border-black/10 bg-white px-3.5 py-1.5 text-sm text-neutral-600 transition hover:bg-black/[0.03]"
            >
              <LogOut size={14} strokeWidth={1.75} />
              <span className="hidden sm:inline">Đăng xuất</span>
            </button>
          </div>
        ) : (
          <>
            <button
              type="button"
              onClick={() => openAuthModal("login")}
              className="rounded-full px-3 py-1.5 text-sm text-neutral-600 transition hover:bg-black/[0.03] md:px-4"
            >
              Đăng nhập
            </button>
            <button
              type="button"
              onClick={() => openAuthModal("register")}
              className="rounded-full bg-neutral-900 px-4 py-1.5 text-sm font-medium text-white transition hover:bg-neutral-800"
            >
              Đăng ký
            </button>
          </>
        )}
      </div>
    </header>
  );
}