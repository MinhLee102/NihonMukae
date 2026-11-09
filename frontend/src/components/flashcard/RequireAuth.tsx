"use client";

import type { ReactNode } from "react";
import { useAuthStore } from "@/store/authStore";
import { useAuthModalStore } from "@/store/authModalStore";

/** Flashcard pages need a logged-in user; guests see a login prompt instead. */
export function RequireAuth({ children, message }: { children: ReactNode; message?: string }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const openAuthModal = useAuthModalStore((s) => s.open);

  if (isInitializing) {
    return <p className="py-20 text-center text-sm text-neutral-400">Đang tải...</p>;
  }

  if (!isAuthenticated) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-24 text-center">
        <p className="text-neutral-600">{message ?? "Bạn cần đăng nhập để dùng Flashcard."}</p>
        <button
          type="button"
          onClick={() => openAuthModal("login")}
          className="rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
        >
          Đăng nhập
        </button>
      </div>
    );
  }

  return <>{children}</>;
}
