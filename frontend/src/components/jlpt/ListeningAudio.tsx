"use client";

import { useEffect, useRef, useState } from "react";
import { Play, Volume2 } from "lucide-react";
import { resolveMediaUrl } from "@/lib/media";

export type AudioMode = "exam" | "free";

const PLAYED_PREFIX = "jlpt:audio-played:";

function wasPlayed(url: string) {
  try {
    return sessionStorage.getItem(PLAYED_PREFIX + url) === "1";
  } catch {
    return false;
  }
}

function markPlayed(url: string) {
  try {
    sessionStorage.setItem(PLAYED_PREFIX + url, "1");
  } catch {
    // Without storage the single-play rule only lasts until the component remounts.
  }
}

/** Practice and review: the browser's own player with seek, pause and replay. */
function FreeAudio({ src }: { src: string }) {
  return (
    <div className="mb-4 flex items-center gap-3 rounded-2xl border border-black/5 bg-white px-4 py-3">
      <Volume2 size={16} className="shrink-0 text-neutral-400" />
      <audio controls preload="metadata" src={src} className="h-8 w-full" />
    </div>
  );
}

/**
 * Exam: like the real test the audio plays once, on its own, with no pause, seek or replay.
 * The play is remembered per file for this browser session, so moving between questions or
 * reloading the page cannot give a second listen.
 */
function ExamAudio({ src, stored }: { src: string; stored: string }) {
  const ref = useRef<HTMLAudioElement>(null);
  const [state, setState] = useState<"ready" | "playing" | "done">(() => (wasPlayed(stored) ? "done" : "ready"));
  const [percent, setPercent] = useState(0);

  useEffect(() => {
    const audio = ref.current;
    if (!audio || state !== "ready") return;
    // Autoplay can be refused by the browser; the button below is the fallback
    audio.play().catch(() => undefined);
  }, [state]);

  if (state === "done") {
    return (
      <p className="mb-4 flex items-center gap-2 rounded-2xl border border-black/5 bg-white px-4 py-3 text-sm text-neutral-500">
        <Volume2 size={16} className="text-neutral-400" />
        Audio đã phát xong. Giống đề thật, bạn không thể nghe lại.
      </p>
    );
  }

  return (
    <div className="mb-4 rounded-2xl border border-black/5 bg-white px-4 py-3">
      <div className="flex items-center gap-3">
        <Volume2 size={16} className="shrink-0 text-neutral-400" />
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-black/5">
          <div className="h-full bg-neutral-900 transition-[width]" style={{ width: `${percent}%` }} />
        </div>
        {state === "ready" && (
          <button
            type="button"
            onClick={() => void ref.current?.play()}
            className="flex items-center gap-1.5 rounded-full bg-neutral-900 px-4 py-1.5 text-xs font-medium text-white"
          >
            <Play size={12} /> Phát (chỉ 1 lần)
          </button>
        )}
        {state === "playing" && <span className="text-xs text-neutral-400">Đang phát</span>}
      </div>
      <audio
        ref={ref}
        src={src}
        preload="auto"
        onPlay={() => {
          markPlayed(stored);
          setState("playing");
        }}
        onTimeUpdate={(e) => {
          const a = e.currentTarget;
          setPercent(a.duration ? (a.currentTime / a.duration) * 100 : 0);
        }}
        onEnded={() => setState("done")}
        // Paused by the browser or a stray key: resume rather than allow a manual pause
        onPause={(e) => {
          if (!e.currentTarget.ended && state === "playing") void e.currentTarget.play().catch(() => undefined);
        }}
        onContextMenu={(e) => e.preventDefault()}
      />
    </div>
  );
}

export function ListeningAudio({ url, mode }: { url: string; mode: AudioMode }) {
  const src = resolveMediaUrl(url);
  if (!src) return null;
  return mode === "exam" ? <ExamAudio key={url} src={src} stored={url} /> : <FreeAudio src={src} />;
}

const SECTION_PREFIX = "jlpt:section-audio:";

interface SectionProgress {
  pos: number;
  done: boolean;
}

function readProgress(key: string): SectionProgress {
  try {
    const raw = sessionStorage.getItem(SECTION_PREFIX + key);
    if (raw) return JSON.parse(raw) as SectionProgress;
  } catch {
    // fall through to a fresh start
  }
  return { pos: 0, done: false };
}

function writeProgress(key: string, value: SectionProgress) {
  try {
    sessionStorage.setItem(SECTION_PREFIX + key, JSON.stringify(value));
  } catch {
    // without storage a reload restarts the file
  }
}

function clock(seconds: number) {
  const s = Math.max(0, Math.round(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

/**
 * Exam: the audio of the whole listening section. It starts as soon as the block opens, runs to the end
 * without pause or seek, and keeps playing while the candidate moves between questions (the player lives
 * above the question panel). A reload resumes from the saved position, like the real recording continuing.
 */
export function SectionAudio({ url, progressKey }: { url: string; progressKey: string }) {
  const src = resolveMediaUrl(url);
  const ref = useRef<HTMLAudioElement>(null);
  const [state, setState] = useState<"loading" | "playing" | "blocked" | "done">(() =>
    readProgress(progressKey).done ? "done" : "loading"
  );
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);

  if (!src) return null;

  if (state === "done") {
    return (
      <div className="shrink-0 border-b border-black/5 bg-white px-6 py-2.5 text-sm text-neutral-500">
        Phần nghe đã kết thúc.
      </div>
    );
  }

  return (
    <div className="shrink-0 border-b border-black/5 bg-white px-6 py-2.5">
      <div className="mx-auto flex max-w-5xl items-center gap-3">
        <Volume2 size={16} className="shrink-0 text-neutral-400" />
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-black/5">
          <div
            className="h-full bg-neutral-900"
            style={{ width: `${duration ? Math.min(100, (position / duration) * 100) : 0}%` }}
          />
        </div>
        <span className="w-24 shrink-0 text-right text-xs tabular-nums text-neutral-400">
          {state === "playing" ? `${clock(position)} / ${clock(duration)}` : ""}
        </span>
        {state === "blocked" && (
          <button
            type="button"
            onClick={() => void ref.current?.play()}
            className="flex items-center gap-1.5 rounded-full bg-neutral-900 px-4 py-1.5 text-xs font-medium text-white"
          >
            <Play size={12} /> Bắt đầu nghe
          </button>
        )}
      </div>
      <audio
        ref={ref}
        src={src}
        preload="auto"
        autoPlay
        onLoadedMetadata={(e) => {
          const a = e.currentTarget;
          setDuration(a.duration);
          const saved = readProgress(progressKey).pos;
          if (saved > 0 && saved < a.duration) a.currentTime = saved;
          // Autoplay can be refused by the browser; the button above is the fallback
          a.play().catch(() => setState("blocked"));
        }}
        onPlay={() => setState("playing")}
        onTimeUpdate={(e) => {
          const a = e.currentTarget;
          setPosition(a.currentTime);
          writeProgress(progressKey, { pos: a.currentTime, done: false });
        }}
        onEnded={() => {
          writeProgress(progressKey, { pos: 0, done: true });
          setState("done");
        }}
        onPause={(e) => {
          if (!e.currentTarget.ended) void e.currentTarget.play().catch(() => setState("blocked"));
        }}
        onContextMenu={(e) => e.preventDefault()}
      />
    </div>
  );
}

/** Review after the exam: the section audio with the normal player. */
export function SectionAudioReview({ url, inline = false }: { url: string; inline?: boolean }) {
  const src = resolveMediaUrl(url);
  if (!src) return null;
  // inline: sits inside another toolbar row instead of taking a row of its own
  if (inline) {
    return (
      <div className="flex min-w-0 flex-1 items-center gap-2">
        <Volume2 size={16} className="shrink-0 text-neutral-400" />
        <audio controls preload="metadata" src={src} className="h-8 w-full min-w-0" />
      </div>
    );
  }
  return (
    <div className="shrink-0 border-b border-black/5 bg-white px-6 py-2.5">
      <div className="mx-auto flex max-w-5xl items-center gap-3">
        <Volume2 size={16} className="shrink-0 text-neutral-400" />
        <audio controls preload="metadata" src={src} className="h-8 w-full" />
      </div>
    </div>
  );
}
