export interface Segment {
  start: number;
  end: number;
  text: string;
}

export interface ParseResult {
  segments: Segment[];
  format: "srt" | "vtt" | "youtube" | "none";
}

const TIME = String.raw`(?:\d{1,2}:)?\d{1,2}:\d{2}(?:[.,]\d{1,3})?`;
const CUE_LINE = new RegExp(`^(${TIME})\\s*-->\\s*(${TIME})`);
const STAMP_ONLY = new RegExp(`^(${TIME})$`);
const STAMP_PREFIX = new RegExp(`^(${TIME})\\s+(\\S.*)$`);
// Thời lượng câu cuối của bản chép lời YouTube (không có mốc kết thúc)
const LAST_SEGMENT_SECONDS = 5;
const MAX_SEGMENT_SECONDS = 15;

/** "1:02:03.5" hoặc "0:12" hoặc "00:00:01,000" → giây. */
export function parseStamp(raw: string): number {
  const parts = raw.replace(",", ".").split(":").map(Number);
  let seconds = 0;
  for (const p of parts) seconds = seconds * 60 + p;
  return seconds;
}

function cleanText(lines: string[]): string {
  return lines
    .join(" ")
    .replace(/<[^>]*>/g, "") // thẻ định dạng của VTT như <c>, <00:01.000>
    .replace(/\[(?:音楽|拍手|笑い|Music|Applause)\]/gi, "")
    .replace(/\s+/g, " ")
    .trim();
}

/** SRT và VTT: mỗi khối có dòng "bắt đầu --> kết thúc" rồi tới chữ. */
function parseCues(lines: string[]): Segment[] {
  const out: Segment[] = [];
  let i = 0;
  while (i < lines.length) {
    const m = CUE_LINE.exec(lines[i].trim());
    if (!m) {
      i++;
      continue;
    }
    const text: string[] = [];
    i++;
    while (i < lines.length && lines[i].trim() !== "") {
      text.push(lines[i].trim());
      i++;
    }
    const t = cleanText(text);
    if (t) out.push({ start: parseStamp(m[1]), end: parseStamp(m[2]), text: t });
  }
  return out;
}

/** Bản chép lời copy từ YouTube: dòng mốc giờ "0:12" rồi dòng chữ, hoặc "0:12 chữ" trên một dòng. */
function parseYoutube(lines: string[]): Segment[] {
  const marks: { start: number; text: string[] }[] = [];
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    const only = STAMP_ONLY.exec(line);
    if (only) {
      marks.push({ start: parseStamp(only[1]), text: [] });
      continue;
    }
    const prefixed = STAMP_PREFIX.exec(line);
    if (prefixed) {
      marks.push({ start: parseStamp(prefixed[1]), text: [prefixed[2]] });
      continue;
    }
    if (marks.length > 0) marks[marks.length - 1].text.push(line);
  }
  const out: Segment[] = [];
  marks.forEach((mk, idx) => {
    const text = cleanText(mk.text);
    if (!text) return;
    const next = marks[idx + 1];
    // Khoảng trống dài (nhạc nền, im lặng) không tính vào câu: giới hạn độ dài một câu
    const end = next && next.start > mk.start ? Math.min(next.start, mk.start + MAX_SEGMENT_SECONDS) : mk.start + LAST_SEGMENT_SECONDS;
    out.push({ start: mk.start, end, text });
  });
  return out;
}

/** Tự nhận dạng SRT, VTT hay bản chép lời YouTube. Văn bản không có mốc giờ cho kết quả rỗng. */
export function parseTranscript(input: string): ParseResult {
  const text = input.replace(/^﻿/, "").replace(/\r\n?/g, "\n");
  const lines = text.split("\n");
  if (lines.some((l) => CUE_LINE.test(l.trim()))) {
    const segments = parseCues(lines);
    return { segments: normalize(segments), format: /^\s*WEBVTT/.test(text) ? "vtt" : "srt" };
  }
  const youtube = parseYoutube(lines);
  if (youtube.length > 0) return { segments: normalize(youtube), format: "youtube" };
  return { segments: [], format: "none" };
}

/** Sắp theo thời gian, bỏ câu trùng liên tiếp (phụ đề tự động hay lặp dòng), đảm bảo end > start. */
function normalize(list: Segment[]): Segment[] {
  const sorted = [...list].sort((a, b) => a.start - b.start);
  const out: Segment[] = [];
  for (const s of sorted) {
    const prev = out[out.length - 1];
    if (prev && prev.text === s.text) {
      prev.end = Math.max(prev.end, s.end);
      continue;
    }
    out.push({ start: s.start, end: s.end > s.start ? s.end : s.start + 1, text: s.text });
  }
  return out;
}

/** Lấy mã video 11 ký tự từ link YouTube (watch, youtu.be, shorts, embed) hoặc chính mã đó. */
export function extractVideoId(input: string): string | null {
  const s = input.trim();
  if (/^[A-Za-z0-9_-]{11}$/.test(s)) return s;
  try {
    const url = new URL(s.startsWith("http") ? s : `https://${s}`);
    const host = url.hostname.replace(/^www\.|^m\./, "");
    let id: string | null = null;
    if (host === "youtu.be") id = url.pathname.slice(1).split("/")[0];
    else if (host === "youtube.com" || host === "youtube-nocookie.com") {
      id = url.searchParams.get("v");
      if (!id) {
        const m = /^\/(?:embed|shorts|live|v)\/([^/?]+)/.exec(url.pathname);
        id = m ? m[1] : null;
      }
    }
    return id && /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
  } catch {
    return null;
  }
}

export function formatClock(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = h > 0 ? String(m).padStart(2, "0") : String(m);
  return `${h > 0 ? `${h}:` : ""}${mm}:${String(sec).padStart(2, "0")}`;
}
