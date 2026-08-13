"use client";

import { usePathname } from "next/navigation";
import { ChevronDown, Globe } from "lucide-react";
import { getActiveNavItem } from "@/lib/navigation";

/**
 * Top header. Breadcrumb title updates based on the active route.
 * Auth buttons are static placeholders — wiring to open the login/register
 * modal happens once AUTH-07 is built.
 */
export function Header() {
  const pathname = usePathname();
  const active = getActiveNavItem(pathname);

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

        <button
          type="button"
          className="rounded-full px-4 py-1.5 text-sm text-neutral-600 transition hover:bg-black/[0.03]"
        >
          Đăng nhập
        </button>
        <button
          type="button"
          className="rounded-full bg-neutral-900 px-4 py-1.5 text-sm font-medium text-white transition hover:bg-neutral-800"
        >
          Đăng ký
        </button>
      </div>
    </header>
  );
}