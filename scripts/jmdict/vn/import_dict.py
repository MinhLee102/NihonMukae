"""
Nạp từ điển JMdict + kết quả Gemini đã qua check.py vào các bảng dict_* (migration V18).

    python vn/import_dict.py                 # DB dev (MAZII_DB_URL hoặc postgresql://dev:dev@localhost:5432/nihon_mukae)
    python vn/import_dict.py --db-url ...    # DB khác

Chạy lại bao nhiêu lần cũng được (upsert): mọi từ đã chọn đều có mặt với nghĩa tiếng Anh của JMdict,
từ nào đã có kết quả Gemini thì có thêm nghĩa tiếng Việt, Hán Việt và câu ví dụ.
"""
import argparse
import glob
import os
import sys

import psycopg2
from psycopg2.extras import Json, execute_values

sys.path.insert(0, os.path.dirname(__file__))
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from hanviet import HanViet, read_json_array  # noqa: E402
from prepare import RESULTS, ROOT, SOURCES, headword, pos_label, select  # noqa: E402
import config  # noqa: E402  (scripts/jmdict/config.py: DB_URL)


def load_results(prefix):
    """Gộp W001.json, W001_retry.json... thành {id hoặc chữ kanji: mục}. Kết quả sau ghi đè kết quả trước."""
    merged = {}
    for path in sorted(glob.glob(os.path.join(RESULTS, f"{prefix}*.json"))):
        # File còn trống (tạo sẵn để dán dần) hoặc chưa qua check.py thì bỏ qua
        for item in read_json_array(path):
            key = item.get("id" if prefix == "W" else "k") if isinstance(item, dict) else None
            if key is not None and ("m" in item or prefix == "K"):
                merged[key] = item
    return merged


def compose_han_viet(word, hv):
    """Hán Việt tự ghép khi mọi chữ kanji của từ chỉ có một âm trong bảng."""
    choices = hv.word_choices(word) if word else None
    if choices and all(len(c) == 1 for c in choices):
        return " ".join(c[0] for c in choices)
    return None


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--db-url", default=config.DB_URL)
    p.add_argument("--jmdict", default=os.path.join(ROOT, "JMdict_e"))
    args = p.parse_args()

    hv = HanViet(SOURCES, RESULTS)
    words = load_results("W")
    kanji = load_results("K")
    entries = select(args.jmdict)

    entry_rows, meaning_rows, example_rows = [], [], []
    for e in entries:
        ai = words.get(e["id"])
        han_viet = (ai or {}).get("hv") or compose_han_viet(e["word"], hv)
        entry_rows.append((
            e["id"], e["word"], e["reading"], headword(e), e["other_forms"], e["other_readings"],
            e["usually_kana"], pos_label(e["senses"][0]["pos"]) if e["senses"] else None,
            e["jlpt"], e["rank"], e["group"], han_viet,
            Json([s["gloss"] for s in e["senses"]]),
        ))
        if ai:
            meaning_rows += [(e["id"], i, m["vi"]) for i, m in enumerate(ai["m"])]
            example_rows += [(e["id"], i, x["ja"], x["vi"]) for i, x in enumerate(ai["ex"])]

    with psycopg2.connect(args.db_url) as conn, conn.cursor() as cur:
        execute_values(cur, """
            INSERT INTO dict_entries (id, word, reading, headword, other_forms, other_readings, usually_kana, pos,
                                      jlpt_level, freq_rank, priority_group, han_viet, gloss_en)
            VALUES %s
            ON CONFLICT (id) DO UPDATE SET
                word = EXCLUDED.word, reading = EXCLUDED.reading, headword = EXCLUDED.headword,
                other_forms = EXCLUDED.other_forms, other_readings = EXCLUDED.other_readings,
                usually_kana = EXCLUDED.usually_kana, pos = EXCLUDED.pos, jlpt_level = EXCLUDED.jlpt_level,
                freq_rank = EXCLUDED.freq_rank, priority_group = EXCLUDED.priority_group,
                han_viet = EXCLUDED.han_viet, gloss_en = EXCLUDED.gloss_en, updated_at = now()
        """, entry_rows, page_size=2000)

        # Nghĩa và ví dụ của các từ có kết quả mới: xoá bản cũ rồi chèn lại cho gọn
        ids = sorted(words)
        if ids:
            cur.execute("DELETE FROM dict_meanings WHERE entry_id = ANY(%s)", (ids,))
            cur.execute("DELETE FROM dict_examples WHERE entry_id = ANY(%s)", (ids,))
        execute_values(cur, "INSERT INTO dict_meanings (entry_id, idx, meaning) VALUES %s", meaning_rows, page_size=5000)
        execute_values(cur, "INSERT INTO dict_examples (entry_id, idx, ja, vi) VALUES %s", example_rows, page_size=5000)

        execute_values(cur, """
            INSERT INTO dict_kanji (kanji, han_viet, meaning) VALUES %s
            ON CONFLICT (kanji) DO UPDATE SET han_viet = EXCLUDED.han_viet, meaning = EXCLUDED.meaning
        """, [(k, v["hv"], v.get("nghia")) for k, v in kanji.items()], page_size=2000)

    with_vi = sum(1 for e in entries if e["id"] in words)
    print(f"Đã nạp {len(entry_rows)} từ ({with_vi} có nghĩa tiếng Việt), {len(example_rows)} câu ví dụ, {len(kanji)} chữ kanji.")


if __name__ == "__main__":
    main()
