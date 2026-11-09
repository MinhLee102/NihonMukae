"use client";

import { useState } from "react";
import { Play } from "lucide-react";

const VIEW = 109;

function Frame({ size, children }: { size: number; children: React.ReactNode }) {
  return (
    <svg viewBox={`0 0 ${VIEW} ${VIEW}`} width={size} height={size} className="rounded-lg border border-black/10 bg-white">
      <g stroke="#e5e5e5" strokeWidth="0.6" strokeDasharray="3 3">
        <line x1="0" y1={VIEW / 2} x2={VIEW} y2={VIEW / 2} />
        <line x1={VIEW / 2} y1="0" x2={VIEW / 2} y2={VIEW} />
      </g>
      {children}
    </svg>
  );
}

const STROKE = { fill: "none", strokeLinecap: "round", strokeLinejoin: "round" } as const;

/**
 * Thứ tự nét viết (KanjiVG, CC BY-SA 3.0): hình chạy từng nét và dãy hình từng bước.
 * paths là thuộc tính d của từng nét theo thứ tự viết, hệ toạ độ 109x109.
 */
export function StrokeOrder({ paths }: { paths: string[] }) {
  const [run, setRun] = useState(0);
  if (paths.length === 0) {
    return <p className="text-sm text-neutral-400">Chưa có dữ liệu thứ tự nét cho chữ này.</p>;
  }
  const per = 0.55; // giây mỗi nét

  return (
    <div className="flex flex-col gap-4">
      <style>{`@keyframes kanji-draw { from { stroke-dashoffset: 1; } to { stroke-dashoffset: 0; } }`}</style>
      <div className="flex items-start gap-4">
        <Frame size={150}>
          {/* key đổi thì animation chạy lại */}
          <g key={run}>
            {paths.map((d, i) => (
              <path
                key={i}
                d={d}
                pathLength={1}
                stroke="#171717"
                strokeWidth="3.4"
                {...STROKE}
                style={{
                  strokeDasharray: 1,
                  strokeDashoffset: 1,
                  animation: `kanji-draw ${per}s ease-in-out ${i * per}s forwards`,
                }}
              />
            ))}
          </g>
        </Frame>
        <button
          type="button"
          onClick={() => setRun((n) => n + 1)}
          className="flex items-center gap-1.5 rounded-full border border-black/10 px-3.5 py-1.5 text-sm text-neutral-600 transition hover:bg-neutral-50"
        >
          <Play size={13} /> Chạy lại
        </button>
      </div>

      <div className="flex flex-wrap gap-2">
        {paths.map((_, step) => (
          <div key={step} className="flex flex-col items-center gap-0.5">
            <Frame size={54}>
              {paths.slice(0, step + 1).map((d, i) => (
                <path
                  key={i}
                  d={d}
                  stroke={i === step ? "#dc2626" : "#404040"}
                  strokeWidth="4"
                  {...STROKE}
                />
              ))}
            </Frame>
            <span className="text-[10px] text-neutral-400">{step + 1}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
