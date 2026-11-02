import type { ReactNode } from "react";
import { resolveMediaUrl } from "@/lib/media";

/**
 * Question text keeps its line breaks, and a tiny markup is supported: <u>word</u> underlines a word
 * (JLPT questions underline the word being asked about). [[img:/media/ID]] shows an uploaded image;
 * only our own media path or an https address is accepted. Everything else is plain text.
 */
export function FormattedText({ text, className = "" }: { text: string; className?: string }) {
  const parts: ReactNode[] = [];
  const pattern = /<u>(.*?)<\/u>|\[\[img:([^\]]+)\]\]/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    const image = match[2]?.trim();
    if (image !== undefined) {
      if (image.startsWith("/media/") || image.startsWith("https://")) {
        parts.push(
          // eslint-disable-next-line @next/next/no-img-element
          <img key={match.index} src={resolveMediaUrl(image)} alt="" className="my-2 block max-h-72 max-w-full" />
        );
      }
    } else {
      parts.push(
        <u key={match.index} className="decoration-2 underline-offset-4">
          {match[1]}
        </u>
      );
    }
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return <span className={`whitespace-pre-wrap ${className}`}>{parts}</span>;
}
