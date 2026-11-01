import type { ReactNode } from "react";

/**
 * Question text keeps its line breaks, and a tiny markup is supported: <u>word</u> underlines a word
 * (JLPT questions underline the word being asked about). Everything else is plain text.
 */
export function FormattedText({ text, className = "" }: { text: string; className?: string }) {
  const parts: ReactNode[] = [];
  const pattern = /<u>(.*?)<\/u>/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    parts.push(
      <u key={match.index} className="decoration-2 underline-offset-4">
        {match[1]}
      </u>
    );
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return <span className={`whitespace-pre-wrap ${className}`}>{parts}</span>;
}
