"use client";

import { usePathname } from "next/navigation";
import { ChevronDown, Globe, LogOut } from "lucide-react";
import { getActiveNavItem } from "@/lib/navigation";
import { useAuthModalStore } from '@/store/authModalStore';
import { useAuth } from '@/hooks/useAuth';

/**
 * Top header. Breadcrumb title updates based on the active route.
 * Auth buttons are static placeholders — wiring to open the login/register
 * modal happens once AUTH-07 is built.
 */
export function Header() {
  const pathname = usePathname();
  const active = getActiveNavItem(pathname);

  const openAuthModal = useAuthModalStore((s) => s.open);
  const { isAuthenticated, user, isInitializing,logout } = useAuth();

  return (
    <header className="flex h-[73px] items-center justify-between border-b border-black/5 bg-[#F5F3EE] px-8">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 text-sm text-neutral-400">
        <span>{active.breadcrumbJa}</span>
        <span className="text-neutral-300">/</span>
        <span className="font-medium text-neutral-800">{active.breadcrumbVi}</span>
      </div>

      {/* Right controls */}
      <div className="flex items-center gap-3">
        <button
          type="button"
          className="flex items-center gap-1.5 rounded-full border border-black/10 bg-white px-3.5 py-1.5 text-sm text-neutral-600 transition hover:bg-black/[0.03]"
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
            <span className="text-sm text-neutral-700">
              {user?.name ?? user?.email}
            </span>
            <button
              type="button"
              onClick={logout}
              aria-label="Đăng xuất"
              title="Đăng xuất"
              className="flex items-center gap-1.5 rounded-full border border-black/10 bg-white px-3.5 py-1.5 text-sm text-neutral-600 transition hover:bg-black/[0.03]"
            >
              <LogOut size={14} strokeWidth={1.75} />
              Đăng xuất
            </button>
          </div>
        ) : (
          <>
            <button
              type="button"
              onClick={() => openAuthModal("login")}
              className="rounded-full px-4 py-1.5 text-sm text-neutral-600 transition hover:bg-black/[0.03]"
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