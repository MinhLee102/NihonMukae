"use client";

import type { ReactNode } from "react";
import { Check, Flag, Volume2, X } from "lucide-react";
import { FormattedText } from "@/components/jlpt/FormattedText";
import { SplitPane } from "@/components/jlpt/SplitPane";
import { SECTION_LABELS, type GroupViewDto, type Section } from "@/lib/api/jlpt";

export interface PanelQuestion {
  position: number;
  total: number;
  section?: Section;
  typeName?: string;
  typeCode?: string;
  question: string;
  options: string[];
  audioUrl?: string;
  selected?: string;
  /** Set when the answer may be shown: after submit, or right after answering in instant mode. */
  correctAnswer?: string;
  explanation?: string;
  mondaiNo?: number;
  instructionJa?: string;
  instructionVi?: string;
}

const LETTERS = ["A", "B", "C", "D"];

function AudioBlock({ url, isListening }: { url?: string; isListening: boolean }) {
  if (url) {
    return (
      <div className="mb-4 flex items-center gap-3 rounded-2xl border border-black/5 bg-white px-4 py-3">
        <Volume2 size={16} className="shrink-0 text-neutral-400" />
        <audio controls preload="none" src={url} className="h-8 w-full" />
      </div>
    );
  }
  if (isListening) {
    return (
      <p className="mb-4 rounded-xl bg-amber-50 px-4 py-2.5 text-xs text-amber-700">
        Câu này chưa có audio. Phần văn bản bên dưới là bản thay thế.
      </p>
    );
  }
  return null;
}

/** Underscore runs in an instruction (＿＿＿) are drawn as blank lines, like the printed paper. */
function InstructionText({ text }: { text: string }) {
  return (
    <>
      {text.split(/(＿+)/).map((part, i) =>
        part.startsWith("＿") ? (
          <span
            key={i}
            aria-hidden="true"
            className="inline-block border-b border-neutral-900 align-baseline"
            style={{ width: `${Math.max(part.length, 2) * 0.9}em`, height: "1em" }}
          />
        ) : (
          <span key={i}>{part}</span>
        )
      )}
    </>
  );
}

/** "問題 N" with a rule under it, then the instruction shared by every question of the type. */
function InstructionHeader({ no, ja, vi }: { no?: number; ja: string; vi?: string }) {
  return (
    <div className="shrink-0 border-b border-black/5 px-6 pb-4 pt-5">
      <div className="mx-auto max-w-5xl">
        {no !== undefined && (
          <p className="border-b-2 border-neutral-900 pb-1 text-xl font-medium text-neutral-900">問題{no}</p>
        )}
        <p className="mt-3 text-[15px] leading-7 text-neutral-900">
          <InstructionText text={ja} />
        </p>
        {vi && <p className="mt-1 text-xs text-neutral-400">{vi}</p>}
      </div>
    </div>
  );
}

function GroupContent({ group }: { group: GroupViewDto }) {
  return (
    <div className="flex flex-col gap-4 p-6">
      {group.title && <p className="text-xs font-semibold tracking-wide text-neutral-400">{group.title}</p>}
      {group.audioUrl && <AudioBlock url={group.audioUrl} isListening />}
      {group.imageUrl && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={group.imageUrl} alt="" className="max-h-80 w-full rounded-xl object-contain" />
      )}
      {group.passage && (
        <div className="rounded-2xl border border-black/5 bg-white p-5 text-[15px] leading-8 text-neutral-800">
          <FormattedText text={group.passage} />
        </div>
      )}
    </div>
  );
}

/**
 * One question with its options. A question that belongs to a group (reading passage, shared audio)
 * is shown in a draggable split: the passage on the left, the question on the right.
 */
export function QuestionPanel({
  question,
  group,
  splitKey,
  onSelect,
  disabled,
  flagged,
  onToggleFlag,
  footer,
}: {
  question: PanelQuestion;
  group?: GroupViewDto;
  splitKey: string;
  onSelect?: (letter: string) => void;
  disabled?: boolean;
  flagged?: boolean;
  onToggleFlag?: () => void;
  footer?: ReactNode;
}) {
  const revealed = question.correctAnswer !== undefined;
  const sectionLabel = question.section ? SECTION_LABELS[question.section].vi : "";
  const isListening = question.section === "chokai";

  const body = (
    <div className="mx-auto flex w-full max-w-3xl flex-col px-6 py-6">
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <p className="text-[10px] font-medium uppercase tracking-[0.2em] text-neutral-400">
            {sectionLabel}
            {question.typeName ? ` · ${question.typeName}` : ""}
          </p>
          <p className="mt-1 text-xs text-neutral-500">
            Câu {question.position} / {question.total}
          </p>
        </div>
        {onToggleFlag && (
          <button
            type="button"
            onClick={onToggleFlag}
            className={`flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-xs transition ${
              flagged
                ? "border-amber-300 bg-amber-50 text-amber-700"
                : "border-black/10 bg-white text-neutral-500 hover:border-black/30"
            }`}
          >
            <Flag size={12} />
            Đánh dấu
          </button>
        )}
      </div>

      {!group?.audioUrl && <AudioBlock url={question.audioUrl} isListening={isListening && !question.audioUrl} />}

      <p className="mb-6 text-lg font-semibold leading-8 text-neutral-900">
        <FormattedText text={question.question} />
      </p>

      <div className="flex flex-col gap-3">
        {question.options.map((option, i) => {
          const letter = LETTERS[i];
          const isSelected = question.selected === letter;
          const isCorrect = revealed && question.correctAnswer === letter;
          const isWrong = revealed && isSelected && !isCorrect;
          let style = "border-black/5 bg-white hover:border-black/25";
          if (isCorrect) style = "border-emerald-400 bg-emerald-50";
          else if (isWrong) style = "border-red-300 bg-red-50";
          else if (isSelected) style = "border-neutral-900 bg-white shadow-sm";
          return (
            <button
              key={letter}
              type="button"
              disabled={disabled}
              onClick={() => onSelect?.(letter)}
              aria-pressed={isSelected}
              className={`flex items-center gap-4 rounded-2xl border px-5 py-4 text-left transition disabled:cursor-default ${style}`}
            >
              <span
                className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-medium ${
                  isSelected && !revealed ? "bg-neutral-900 text-white" : "bg-[#EFEBE2] text-neutral-500"
                }`}
              >
                {i + 1}
              </span>
              <span className="min-w-0 flex-1 text-[15px] text-neutral-900">
                <FormattedText text={option} />
              </span>
              {isCorrect && <Check size={18} className="shrink-0 text-emerald-600" />}
              {isWrong && <X size={18} className="shrink-0 text-red-500" />}
            </button>
          );
        })}
      </div>

      {revealed && question.explanation && (
        <div className="mt-5 rounded-2xl border border-sky-100 bg-sky-50 px-5 py-4">
          <p className="text-[10px] font-semibold tracking-widest text-sky-600">GIẢI THÍCH</p>
          <p className="mt-1.5 text-sm leading-6 text-sky-900">
            <FormattedText text={question.explanation} />
          </p>
        </div>
      )}
      {revealed && question.selected === undefined && (
        <p className="mt-4 text-xs text-neutral-400">Bạn chưa trả lời câu này.</p>
      )}

      {footer && <div className="mt-8">{footer}</div>}
    </div>
  );

  const hasGroup = group && (group.passage || group.imageUrl || group.audioUrl);
  return (
    <div className="flex h-full min-h-0 flex-col">
      {question.instructionJa && (
        <InstructionHeader no={question.mondaiNo} ja={question.instructionJa} vi={question.instructionVi} />
      )}
      <div className="min-h-0 flex-1">
        {hasGroup && group ? (
          <SplitPane storageKey={splitKey} left={<GroupContent group={group} />} right={body} />
        ) : (
          <div className="h-full overflow-y-auto">{body}</div>
        )}
      </div>
    </div>
  );
}
