"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/** Phần của YouTube IFrame Player API mà app dùng. */
export interface YTPlayer {
  playVideo(): void;
  pauseVideo(): void;
  seekTo(seconds: number, allowSeekAhead: boolean): void;
  getCurrentTime(): number;
  getPlayerState(): number;
  setPlaybackRate(rate: number): void;
  destroy(): void;
}

interface YTNamespace {
  Player: new (
    el: HTMLElement,
    opts: {
      videoId: string;
      playerVars?: Record<string, number | string>;
      events?: {
        onReady?: () => void;
        onStateChange?: (e: { data: number }) => void;
        onError?: (e: { data: number }) => void;
      };
    }
  ) => YTPlayer;
}

declare global {
  interface Window {
    YT?: YTNamespace;
    onYouTubeIframeAPIReady?: () => void;
  }
}

export const YT_PLAYING = 1;

let apiPromise: Promise<YTNamespace> | null = null;

function loadApi(): Promise<YTNamespace> {
  if (window.YT?.Player) return Promise.resolve(window.YT);
  if (!apiPromise) {
    apiPromise = new Promise((resolve, reject) => {
      const previous = window.onYouTubeIframeAPIReady;
      window.onYouTubeIframeAPIReady = () => {
        previous?.();
        if (window.YT) resolve(window.YT);
      };
      const script = document.createElement("script");
      script.src = "https://www.youtube.com/iframe_api";
      script.onerror = () => {
        apiPromise = null;
        reject(new Error("Không tải được trình phát YouTube."));
      };
      document.head.appendChild(script);
    });
  }
  return apiPromise;
}

/** Tạo player YouTube trong phần tử `containerRef` và báo trạng thái phát. */
export function useYouTubePlayer(videoId: string) {
  const containerRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<YTPlayer | null>(null);
  const [ready, setReady] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const host = containerRef.current;
    if (!host) return;
    // YouTube thay hẳn phần tử bằng iframe, nên tạo một phần tử con để khi huỷ vẫn còn host
    const mount = document.createElement("div");
    host.appendChild(mount);
    loadApi()
      .then((YT) => {
        if (cancelled) return;
        playerRef.current = new YT.Player(mount, {
          videoId,
          playerVars: { playsinline: 1, rel: 0, modestbranding: 1, cc_load_policy: 0, iv_load_policy: 3 },
          events: {
            onReady: () => !cancelled && setReady(true),
            onStateChange: (e) => !cancelled && setPlaying(e.data === YT_PLAYING),
            onError: (e) =>
              !cancelled &&
              setError(
                e.data === 101 || e.data === 150
                  ? "Chủ video không cho nhúng video này. Hãy thử video khác."
                  : "Không phát được video này."
              ),
          },
        });
      })
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
      playerRef.current?.destroy();
      playerRef.current = null;
      host.innerHTML = "";
      setReady(false);
      setPlaying(false);
    };
  }, [videoId]);

  const seekAndPlay = useCallback((seconds: number) => {
    const p = playerRef.current;
    if (!p) return;
    p.seekTo(Math.max(0, seconds), true);
    p.playVideo();
  }, []);

  const pause = useCallback(() => playerRef.current?.pauseVideo(), []);
  const resume = useCallback(() => playerRef.current?.playVideo(), []);
  const setRate = useCallback((rate: number) => playerRef.current?.setPlaybackRate(rate), []);
  const currentTime = useCallback(() => playerRef.current?.getCurrentTime() ?? 0, []);

  return { containerRef, ready, playing, error, seekAndPlay, pause, resume, setRate, currentTime };
}
