import argparse
import re
import sys
 
import pandas as pd
import psycopg2
from psycopg2.extras import execute_values
from tqdm import tqdm
 
import config
 
HTML_TAG_RE = re.compile(r"<[^>]+>")
 
 
def strip_html(value):
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return None
    text = HTML_TAG_RE.sub("", str(value)).strip()
    return text or None
 
 
def clean_han_viet(value):
    """Bỏ ngoặc kép góc 「...」 bao quanh han_viet, vd '「ĐỊA VỰC ĐẠI BIỂU」' -> 'ĐỊA VỰC ĐẠI BIỂU'."""
    text = strip_html(value)
    if text is None:
        return None
    text = text.strip("「」").strip()
    return text or None
 
 
def clean_pos(value):
    """Bỏ ký hiệu trang trí đầu type_word, vd '☆ Danh từ' -> 'Danh từ'."""
    text = strip_html(value)
    if text is None:
        return None
    text = re.sub(r"^[☆★\s]+", "", text).strip()
    return text or None
 
 
def to_int(value):
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return None
    try:
        return int(value)
    except (ValueError, TypeError):
        return None
 
 
def parse_jlpt(value):
    """Chuẩn hoá jlpt_level về số 1-5 (1=N1 ... 5=N5). Chấp nhận 'N5', '5', 5, None."""
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return None
    text = str(value).strip().upper()
    match = re.search(r"([1-5])", text)
    return int(match.group(1)) if match else None
 
 
def count_csv_rows(path):
    with open(path, "r", encoding="utf-8") as f:
        return sum(1 for _ in f) - 1  # trừ header
 
 
def import_vocab(conn, path, chunk_size):
    """mazii_vocabs.csv -> words. Trả về map {mazii_vocab_id: words.id}."""
    id_map = {}
    total = count_csv_rows(path)
    with tqdm(total=total, desc="words") as pbar:
        for chunk in pd.read_csv(path, chunksize=chunk_size):
            rows = []
            for r in chunk.itertuples(index=False):
                word = strip_html(getattr(r, "word", None))
                kana = strip_html(getattr(r, "phonetic", None))
                if not word or not kana:
                    # kana NOT NULL trong schema -> bỏ qua dòng thiếu dữ liệu bắt buộc
                    pbar.update(1)
                    continue
                rows.append((
                    to_int(r.id),
                    word,
                    kana,
                    clean_han_viet(getattr(r, "han_viet", None)),
                    strip_html(getattr(r, "pronunciation", None)),  # ký hiệu trọng âm (pitch accent), giữ nguyên
                    clean_pos(getattr(r, "type_word", None)),
                ))
                pbar.update(1)
 
            if not rows:
                continue
 
            with conn.cursor() as cur:
                result = execute_values(
                    cur,
                    """
                    INSERT INTO words (mazii_vocab_id, kanji, kana, han_viet, pronunciation, pos)
                    VALUES %s
                    ON CONFLICT (mazii_vocab_id) DO UPDATE SET
                        kanji = EXCLUDED.kanji,
                        kana = EXCLUDED.kana,
                        han_viet = EXCLUDED.han_viet,
                        pronunciation = EXCLUDED.pronunciation,
                        pos = EXCLUDED.pos
                    RETURNING id, mazii_vocab_id
                    """,
                    rows,
                    fetch=True,
                )
                for word_id, mazii_vocab_id in result:
                    id_map[mazii_vocab_id] = word_id
            conn.commit()
    return id_map
 
 
def import_meanings(conn, path, chunk_size, vocab_id_map):
    """meaning_detail.csv -> word_meanings. Trả về map
    {mazii_meaning_id: (word_meanings.id, word_id)}."""
    id_map = {}
    skipped_no_parent = 0
    # word_meanings có UNIQUE(word_id, lang, sort_order) -> mỗi nghĩa của
    # cùng 1 từ phải có sort_order tăng dần (0, 1, 2...), không thể để mặc
    # định 0 hết vì 1 từ thường có nhiều nghĩa. Đếm theo thứ tự xuất hiện
    # trong CSV, giữ nguyên xuyên suốt toàn bộ file (không reset theo chunk)
    # để chạy lại nhiều lần vẫn ra cùng thứ tự -> ON CONFLICT vẫn khớp đúng dòng.
    sort_counters = {}
    total = count_csv_rows(path)
    with tqdm(total=total, desc="word_meanings") as pbar:
        for chunk in pd.read_csv(path, chunksize=chunk_size):
            rows = []
            for r in chunk.itertuples(index=False):
                word_id = vocab_id_map.get(to_int(r.vocab_id))
                meaning = strip_html(getattr(r, "meaning", None))
                if word_id is None or not meaning:
                    skipped_no_parent += 1
                    pbar.update(1)
                    continue
                sort_order = sort_counters.get(word_id, 0)
                sort_counters[word_id] = sort_order + 1
                rows.append((to_int(r.id), word_id, meaning, sort_order))
                pbar.update(1)
 
            if not rows:
                continue
 
            with conn.cursor() as cur:
                result = execute_values(
                    cur,
                    """
                    INSERT INTO word_meanings (mazii_meaning_id, word_id, lang, meaning, sort_order)
                    VALUES %s
                    ON CONFLICT (mazii_meaning_id) DO UPDATE SET
                        meaning = EXCLUDED.meaning,
                        sort_order = EXCLUDED.sort_order
                    RETURNING id, mazii_meaning_id, word_id
                    """,
                    [(mid, wid, "vi", meaning, sort_order) for mid, wid, meaning, sort_order in rows],
                    fetch=True,
                )
                for wm_id, mazii_meaning_id, word_id in result:
                    id_map[mazii_meaning_id] = (wm_id, word_id)
            conn.commit()
    if skipped_no_parent:
        print(f"  (bỏ qua {skipped_no_parent} dòng meaning_details: thiếu vocab_id khớp hoặc thiếu meaning)")
    return id_map
 
 
def import_examples(conn, path, chunk_size, meaning_id_map):
    """examples.csv -> example_sentences."""
    skipped = 0
    total = count_csv_rows(path)
    with tqdm(total=total, desc="example_sentences") as pbar:
        for chunk in pd.read_csv(path, chunksize=chunk_size):
            rows = []
            for r in chunk.itertuples(index=False):
                mapped = meaning_id_map.get(to_int(r.meaning_detail_id))
                jp = strip_html(getattr(r, "jp", None))
                if mapped is None or not jp:
                    skipped += 1
                    pbar.update(1)
                    continue
                _, word_id = mapped
                rows.append((word_id, "ja", "vi", jp, strip_html(getattr(r, "vi", None))))
                pbar.update(1)
 
            if not rows:
                continue
 
            with conn.cursor() as cur:
                execute_values(
                    cur,
                    """
                    INSERT INTO example_sentences (word_id, lang_src, lang_tgt, sentence, translation)
                    VALUES %s
                    """,
                    rows,
                )
            conn.commit()
    if skipped:
        print(f"  (bỏ qua {skipped} dòng examples: thiếu meaning_detail_id khớp hoặc thiếu câu jp)")
 
 
def import_kanji(conn, path, chunk_size):
    """kanji.csv -> kanji (bảng độc lập). 1 dòng/1 ký tự kanji — dữ liệu gốc
    có thể có nhiều dòng trùng cùng 1 ký tự, nên dedupe theo `word` (ký tự),
    giữ lại bản ghi xuất hiện sau cùng trong mỗi batch."""
    total = count_csv_rows(path)
    inserted = 0
    with tqdm(total=total, desc="kanji") as pbar:
        for chunk in pd.read_csv(path, chunksize=chunk_size):
            rows_by_word = {}
            for r in chunk.itertuples(index=False):
                word = strip_html(getattr(r, "word", None))
                if not word:
                    pbar.update(1)
                    continue
                # dùng dict keyed theo word -> tự dedupe trong cùng 1 batch;
                # Postgres báo lỗi nếu 1 câu INSERT ON CONFLICT đụng cùng 1
                # dòng 2 lần, nên phải dedupe trước khi gửi xuống DB.
                rows_by_word[word] = (
                    to_int(r.id),
                    word,
                    strip_html(getattr(r, "kunyomi", None)),
                    strip_html(getattr(r, "onyomi", None)),
                    to_int(getattr(r, "strokes", None)),
                    parse_jlpt(getattr(r, "jlpt_level", None)),
                    strip_html(getattr(r, "meaning", None)),
                    strip_html(getattr(r, "explain", None)),
                )
                pbar.update(1)
            rows = list(rows_by_word.values())
 
            if not rows:
                continue
 
            with conn.cursor() as cur:
                execute_values(
                    cur,
                    """
                    INSERT INTO kanji (mazii_kanji_id, word, kunyomi, onyomi, strokes, jlpt_level, meaning, explain)
                    VALUES %s
                    ON CONFLICT (word) DO UPDATE SET
                        mazii_kanji_id = EXCLUDED.mazii_kanji_id,
                        kunyomi = EXCLUDED.kunyomi,
                        onyomi = EXCLUDED.onyomi,
                        strokes = EXCLUDED.strokes,
                        jlpt_level = EXCLUDED.jlpt_level,
                        meaning = EXCLUDED.meaning,
                        explain = EXCLUDED.explain
                    """,
                    rows,
                )
            conn.commit()
            inserted += len(rows)
    return inserted
 
 
def rebuild_search_vector(conn):
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE words SET search_vector =
                to_tsvector('simple', coalesce(kanji, '') || ' ' || kana || ' ' || coalesce(romaji, ''))
            """
        )
    conn.commit()
 
 
def main():
    parser = argparse.ArgumentParser(description="Import Mazii CSV vào PostgreSQL")
    parser.add_argument("--db-url", default=config.DB_URL)
    parser.add_argument("--data-dir", default=config.DATA_DIR)
    parser.add_argument("--chunk-size", type=int, default=config.CHUNK_SIZE)
    parser.add_argument("--skip-vocab", action="store_true")
    parser.add_argument("--skip-meanings", action="store_true")
    parser.add_argument("--skip-examples", action="store_true")
    parser.add_argument("--skip-kanji", action="store_true")
    args = parser.parse_args()
 
    conn = psycopg2.connect(args.db_url)
    try:
        vocab_id_map = {}
        meaning_id_map = {}
 
        if not args.skip_vocab:
            print("== Import words (mazii_vocabs.csv) ==")
            vocab_id_map = import_vocab(conn, f"{args.data_dir}/mazii_vocabs.csv", args.chunk_size)
            print(f"  -> {len(vocab_id_map)} từ đã import")
        else:
            print("Bỏ qua vocab. Lưu ý: cần vocab_id_map để import meanings/examples đúng,"
                  " nên nếu --skip-vocab thì --skip-meanings và --skip-examples cũng nên bật.")
 
        if not args.skip_meanings:
            print("== Import word_meanings (meaning_detail.csv) ==")
            meaning_id_map = import_meanings(
                conn, f"{args.data_dir}/meaning_detail.csv", args.chunk_size, vocab_id_map
            )
            print(f"  -> {len(meaning_id_map)} nghĩa đã import")
 
        if not args.skip_examples:
            print("== Import example_sentences (examples.csv) ==")
            import_examples(conn, f"{args.data_dir}/examples.csv", args.chunk_size, meaning_id_map)
 
        if not args.skip_kanji:
            print("== Import kanji (kanji.csv) ==")
            count = import_kanji(conn, f"{args.data_dir}/kanji.csv", args.chunk_size)
            print(f"  -> {count} kanji đã import")
 
        print("== Rebuild search_vector ==")
        rebuild_search_vector(conn)
 
        print("Xong.")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
 
 
if __name__ == "__main__":
    sys.exit(main())