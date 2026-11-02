import type { ReactNode } from "react";
import { resolveMediaUrl } from "@/lib/media";

/**
 * Question and passage text keeps its line breaks, plus a small safe markup (never raw HTML):
 *
 *   <u>word</u>             underline (the word a question asks about)
 *   **word**                bold
 *   [[img:/media/ID]]       an uploaded image (only our media path or an https address)
 *   | a | b |               table rows; a row of |---|---| after the first row makes it a header
 *   [[box]]...[[/box]]      bordered box (notice, flyer, email frame)
 *   [[center]]...[[/center]], [[right]]...[[/right]]   alignment (titles, dates, signatures)
 */

const INLINE = /<u>(.*?)<\/u>|\*\*(.+?)\*\*|\[\[img:([^\]]+)\]\]/g;
const BLOCK_OPEN = /\[\[(box|center|right)\]\]/;
const TABLE_LINE = /^\s*\|/;

function inline(text: string, keyPrefix: string): ReactNode[] {
  const parts: ReactNode[] = [];
  const pattern = new RegExp(INLINE.source, "g");
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    const key = `${keyPrefix}-${match.index}`;
    if (match[1] !== undefined) {
      parts.push(
        <u key={key} className="decoration-2 underline-offset-4">
          {match[1]}
        </u>
      );
    } else if (match[2] !== undefined) {
      parts.push(<strong key={key}>{match[2]}</strong>);
    } else {
      const image = match[3].trim();
      if (image.startsWith("/media/") || image.startsWith("https://")) {
        parts.push(
          // eslint-disable-next-line @next/next/no-img-element
          <img key={key} src={resolveMediaUrl(image)} alt="" className="my-2 block max-h-72 max-w-full" />
        );
      }
    }
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

function splitCells(line: string): string[] {
  const cells = line.trim().split("|").map((c) => c.trim());
  if (cells[0] === "") cells.shift();
  if (cells.length > 0 && cells[cells.length - 1] === "") cells.pop();
  return cells;
}

function renderTable(rows: string[], key: string): ReactNode {
  const cells = rows.map(splitCells);
  const isSeparator = (row: string[]) => row.length > 0 && row.every((c) => /^:?-{2,}:?$/.test(c));
  const hasHeader = cells.length > 1 && isSeparator(cells[1]);
  const body = cells.filter((row, i) => !isSeparator(row) && !(hasHeader && i === 0));
  return (
    <table key={key} className="my-3 w-full border-collapse text-[15px]">
      {hasHeader && (
        <thead>
          <tr>
            {cells[0].map((c, i) => (
              <th key={i} className="border border-neutral-400 bg-black/5 px-3 py-1.5 text-left font-medium">
                {inline(c, `${key}-h${i}`)}
              </th>
            ))}
          </tr>
        </thead>
      )}
      <tbody>
        {body.map((row, r) => (
          <tr key={r}>
            {row.map((c, i) => (
              <td key={i} className="border border-neutral-400 px-3 py-1.5 align-top">
                {inline(c, `${key}-${r}-${i}`)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Plain lines and table rows (no block tags). */
function renderLines(text: string, keyPrefix: string): ReactNode[] {
  const out: ReactNode[] = [];
  const lines = text.split("\n");
  let plain: string[] = [];
  let table: string[] = [];
  const flushPlain = () => {
    if (plain.length === 0) return;
    out.push(
      <span key={`${keyPrefix}-p${out.length}`} className="block whitespace-pre-wrap">
        {inline(plain.join("\n"), `${keyPrefix}-i${out.length}`)}
      </span>
    );
    plain = [];
  };
  const flushTable = () => {
    if (table.length === 0) return;
    out.push(renderTable(table, `${keyPrefix}-t${out.length}`));
    table = [];
  };
  for (const line of lines) {
    if (TABLE_LINE.test(line)) {
      flushPlain();
      table.push(line);
    } else {
      flushTable();
      plain.push(line);
    }
  }
  flushPlain();
  flushTable();
  return out;
}

/** Splits on [[box]], [[center]], [[right]] pairs (nesting allowed) and renders the rest as lines. */
function renderBlocks(text: string, keyPrefix: string): ReactNode[] {
  const out: ReactNode[] = [];
  let rest = text;
  let n = 0;
  while (rest.length > 0) {
    const open = BLOCK_OPEN.exec(rest);
    if (!open) {
      out.push(...renderLines(rest, `${keyPrefix}-r${n}`));
      break;
    }
    const name = open[1];
    const before = rest.slice(0, open.index).replace(/\n$/, "");
    if (before) out.push(...renderLines(before, `${keyPrefix}-b${n}`));

    const afterOpen = rest.slice(open.index + open[0].length);
    const tags = new RegExp(`\\[\\[(/?)${name}\\]\\]`, "g");
    let depth = 1;
    let end = -1;
    let closeLength = 0;
    let t: RegExpExecArray | null;
    while ((t = tags.exec(afterOpen)) !== null) {
      depth += t[1] ? -1 : 1;
      if (depth === 0) {
        end = t.index;
        closeLength = t[0].length;
        break;
      }
    }
    const inner = (end === -1 ? afterOpen : afterOpen.slice(0, end)).replace(/^\n/, "").replace(/\n$/, "");
    const key = `${keyPrefix}-k${n}`;
    const children = renderBlocks(inner, key);
    if (name === "box") {
      out.push(
        <div key={key} className="my-3 rounded-lg border-2 border-neutral-500 px-4 py-3">
          {children}
        </div>
      );
    } else {
      out.push(
        <div key={key} className={name === "center" ? "text-center" : "text-right"}>
          {children}
        </div>
      );
    }
    rest = end === -1 ? "" : afterOpen.slice(end + closeLength).replace(/^\n/, "");
    n++;
  }
  return out;
}

export function FormattedText({ text, className = "" }: { text: string; className?: string }) {
  const hasBlocks = BLOCK_OPEN.test(text) || /^\s*\|/m.test(text);
  if (!hasBlocks) {
    return <span className={`whitespace-pre-wrap ${className}`}>{inline(text, "f")}</span>;
  }
  return <div className={className}>{renderBlocks(text, "f")}</div>;
}
