"""
Nạp thông tin chữ kanji vào dict_kanji (migration V23): âm on/kun, nghĩa tiếng Anh, số nét, bộ, lớp, tần suất
(KANJIDIC2) và thứ tự nét viết (KanjiVG).

Nguồn (tải về thư mục riêng trong sources/, đều CC BY-SA, cần ghi nguồn trên web):
  sources/kanjidic2/kanjidic2.xml   https://www.edrdg.org/kanjidic/kanjidic2.xml.gz  (EDRDG, CC BY-SA 4.0)
  sources/kanjivg/kanjivg.xml       https://github.com/KanjiVG/kanjivg/releases      (CC BY-SA 3.0)

    python -I -X utf8 vn/import_kanji.py
    python -I -X utf8 vn/import_kanji.py --db-url ...

Chạy lại bao nhiêu lần cũng được. Hán Việt và nghĩa tiếng Việt đã có (từ pipeline) được giữ nguyên;
chữ nào chưa có Hán Việt thì lấy âm "vietnam" của KANJIDIC2.
"""
import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

import psycopg2
from psycopg2.extras import execute_values

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import config  # noqa: E402  (scripts/jmdict/config.py: DB_URL)

SOURCES = os.path.join(os.path.dirname(HERE), "sources")
KVG_ID = re.compile(r"^kvg:kanji_([0-9a-f]{5})$")  # bỏ các biến thể có hậu tố như -Kaisho


def read_kanjidic(path):
    out = {}
    for _, el in ET.iterparse(path):
        if el.tag != "character":
            continue
        literal = el.findtext("literal")
        misc = el.find("misc")
        rad = el.find("radical/rad_value[@rad_type='classical']")
        reading = el.find("reading_meaning/rmgroup")
        on, kun, vi, meanings = [], [], [], []
        if reading is not None:
            for r in reading.findall("reading"):
                kind, text = r.get("r_type"), (r.text or "").strip()
                if kind == "ja_on":
                    on.append(text)
                elif kind == "ja_kun":
                    kun.append(text)
                elif kind == "vietnam":
                    vi.append(text.lower())
            meanings = [m.text.strip() for m in reading.findall("meaning") if m.get("m_lang") is None and m.text]

        def num(tag):
            v = misc.findtext(tag) if misc is not None else None
            return int(v) if v and v.isdigit() else None

        out[literal] = {
            "on": on, "kun": kun, "vi": vi, "en": meanings,
            "strokes": num("stroke_count"), "grade": num("grade"), "freq": num("freq"),
            "radical": int(rad.text) if rad is not None and rad.text else None,
        }
        el.clear()
    return out


def read_kanjivg(path):
    """Chữ -> danh sách thuộc tính d của các nét theo thứ tự viết."""
    out = {}
    for _, el in ET.iterparse(path):
        if el.tag != "kanji":
            continue
        m = KVG_ID.match(el.get("id", ""))
        if m:
            strokes = []
            for p in el.iter("path"):
                pid = p.get("id", "")
                n = re.search(r"-s(\d+)$", pid)
                if n and p.get("d"):
                    strokes.append((int(n.group(1)), p.get("d")))
            strokes.sort()
            out[chr(int(m.group(1), 16))] = [d for _, d in strokes]
        el.clear()
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--db-url", default=config.DB_URL)
    ap.add_argument("--kanjidic", default=os.path.join(SOURCES, "kanjidic2", "kanjidic2.xml"))
    ap.add_argument("--kanjivg", default=os.path.join(SOURCES, "kanjivg", "kanjivg.xml"))
    args = ap.parse_args()

    kanji = read_kanjidic(args.kanjidic)
    paths = read_kanjivg(args.kanjivg)
    rows = []
    for ch, k in kanji.items():
        rows.append((ch, k["vi"], k["on"], k["kun"], k["en"], k["strokes"], k["grade"], k["freq"], k["radical"],
                     paths.get(ch, [])))

    with psycopg2.connect(args.db_url) as conn, conn.cursor() as cur:
        execute_values(cur, """
            INSERT INTO dict_kanji (kanji, han_viet, on_yomi, kun_yomi, meanings_en, stroke_count, grade, frequency,
                                    radical, stroke_paths)
            VALUES %s
            ON CONFLICT (kanji) DO UPDATE SET
                on_yomi = EXCLUDED.on_yomi, kun_yomi = EXCLUDED.kun_yomi, meanings_en = EXCLUDED.meanings_en,
                stroke_count = EXCLUDED.stroke_count, grade = EXCLUDED.grade, frequency = EXCLUDED.frequency,
                radical = EXCLUDED.radical, stroke_paths = EXCLUDED.stroke_paths,
                han_viet = CASE WHEN cardinality(dict_kanji.han_viet) = 0 THEN EXCLUDED.han_viet
                                ELSE dict_kanji.han_viet END
        """, rows, page_size=1000)
    with_strokes = sum(1 for r in rows if r[9])
    print(f"Đã nạp {len(rows)} chữ kanji từ KANJIDIC2 ({with_strokes} có thứ tự nét từ KanjiVG).")


if __name__ == "__main__":
    main()
