"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";

const MIN_RATIO = 0.25;
const MAX_RATIO = 0.75;

function clamp(value: number): number {
  return Math.min(Math.max(value, MIN_RATIO), MAX_RATIO);
}

/**
 * Two panes side by side with a draggable divider (stacked on narrow screens). The position is
 * remembered in localStorage; double-click or Home resets it, arrow keys nudge it.
 */
export function SplitPane({
  left,
  right,
  storageKey,
  defaultRatio = 0.5,
}: {
  left: ReactNode;
  right: ReactNode;
  storageKey: string;
  defaultRatio?: number;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [ratio, setRatio] = useState(defaultRatio);
  const [dragging, setDragging] = useState(false);
  // The ref is read by pointermove straight away: state would lag behind a fast press-and-move
  const draggingRef = useRef(false);
  const ratioRef = useRef(defaultRatio);

  useEffect(() => {
    try {
      const saved = parseFloat(localStorage.getItem(storageKey) ?? "");
      if (Number.isFinite(saved)) {
        ratioRef.current = clamp(saved);
        setRatio(ratioRef.current);
      }
    } catch {
      // storage unavailable: keep the default
    }
  }, [storageKey]);

  const persist = useCallback(
    (value: number) => {
      try {
        localStorage.setItem(storageKey, String(value));
      } catch {
        // ignore
      }
    },
    [storageKey]
  );

  const moveTo = (clientX: number) => {
    const box = containerRef.current?.getBoundingClientRect();
    if (!box || box.width === 0) return;
    const next = clamp((clientX - box.left) / box.width);
    ratioRef.current = next;
    setRatio(next);
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    let next = ratio;
    if (e.key === "ArrowLeft") next = clamp(ratio - 0.02);
    else if (e.key === "ArrowRight") next = clamp(ratio + 0.02);
    else if (e.key === "Home") next = defaultRatio;
    else return;
    e.preventDefault();
    ratioRef.current = next;
    setRatio(next);
    persist(next);
  };

  return (
    <div
      ref={containerRef}
      className="flex h-full min-h-0 flex-col md:grid"
      style={{ gridTemplateColumns: `${ratio * 100}% 10px minmax(0, 1fr)` }}
    >
      <div data-scroll="left" className="min-h-0 min-w-0 overflow-y-auto">{left}</div>
      <div
        role="separator"
        aria-orientation="vertical"
        aria-valuenow={Math.round(ratio * 100)}
        aria-valuemin={MIN_RATIO * 100}
        aria-valuemax={MAX_RATIO * 100}
        tabIndex={0}
        title="Kéo để đổi kích thước, nhấn đúp để đặt lại"
        onPointerDown={(e) => {
          // Capture keeps the drag alive outside the divider; not every pointer supports it
          try {
            e.currentTarget.setPointerCapture(e.pointerId);
          } catch {
            // dragging still works while the pointer stays over the page
          }
          draggingRef.current = true;
          setDragging(true);
        }}
        onPointerMove={(e) => draggingRef.current && moveTo(e.clientX)}
        onPointerUp={(e) => {
          if (draggingRef.current) moveTo(e.clientX);
          try {
            e.currentTarget.releasePointerCapture(e.pointerId);
          } catch {
            // nothing was captured
          }
          draggingRef.current = false;
          setDragging(false);
          persist(ratioRef.current);
        }}
        onDoubleClick={() => {
          ratioRef.current = defaultRatio;
          setRatio(defaultRatio);
          persist(defaultRatio);
        }}
        onKeyDown={onKeyDown}
        className="group hidden cursor-col-resize touch-none items-stretch justify-center outline-none md:flex"
      >
        <span
          className={`my-2 w-[3px] rounded-full transition-colors ${
            dragging ? "bg-neutral-500" : "bg-black/10 group-hover:bg-neutral-400 group-focus-visible:bg-neutral-400"
          }`}
        />
      </div>
      <div data-scroll="right" className="min-h-0 min-w-0 overflow-y-auto">{right}</div>
    </div>
  );
}
