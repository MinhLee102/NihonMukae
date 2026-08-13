export type RecentWord = {
  id: string;
  /** The single kanji/kana shown in the avatar circle, e.g. "日" */
  avatarChar: string;
  /** Word in Japanese script, e.g. "日本語" */
  word: string;
  /** Reading, e.g. "にほんご" */
  reading: string;
  /** Vietnamese meaning, e.g. "Tiếng Nhật" */
  meaning: string;
};

/**
 * Single "recently looked up" entry. Reused as-is by the search history
 * modal (FE-05)
 */
export function RecentWordCard({ word }: { word: RecentWord }) {
  return (
    <button
      type="button"
      className="flex w-full items-center gap-3 rounded-2xl border border-black/5 bg-white px-4 py-3 text-left transition hover:border-black/10 hover:shadow-sm"
    >
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#EFE9DD] font-serif text-lg text-neutral-700">
        {word.avatarChar}
      </div>
      <div className="min-w-0">
        <p className="truncate font-medium text-neutral-900">{word.word}</p>
        <p className="truncate text-xs text-neutral-400">
          {word.reading} · {word.meaning}
        </p>
      </div>
    </button>
  );
}