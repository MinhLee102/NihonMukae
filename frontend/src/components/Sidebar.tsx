"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { ChevronLeft, ChevronRight, User } from "lucide-react";
import { NAV_ITEMS } from "@/lib/navigation";

/**
 * App-wide sidebar. 
 * swap for the real auth store once AUTH-07 lands.
 */
export function Sidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);

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

      {/* User footer — placeholder */}
      <div className="border-t border-black/5 px-3 py-4">
        <div
          className={`flex items-center gap-3 rounded-xl px-2 py-2 ${
            collapsed ? "justify-center" : ""
          }`}
        >
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-neutral-200 text-neutral-500">
            <User size={16} strokeWidth={1.75} />
          </div>
          {!collapsed && (
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-neutral-800">
                Tanaka Ken
              </p>
              <p className="truncate text-xs text-neutral-400">N4 Level</p>
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}