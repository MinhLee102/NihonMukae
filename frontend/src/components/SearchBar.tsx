"use client";

import { useState } from "react";
import { Search, PenLine, Keyboard, Volume2, Camera, ChevronDown, Loader2 } from "lucide-react";

const SEARCH_MODES = [
  { id: "ai", label: "Chế độ AI" },
  { id: "vocab", label: "Từ vựng" },
  { id: "kanji", label: "Hán tự" },
  { id: "sentence", label: "Mẫu câu" },
  { id: "grammar", label: "Ngữ pháp" },
  { id: "ja-ja", label: "Nhật - Nhật" },
] as const;

// Modes that are not built yet stay visible but cannot be selected
const AVAILABLE_MODES: string[] = ["vocab", "kanji"];

export type SearchMode = (typeof SEARCH_MODES)[number]["id"];

interface SearchBarProps {
  value?: string;
  onChange?: (val: string) => void;
  /** Chế độ đang chọn; truyền cùng onModeChange để trang cha điều khiển */
  mode?: SearchMode;
  onModeChange?: (mode: SearchMode) => void;
  onSearch?: (query: string, mode: SearchMode) => void;
  isLoading?: boolean;
}
/**
 * Search input + mode tabs. Local UI state only (which tab is selected,
 * the input's text) — no actual search call wired in yet.
 */
export function SearchBar({ value, onChange, mode, onModeChange, onSearch, isLoading = false }: SearchBarProps) {
  const [internalMode, setInternalMode] = useState<SearchMode>("vocab");
  const activeMode = mode ?? internalMode;
  const setActiveMode = (m: SearchMode) => {
    setInternalMode(m);
    onModeChange?.(m);
  };
  const [internalQuery, setInternalQuery] = useState("");

  const query = value !== undefined ? value : internalQuery;

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    if (value === undefined) setInternalQuery(val);
    onChange?.(val);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      onSearch?.(query, activeMode);
    }
  };

  return (
    <div className="rounded-2xl border border-black/5 bg-white p-2 shadow-sm">
      <div className="flex items-center gap-3 px-3 py-2">
        {isLoading ? (
          <Loader2 size={18} className="shrink-0 animate-spin text-neutral-400" />
        ) : (
          <Search size={18} strokeWidth={1.75} className="shrink-0 text-neutral-400" />
        )}
        <input
          value={query}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          placeholder={activeMode === "kanji" ? "食, thực, ăn, ショク, shoku..." : "日本, nihon, Nhật Bản..."}
          className="min-w-0 flex-1 bg-transparent text-sm text-neutral-800 outline-none placeholder:text-neutral-400"
        />
        <div className="flex shrink-0 items-center gap-3 text-neutral-400">
          <button type="button" aria-label="Viết tay" disabled title="Đang phát triển" className="cursor-not-allowed opacity-50">
            <PenLine size={16} strokeWidth={1.75} />
          </button>
          <button type="button" aria-label="Bàn phím ảo" disabled title="Đang phát triển" className="cursor-not-allowed opacity-50">
            <Keyboard size={16} strokeWidth={1.75} />
          </button>
          <button type="button" aria-label="Tìm bằng giọng nói" disabled title="Đang phát triển" className="cursor-not-allowed opacity-50">
            <Volume2 size={16} strokeWidth={1.75} />
          </button>
          <button type="button" aria-label="Tìm bằng hình ảnh" disabled title="Đang phát triển" className="cursor-not-allowed opacity-50">
            <Camera size={16} strokeWidth={1.75} />
          </button>
          <div className="mx-1 h-4 w-px bg-black/10" />
          <button
            type="button"
            disabled
            title="Đang phát triển"
            className="flex cursor-not-allowed items-center gap-1 text-sm text-neutral-500 opacity-50"
          >
            Nhật - Việt
            <ChevronDown size={14} strokeWidth={1.75} />
          </button>
        </div>
      </div>

      <div className="flex items-center gap-1 px-1 pb-1 pt-1">
        {SEARCH_MODES.map((mode) => (
          <button
            key={mode.id}
            type="button"
            onClick={() => setActiveMode(mode.id)}
            disabled={!AVAILABLE_MODES.includes(mode.id)}
            title={AVAILABLE_MODES.includes(mode.id) ? undefined : "Đang phát triển"}
            className={`rounded-full px-3.5 py-1.5 text-sm transition-colors ${
              activeMode === mode.id
                ? "bg-neutral-900 text-white"
                : AVAILABLE_MODES.includes(mode.id)
                  ? "text-neutral-500 hover:bg-black/5 hover:text-neutral-800"
                  : "cursor-not-allowed text-neutral-300"
            }`}
          >
            {mode.label}
          </button>
        ))}
      </div>
    </div>
  );
}