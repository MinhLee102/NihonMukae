"use client";

import { useEffect, useState, type ReactNode } from "react";
import { fetchServerStatus, type ServerStatus } from "@/lib/api/admin";

const REFRESH_MS = 5000;

function bytes(n: number | null | undefined) {
  if (n == null) return "-";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = n;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  return `${value.toFixed(value >= 100 || i === 0 ? 0 : 1)} ${units[i]}`;
}

function uptime(ms: number) {
  const minutes = Math.floor(ms / 60000);
  const d = Math.floor(minutes / 1440);
  const h = Math.floor((minutes % 1440) / 60);
  const m = minutes % 60;
  return d > 0 ? `${d} ngày ${h} giờ` : h > 0 ? `${h} giờ ${m} phút` : `${m} phút`;
}

function Meter({ label, used, total, detail }: { label: string; used: number; total: number; detail: string }) {
  const percent = total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0;
  const tone = percent >= 90 ? "bg-red-500" : percent >= 75 ? "bg-amber-500" : "bg-emerald-500";
  return (
    <div className="rounded-2xl border border-black/5 bg-white p-5">
      <div className="flex items-baseline justify-between">
        <p className="text-sm text-neutral-500">{label}</p>
        <p className="text-sm font-semibold text-neutral-900">{percent}%</p>
      </div>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-black/5">
        <div className={`h-full ${tone}`} style={{ width: `${percent}%` }} />
      </div>
      <p className="mt-2 text-xs text-neutral-400">{detail}</p>
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: ReactNode; tone?: "ok" | "bad" }) {
  return (
    <div className="rounded-2xl border border-black/5 bg-white p-5">
      <p className="text-xs text-neutral-400">{label}</p>
      <p
        className={`mt-1 text-lg font-semibold ${
          tone === "bad" ? "text-red-600" : tone === "ok" ? "text-emerald-600" : "text-neutral-900"
        }`}
      >
        {value}
      </p>
    </div>
  );
}

export default function AdminServerPage() {
  const [status, setStatus] = useState<ServerStatus | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function tick() {
      try {
        const s = await fetchServerStatus();
        if (!cancelled) {
          setStatus(s);
          setError(false);
        }
      } catch {
        if (!cancelled) setError(true);
      }
    }
    void tick();
    const timer = setInterval(tick, REFRESH_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, []);

  if (!status) {
    return <p className="text-sm text-neutral-400">{error ? "Không lấy được trạng thái server." : "Đang tải..."}</p>;
  }

  const s = status;
  return (
    <div>
      <div className="flex items-baseline justify-between">
        <h1 className="font-serif text-2xl font-semibold text-neutral-900">Trạng thái server</h1>
        <p className="text-xs text-neutral-400">
          {error ? "Mất kết nối, đang thử lại..." : `Tự cập nhật mỗi ${REFRESH_MS / 1000} giây`}
        </p>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <Meter
          label="RAM của máy"
          used={s.memory.usedBytes}
          total={s.memory.totalBytes}
          detail={`${bytes(s.memory.usedBytes)} / ${bytes(s.memory.totalBytes)}, còn trống ${bytes(s.memory.freeBytes)}`}
        />
        <Meter
          label="Heap Java"
          used={s.jvm.heapUsedBytes}
          total={s.jvm.heapMaxBytes}
          detail={`${bytes(s.jvm.heapUsedBytes)} / ${bytes(s.jvm.heapMaxBytes)}, ${s.jvm.threads} luồng`}
        />
        <Meter
          label="CPU"
          used={s.cpuLoadPercent ?? 0}
          total={100}
          detail={`${s.cpuCores} nhân, tải trung bình ${s.loadAverage?.toFixed(2) ?? "-"}`}
        />
        <Meter
          label="Ổ đĩa"
          used={s.disk.usedBytes}
          total={s.disk.totalBytes}
          detail={`${bytes(s.disk.usedBytes)} / ${bytes(s.disk.totalBytes)}, còn trống ${bytes(s.disk.freeBytes)}`}
        />
      </div>

      <div className="mt-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="PostgreSQL" value={s.database.up ? "Hoạt động" : "Mất kết nối"} tone={s.database.up ? "ok" : "bad"} />
        <Stat
          label="Dung lượng DB / kết nối"
          value={`${bytes(s.database.sizeBytes)}, ${s.database.activeConnections ?? "-"}/${s.database.maxConnections ?? "-"}`}
        />
        <Stat
          label="Redis"
          value={s.cache.up ? `Hoạt động (${s.cache.pingMs ?? "-"} ms)` : "Mất kết nối"}
          tone={s.cache.up ? "ok" : "bad"}
        />
        <Stat label="Redis dùng" value={bytes(s.cache.usedMemoryBytes)} />
        <Stat label="Thời gian chạy" value={uptime(s.jvm.uptimeMs)} />
        <Stat label="Yêu cầu từ lúc khởi động" value={s.traffic.requests.toLocaleString("vi-VN")} />
        <Stat label="Lỗi 4xx" value={s.traffic.clientErrors.toLocaleString("vi-VN")} />
        <Stat
          label="Lỗi 5xx"
          value={s.traffic.serverErrors.toLocaleString("vi-VN")}
          tone={s.traffic.serverErrors > 0 ? "bad" : undefined}
        />
      </div>

      <h2 className="mt-8 text-sm font-medium text-neutral-500">Dữ liệu</h2>
      <div className="mt-3 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Người dùng" value={s.counts.users.toLocaleString("vi-VN")} />
        <Stat label="Từ điển" value={s.counts.words.toLocaleString("vi-VN")} />
        <Stat label="Câu hỏi đang dùng" value={s.counts.activeQuestions.toLocaleString("vi-VN")} />
        <Stat label="Lượt làm bài" value={s.counts.sessions.toLocaleString("vi-VN")} />
        <Stat label="File media" value={`${s.counts.mediaFiles} (${bytes(s.counts.mediaBytes)})`} />
        <Stat label="Nơi lưu media" value={s.mediaProvider === "drive" ? "Google Drive" : "Ổ đĩa server"} />
        <Stat label="Java" value={s.jvm.javaVersion} />
      </div>
    </div>
  );
}
