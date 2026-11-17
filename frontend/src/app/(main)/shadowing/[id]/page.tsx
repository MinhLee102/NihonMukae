"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Check, Eye, EyeOff, Mic, Play, Repeat, Square, Star, Volume2 } from "lucide-react";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { useYouTubePlayer } from "@/components/shadowing/useYouTubePlayer";
import { getApiErrorMessage } from "@/lib/api/flashcard";
import { getShadowing, saveShadowingMarks, type ShadowingDetail } from "@/lib/api/shadowing";
import { formatClock } from "@/lib/shadowing/parseTranscript";

type PlayMode = "through" | "once" | "loop";

const MODES: { value: PlayMode; label: string }[] = [
  { value: "through", label: "Liên tục" },
  { value: "once", label: "Từng câu" },
  { value: "loop", label: "Lặp câu" },
];
const RATES = [0.5, 0.75, 1];
const END_PAD = 0.3;

function Practice({ material }: { material: ShadowingDetail }) {
  const { segments } = material;
  const player = useYouTubePlayer(material.videoId);
  const [mode, setMode] = useState<PlayMode>("through");
  const [rate, setRate] = useState(1);
  const [active, setActive] = useState<number | null>(null);
  const [hideScript, setHideScript] = useState(false);
  const [onlyMarked, setOnlyMarked] = useState(false);
  const [marked, setMarked] = useState<Set<number>>(new Set(material.marked));
  const [saveState, setSaveState] = useState<"idle" | "saving" | "saved" | "error">("idle");
  const [recordings, setRecordings] = useState<Record<number, string>>({});
  const [recordingIdx, setRecordingIdx] = useState<number | null>(null);
  const [micError, setMicError] = useState<string | null>(null);

  // Giá trị mới nhất cho vòng kiểm tra thời gian, tránh dựng lại interval mỗi lần đổi state
  const live = useRef({ mode, active, onlyMarked, marked, recordingIdx, compareIdx: null as number | null });
  live.current = { ...live.current, mode, active, onlyMarked, marked, recordingIdx };
  const recorder = useRef<MediaRecorder | null>(null);
  const chunks = useRef<Blob[]>([]);
  const stream = useRef<MediaStream | null>(null);
  const mineAudio = useRef<HTMLAudioElement | null>(null);
  const urls = useRef<string[]>([]);
  const listRef = useRef<HTMLDivElement>(null);
  const markedOrder = useMemo(() => Array.from(marked).sort((a, b) => a - b), [marked]);

  const { seekAndPlay, pause, currentTime, playing, setRate: setPlayerRate } = player;

  const playSegment = useCallback(
    (idx: number) => {
      mineAudio.current?.pause();
      live.current.compareIdx = null;
      setActive(idx);
      seekAndPlay(segments[idx].start);
    },
    [segments, seekAndPlay]
  );

  const stopRecording = useCallback(() => {
    if (recorder.current && recorder.current.state !== "inactive") recorder.current.stop();
  }, []);

  // Vòng kiểm tra thời gian: tới cuối câu thì dừng, lặp hoặc chuyển câu tuỳ chế độ
  useEffect(() => {
    if (!player.ready) return;
    const timer = window.setInterval(() => {
      const s = live.current;
      const t = currentTime();
      const cur = s.active != null ? segments[s.active] : null;

      if (s.recordingIdx != null) {
        const seg = segments[s.recordingIdx];
        if (t >= seg.end + END_PAD) {
          pause();
          stopRecording();
        }
        return;
      }
      if (s.compareIdx != null) {
        const seg = segments[s.compareIdx];
        if (t >= seg.end) {
          pause();
          s.compareIdx = null;
          void mineAudio.current?.play();
        }
        return;
      }
      if (!playing) return;

      if (s.mode === "through") {
        const idx = segments.findIndex((x) => t >= x.start && t < x.end);
        if (idx < 0) return;
        if (s.onlyMarked && !s.marked.has(idx)) {
          const next = Array.from(s.marked).sort((a, b) => a - b).find((m) => segments[m].start > t);
          if (next != null) return playSegment(next);
          pause();
          return;
        }
        if (idx !== s.active) setActive(idx);
      } else if (cur && s.active != null) {
        if (s.mode === "once" && t >= cur.end) pause();
        if (s.mode === "loop" && t >= cur.end - 0.05) seekAndPlay(cur.start);
      }
    }, 120);
    return () => window.clearInterval(timer);
  }, [player.ready, segments, playing, currentTime, pause, seekAndPlay, playSegment, stopRecording]);

  useEffect(() => {
    if (player.ready) setPlayerRate(rate);
  }, [rate, player.ready, setPlayerRate]);

  useEffect(() => {
    if (active == null) return;
    listRef.current?.querySelector<HTMLElement>(`[data-idx="${active}"]`)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [active]);

  // Lưu các câu đánh dấu sau khi người dùng ngừng bấm một lúc
  const firstRender = useRef(true);
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    setSaveState("saving");
    const timer = window.setTimeout(() => {
      saveShadowingMarks(material.id, markedOrder)
        .then(() => setSaveState("saved"))
        .catch(() => setSaveState("error"));
    }, 800);
    return () => window.clearTimeout(timer);
  }, [markedOrder, material.id]);

  useEffect(
    () => () => {
      stream.current?.getTracks().forEach((tr) => tr.stop());
      urls.current.forEach((u) => URL.revokeObjectURL(u));
    },
    []
  );

  const toggleMark = (idx: number) =>
    setMarked((prev) => {
      const next = new Set(prev);
      if (next.has(idx)) next.delete(idx);
      else next.add(idx);
      return next;
    });

  const startRecording = async (idx: number) => {
    setMicError(null);
    if (typeof MediaRecorder === "undefined" || !navigator.mediaDevices?.getUserMedia) {
      setMicError("Trình duyệt này không hỗ trợ thu âm.");
      return;
    }
    try {
      stream.current ??= await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      setMicError("Chưa được phép dùng micro. Hãy cho phép micro cho trang này trong trình duyệt.");
      return;
    }
    mineAudio.current?.pause();
    chunks.current = [];
    const rec = new MediaRecorder(stream.current);
    rec.ondataavailable = (e) => e.data.size > 0 && chunks.current.push(e.data);
    rec.onstop = () => {
      const url = URL.createObjectURL(new Blob(chunks.current, { type: rec.mimeType }));
      urls.current.push(url);
      setRecordings((prev) => {
        if (prev[idx]) URL.revokeObjectURL(prev[idx]);
        return { ...prev, [idx]: url };
      });
      setRecordingIdx(null);
    };
    recorder.current = rec;
    rec.start();
    setRecordingIdx(idx);
    setActive(idx);
    seekAndPlay(segments[idx].start);
  };

  const playMine = (idx: number) => {
    pause();
    mineAudio.current?.pause();
    mineAudio.current = new Audio(recordings[idx]);
    void mineAudio.current.play();
  };

  // So sánh: nghe câu gốc rồi tới bản thu của mình
  const compare = (idx: number) => {
    mineAudio.current?.pause();
    mineAudio.current = new Audio(recordings[idx]);
    live.current.compareIdx = idx;
    setActive(idx);
    seekAndPlay(segments[idx].start);
  };

  const visible = segments.map((s, i) => ({ s, i })).filter(({ i }) => !onlyMarked || marked.has(i));

  return (
    <div className="mx-auto max-w-6xl">
      <Link href="/shadowing" className="mb-4 inline-flex items-center gap-1.5 text-xs text-neutral-500 hover:text-neutral-800">
        <ArrowLeft size={14} /> Danh sách bài
      </Link>
      <h1 className="mb-5 text-xl font-semibold text-neutral-900">{material.title}</h1>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1fr_400px]">
        <div className="h-fit space-y-4 lg:sticky lg:top-0">
          <div className="relative aspect-video w-full overflow-hidden rounded-2xl bg-black">
            <div ref={player.containerRef} className="absolute inset-0 [&_iframe]:h-full [&_iframe]:w-full" />
          </div>
          {player.error && <p className="rounded-xl bg-red-50 px-4 py-3 text-xs text-red-700">{player.error}</p>}

          <div className="rounded-2xl border border-black/5 bg-white p-4">
            <p className="mb-2 text-[10px] tracking-widest text-neutral-400">CÁCH PHÁT</p>
            <div className="grid grid-cols-3 gap-1.5">
              {MODES.map((m) => (
                <button
                  key={m.value}
                  type="button"
                  onClick={() => setMode(m.value)}
                  className={`rounded-lg border py-2 text-xs transition ${
                    mode === m.value ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/10 text-neutral-600 hover:border-black/30"
                  }`}
                >
                  {m.label}
                </button>
              ))}
            </div>

            <p className="mb-2 mt-4 text-[10px] tracking-widest text-neutral-400">TỐC ĐỘ</p>
            <div className="grid grid-cols-3 gap-1.5">
              {RATES.map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setRate(r)}
                  className={`rounded-lg border py-2 text-xs transition ${
                    rate === r ? "border-neutral-900 bg-neutral-900 text-white" : "border-black/10 text-neutral-600 hover:border-black/30"
                  }`}
                >
                  {r}x
                </button>
              ))}
            </div>

            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => setHideScript((v) => !v)}
                className="flex items-center gap-1.5 rounded-full border border-black/10 px-3 py-1.5 text-xs text-neutral-600 hover:border-black/30"
              >
                {hideScript ? <Eye size={13} /> : <EyeOff size={13} />}
                {hideScript ? "Hiện script" : "Ẩn script"}
              </button>
              <button
                type="button"
                onClick={() => setOnlyMarked((v) => !v)}
                disabled={marked.size === 0 && !onlyMarked}
                className={`flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs transition disabled:opacity-40 ${
                  onlyMarked ? "border-amber-500 bg-amber-50 text-amber-700" : "border-black/10 text-neutral-600 hover:border-black/30"
                }`}
              >
                <Star size={13} />
                Chỉ câu cần luyện ({marked.size})
              </button>
            </div>
            <p className="mt-3 text-[11px] leading-4 text-neutral-400">
              Nên đeo tai nghe khi thu âm để micro không thu lại tiếng video. Bản thu chỉ nằm trong trình duyệt, tải lại trang là mất.
            </p>
            {micError && <p className="mt-2 text-xs text-red-600">{micError}</p>}
          </div>
        </div>

        <div className="rounded-2xl border border-black/5 bg-white">
          <div className="flex items-center justify-between border-b border-black/5 px-4 py-3 text-xs text-neutral-500">
            <span>{visible.length} câu</span>
            <span aria-live="polite">
              {saveState === "saving" && "Đang lưu..."}
              {saveState === "saved" && (
                <span className="inline-flex items-center gap-1 text-emerald-700">
                  <Check size={12} /> Đã lưu
                </span>
              )}
              {saveState === "error" && <span className="text-red-600">Chưa lưu được</span>}
            </span>
          </div>
          <div ref={listRef} className="max-h-[70vh] overflow-y-auto">
            {visible.length === 0 && <p className="p-6 text-center text-sm text-neutral-400">Chưa có câu nào được đánh dấu.</p>}
            {visible.map(({ s, i }) => {
              const isActive = active === i;
              const isMarked = marked.has(i);
              const isRecording = recordingIdx === i;
              return (
                <div
                  key={i}
                  data-idx={i}
                  className={`border-b border-black/5 px-4 py-3 transition ${isActive ? "bg-[#F5F3EE]" : ""}`}
                >
                  <div className="flex items-start gap-3">
                    <button
                      type="button"
                      onClick={() => playSegment(i)}
                      aria-label={`Phát câu ${i + 1}`}
                      className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-neutral-900 text-white"
                    >
                      <Play size={12} />
                    </button>
                    <div className="min-w-0 flex-1">
                      <p className="text-[10px] text-neutral-400">{formatClock(s.start)}</p>
                      <p
                        lang="ja"
                        className={`text-[15px] leading-6 text-neutral-900 transition ${
                          hideScript && !isActive ? "select-none blur-sm hover:blur-none" : ""
                        }`}
                      >
                        {s.text}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => toggleMark(i)}
                      aria-label={isMarked ? "Bỏ đánh dấu cần luyện" : "Đánh dấu cần luyện"}
                      aria-pressed={isMarked}
                      className={`shrink-0 p-1 transition ${isMarked ? "text-amber-500" : "text-neutral-300 hover:text-neutral-500"}`}
                    >
                      <Star size={16} fill={isMarked ? "currentColor" : "none"} />
                    </button>
                  </div>

                  <div className="ml-10 mt-2 flex flex-wrap items-center gap-1.5">
                    {isRecording ? (
                      <button
                        type="button"
                        onClick={() => {
                          pause();
                          stopRecording();
                        }}
                        className="flex items-center gap-1 rounded-full bg-red-600 px-3 py-1 text-[11px] text-white"
                      >
                        <Square size={11} /> Đang thu, bấm để dừng
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => startRecording(i)}
                        disabled={recordingIdx != null}
                        className="flex items-center gap-1 rounded-full border border-black/10 px-3 py-1 text-[11px] text-neutral-600 hover:border-black/30 disabled:opacity-40"
                      >
                        <Mic size={11} /> Thu âm
                      </button>
                    )}
                    {recordings[i] && !isRecording && (
                      <>
                        <button
                          type="button"
                          onClick={() => playMine(i)}
                          className="flex items-center gap-1 rounded-full border border-black/10 px-3 py-1 text-[11px] text-neutral-600 hover:border-black/30"
                        >
                          <Volume2 size={11} /> Nghe bản thu
                        </button>
                        <button
                          type="button"
                          onClick={() => compare(i)}
                          className="flex items-center gap-1 rounded-full border border-black/10 px-3 py-1 text-[11px] text-neutral-600 hover:border-black/30"
                        >
                          <Repeat size={11} /> Gốc rồi tới bạn
                        </button>
                      </>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

function Loader() {
  const { id } = useParams<{ id: string }>();
  const [material, setMaterial] = useState<ShadowingDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getShadowing(id)
      .then(setMaterial)
      .catch((err) => setError(getApiErrorMessage(err, "Không tải được bài này.")));
  }, [id]);

  if (error) return <p className="py-20 text-center text-sm text-red-600">{error}</p>;
  if (!material) return <p className="py-20 text-center text-sm text-neutral-400">Đang tải...</p>;
  return <Practice material={material} />;
}

export default function ShadowingPracticePage() {
  return (
    <RequireAuth message="Bạn cần đăng nhập để dùng Shadowing.">
      <Loader />
    </RequireAuth>
  );
}
