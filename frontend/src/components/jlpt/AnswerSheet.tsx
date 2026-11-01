"use client";

import type { ReactNode } from "react";

export type CellState = "unanswered" | "answered" | "correct" | "wrong" | "skipped";

export interface SheetCell {
  position: number;
  state: CellState;
  flagged?: boolean;
  /** Not reachable right now (question in a block that is not open). */
  locked?: boolean;
}

const CELL_STYLE: Record<CellState, string> = {
  unanswered: "border-black/10 bg-white text-neutral-500 hover:border-black/30",
  answered: "border-black/10 bg-[#E8E3D6] text-neutral-700 hover:border-black/30",
  correct: "border-emerald-200 bg-emerald-50 text-emerald-700 hover:border-emerald-400",
  wrong: "border-red-200 bg-red-50 text-red-600 hover:border-red-400",
  skipped: "border-black/10 bg-white text-neutral-400 hover:border-black/30",
};

/** Grid of question numbers. Used as the answer sheet while working and as the result map afterwards. */
export function AnswerSheet({
  cells,
  current,
  onJump,
  header,
  legend,
}: {
  cells: SheetCell[];
  current: number | null;
  onJump: (position: number) => void;
  header?: ReactNode;
  legend?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-5">
      {header}
      <div className="grid grid-cols-5 gap-2">
        {cells.map((cell) => {
          const isCurrent = cell.position === current;
          return (
            <button
              key={cell.position}
              type="button"
              disabled={cell.locked}
              onClick={() => onJump(cell.position)}
              aria-label={`Câu ${cell.position}`}
              aria-current={isCurrent ? "true" : undefined}
              className={`relative flex h-11 items-center justify-center rounded-xl border text-xs font-medium transition disabled:cursor-not-allowed disabled:opacity-40 ${
                isCurrent ? "border-neutral-900 bg-neutral-900 text-white" : CELL_STYLE[cell.state]
              }`}
            >
              {cell.position}
              {cell.flagged && <span className="absolute right-1 top-1 h-1.5 w-1.5 rounded-full bg-amber-400" />}
            </button>
          );
        })}
      </div>
      {legend}
    </div>
  );
}
