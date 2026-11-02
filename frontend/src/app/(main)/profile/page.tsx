"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useAuthStore } from "@/store/authStore";
import { useAuthModalStore } from "@/store/authModalStore";
import { useProfileStore } from "@/store/profileStore";

const LEVELS = [5, 4, 3, 2, 1];

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
}

export default function ProfilePage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const openAuthModal = useAuthModalStore((s) => s.open);
  const profile = useProfileStore((s) => s.profile);
  const save = useProfileStore((s) => s.save);

  const [name, setName] = useState("");
  const [level, setLevel] = useState<number | null>(null);
  const [status, setStatus] = useState<"idle" | "saving" | "saved" | "error">("idle");

  useEffect(() => {
    if (profile) {
      setName(profile.name);
      setLevel(profile.targetLevel);
    }
  }, [profile]);

  if (isInitializing) return null;
  if (!isAuthenticated) {
    return (
      <div className="mx-auto max-w-md rounded-2xl border border-black/5 bg-white p-8 text-center">
        <p className="text-neutral-600">Bạn cần đăng nhập để xem hồ sơ.</p>
        <button
          type="button"
          onClick={() => openAuthModal("login")}
          className="mt-4 rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
        >
          Đăng nhập
        </button>
      </div>
    );
  }
  if (!profile) return null;

  const dirty = name.trim() !== profile.name || level !== profile.targetLevel;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setStatus("saving");
    try {
      await save({ name: name.trim(), targetLevel: level });
      setStatus("saved");
    } catch {
      setStatus("error");
    }
  }

  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-xl rounded-2xl border border-black/5 bg-white p-8">
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Hồ sơ của bạn</h1>

      <label className="mt-6 block text-sm font-medium text-neutral-700" htmlFor="profile-name">
        Tên hiển thị
      </label>
      <input
        id="profile-name"
        value={name}
        maxLength={100}
        onChange={(e) => {
          setName(e.target.value);
          setStatus("idle");
        }}
        className="mt-1.5 w-full rounded-xl border border-black/10 px-3.5 py-2.5 text-sm outline-none focus:border-neutral-900"
      />

      <p className="mt-6 text-sm font-medium text-neutral-700">Cấp độ mong muốn</p>
      <div className="mt-2 flex flex-wrap gap-2">
        {LEVELS.map((l) => (
          <button
            key={l}
            type="button"
            onClick={() => {
              setLevel(level === l ? null : l);
              setStatus("idle");
            }}
            className={`rounded-full border px-4 py-1.5 text-sm transition ${
              level === l
                ? "border-neutral-900 bg-neutral-900 text-white"
                : "border-black/10 text-neutral-600 hover:bg-black/5"
            }`}
          >
            N{l}
          </button>
        ))}
      </div>
      <p className="mt-1.5 text-xs text-neutral-400">Bấm lại cấp đã chọn để bỏ chọn.</p>

      <dl className="mt-6 space-y-2 border-t border-black/5 pt-5 text-sm">
        <div className="flex justify-between">
          <dt className="text-neutral-400">Email</dt>
          <dd className="text-neutral-700">{profile.email}</dd>
        </div>
        <div className="flex justify-between">
          <dt className="text-neutral-400">Ngày tạo tài khoản</dt>
          <dd className="text-neutral-700">{formatDate(profile.createdAt)}</dd>
        </div>
      </dl>

      <div className="mt-6 flex items-center gap-3">
        <button
          type="submit"
          disabled={!dirty || !name.trim() || status === "saving"}
          className="rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white transition disabled:opacity-40"
        >
          {status === "saving" ? "Đang lưu..." : "Lưu thay đổi"}
        </button>
        {status === "saved" && <span className="text-sm text-emerald-600">Đã lưu</span>}
        {status === "error" && <span className="text-sm text-red-600">Không lưu được, thử lại nhé</span>}
      </div>
    </form>
  );
}
