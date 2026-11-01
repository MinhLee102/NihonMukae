// Hiragana, katakana and CJK ideographs
const JAPANESE = /[぀-ヿ㐀-鿿]/;

/**
 * Font class for user-written card text. The serif face suits Japanese, but the
 * fallback serif font renders Vietnamese diacritics badly (e.g. "Quyê´t"), so
 * text without any Japanese characters uses the sans face instead.
 */
export function cardFontClass(text: string): string {
  return JAPANESE.test(text) ? "font-serif" : "font-sans";
}
