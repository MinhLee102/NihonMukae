"use client";

import type { ReactNode } from "react";
import { Check, Flag, X } from "lucide-react";
import { FormattedText } from "@/components/jlpt/FormattedText";
import { SplitPane } from "@/components/jlpt/SplitPane";
import { ListeningAudio, type AudioMode } from "@/components/jlpt/ListeningAudio";
import { resolveMediaUrl } from "@/lib/media";
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

function AudioBlock({ url, isListening, mode }: { url?: string; isListening: boolean; mode: AudioMode }) {
  if (url) return <ListeningAudio url={url} mode={mode} />;
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

const LISTEN_PLACEHOLDER = /^\s*質問を聞いてください[。.]?\s*$/;

/** Drops a line that only says 質問を聞いてください。 (listening questions carry it as a placeholder). */
function stripListenPlaceholder(text: string): string {
  return text
    .split("\n")
    .filter((line) => !LISTEN_PLACEHOLDER.test(line))
    .join("\n")
    .trim();
}

/** "問題 N" with a rule under it, then the instruction shared by every question of the type. */
function InstructionHeader({ no, ja, vi }: { no?: number; ja: string; vi?: string }) {
  return (
    <div className="shrink-0 border-b border-black/5 px-6 pb-5 pt-6">
      <div className="mx-auto max-w-5xl">
        {no !== undefined && (
          <p className="border-b-2 border-neutral-900 pb-1 text-2xl font-medium text-neutral-900">問題{no}</p>
        )}
        <p className="mt-4 text-xl font-medium leading-9 text-neutral-900">
          <InstructionText text={ja} />
        </p>
        {vi && <p className="mt-1.5 text-sm text-neutral-500">{vi}</p>}
      </div>
    </div>
  );
}

function GroupContent({ group, audioMode }: { group: GroupViewDto; audioMode: AudioMode }) {
  return (
    <div className="flex flex-col gap-4 p-6">
      {group.title && <p className="text-xs font-semibold tracking-wide text-neutral-400">{group.title}</p>}
      {group.audioUrl && <AudioBlock url={group.audioUrl} isListening mode={audioMode} />}
      {group.imageUrl && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={resolveMediaUrl(group.imageUrl)} alt="" className="max-h-80 w-full rounded-xl object-contain" />
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
  onReport,
  footer,
  audioMode = "free",
  sectionAudio = false,
}: {
  question: PanelQuestion;
  group?: GroupViewDto;
  splitKey: string;
  onSelect?: (letter: string) => void;
  disabled?: boolean;
  flagged?: boolean;
  onToggleFlag?: () => void;
  /** Opens the feedback form for this question. */
  onReport?: () => void;
  footer?: ReactNode;
  /** exam: audio plays once with no controls. free: normal player. */
  audioMode?: AudioMode;
  /** The whole listening section has one recording (player shown above), so no per-question audio is expected. */
  sectionAudio?: boolean;
}) {
  const revealed = question.correctAnswer !== undefined;
  const sectionLabel = question.section ? SECTION_LABELS[question.section].vi : "";
  const isListening = question.section === "chokai";
  // When every answer is a picture, lay them out as a 2 column grid like the printed paper
  // The instruction above already says "listen to the question first", so the placeholder line is dropped
  const promptText = isListening && question.instructionJa ? stripListenPlaceholder(question.question) : question.question;
  const imageOptions = question.options.length > 0 && question.options.every((o) => /^\s*\[\[img:[^\]]+\]\]\s*$/.test(o));

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
        {onReport && (
          <button
            type="button"
            onClick={onReport}
            className="flex items-center gap-1.5 rounded-full border border-black/10 bg-white px-3.5 py-1.5 text-xs text-neutral-500 transition hover:border-black/30"
          >
            Báo lỗi
          </button>
        )}
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

      {!group?.audioUrl && <AudioBlock url={question.audioUrl} isListening={isListening && !question.audioUrl && !sectionAudio} mode={audioMode} />}

      {promptText && (
        <div className="mb-6 text-lg font-semibold leading-8 text-neutral-900">
          <FormattedText text={promptText} />
        </div>
      )}

      <div
        className={imageOptions ? "" : "flex flex-col gap-3"}
        style={imageOptions ? { display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 } : undefined}
      >
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
              <span
                className={`min-w-0 flex-1 text-[15px] text-neutral-900 ${imageOptions ? "[&_img]:mx-auto [&_img]:max-h-44" : ""}`}
              >
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
          <SplitPane storageKey={splitKey} left={<GroupContent group={group} audioMode={audioMode} />} right={body} />
        ) : (
          <div className="h-full overflow-y-auto">{body}</div>
        )}
      </div>
    </div>
  );
}
