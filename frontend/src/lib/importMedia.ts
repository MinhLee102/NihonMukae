/**
 * Admins write plain file names in the import JSON ("n3_track01.mp3") and pick
 * the files next to it. After upload, every exact file name in a string value is
 * swapped for its "/media/{id}" address, and "[[img:name.png]]" tokens inside
 * longer texts get the same treatment.
 */
export function substituteMedia(value: unknown, urlsByName: Map<string, string>): unknown {
  if (typeof value === 'string') {
    const exact = urlsByName.get(value.trim().toLowerCase());
    if (exact) return exact;
    return value.replace(/\[\[img:([^\]]+)\]\]/g, (whole, name: string) => {
      const url = urlsByName.get(name.trim().toLowerCase());
      return url ? `[[img:${url}]]` : whole;
    });
  }
  if (Array.isArray(value)) return value.map((v) => substituteMedia(v, urlsByName));
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([k, v]) => [k, substituteMedia(v, urlsByName)]),
    );
  }
  return value;
}

const MEDIA_NAME = /.+\.(mp3|m4a|ogg|wav|png|jpe?g|webp)$/i;

/** File names the JSON refers to: string values that look like a media file, and [[img:name]] tokens. */
export function collectMediaRefs(value: unknown, found: Set<string> = new Set()): Set<string> {
  if (typeof value === 'string') {
    const text = value.trim();
    if (MEDIA_NAME.test(text) && !text.startsWith('/') && !text.startsWith('http') && !text.includes('[[')) {
      found.add(text);
    }
    for (const m of Array.from(text.matchAll(/\[\[img:([^\]]+)\]\]/g))) {
      const name = m[1].trim();
      if (!name.startsWith('/') && !name.startsWith('http')) found.add(name);
    }
  } else if (Array.isArray(value)) {
    value.forEach((v) => collectMediaRefs(v, found));
  } else if (value && typeof value === 'object') {
    Object.values(value as Record<string, unknown>).forEach((v) => collectMediaRefs(v, found));
  }
  return found;
}

export const IMPORT_TEMPLATE = {
  test: { level: 3, title: 'JLPT N3 2023-07', year: 2023, month: 7, source_note: 'Đề mẫu', audio_url: 'n3_2023_07_listening.mp3' },
  questions: [
    {
      type: 'kanji_reading',
      question: '<u>試験</u>に合格しました。',
      options: ['しけん', 'じけん', 'しげん', 'じげん'],
      correct: 'A',
      explanation: '試験 đọc là しけん',
    },
  ],
};
