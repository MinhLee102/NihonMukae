"""
Xuất phần tiếng Việt (nghĩa, ví dụ, Hán Việt) của các batch W đã làm thành một file SQL nhỏ để cập nhật DB production,
không cần nạp lại cả 218 nghìn mục và không đụng tới đóng góp của người dùng.

    python -I -X utf8 vn/export_sql.py                 # ghi work/feed.sql
    python -I -X utf8 vn/export_sql.py --out ...

File SQL chạy trong một giao dịch và chạy lại được nhiều lần:
  - chỉ cập nhật mục đã có trong dict_entries và chưa bị admin sửa tay (edited);
  - thay toàn bộ nghĩa và ví dụ của các mục đó bằng bản mới.
Trên VPS:  dc exec -T postgres psql -U nihon -d nihon_mukae -v ON_ERROR_STOP=1 < feed.sql
(DB production phải đã nạp toàn bộ từ điển một lần, xem docs/JMDICT_VI_PIPELINE.md.)
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
from hanviet import HanViet  # noqa: E402
from import_dict import compose_han_viet, load_results  # noqa: E402
from prepare import RESULTS, ROOT, SOURCES, WORK, select  # noqa: E402

CHUNK = 500


def lit(v):
    """Giá trị SQL: chuỗi (nhân đôi dấu nháy đơn, standard_conforming_strings), số hoặc NULL."""
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def insert_rows(out, table, columns, rows):
    for i in range(0, len(rows), CHUNK):
        chunk = rows[i:i + CHUNK]
        out.write(f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n")
        out.write(",\n".join("(" + ", ".join(lit(v) for v in r) + ")" for r in chunk))
        out.write(";\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jmdict", default=os.path.join(ROOT, "JMdict_e"))
    ap.add_argument("--out", default=os.path.join(WORK, "feed.sql"))
    args = ap.parse_args()

    hv = HanViet(SOURCES, RESULTS)
    words = load_results("W")
    entries = {e["id"]: e for e in select(args.jmdict)}

    entry_rows, meaning_rows, example_rows = [], [], []
    for wid, ai in sorted(words.items()):
        e = entries.get(wid)
        if not e:
            continue
        entry_rows.append((wid, ai.get("hv") or compose_han_viet(e["word"], hv)))
        meaning_rows += [(wid, i, m["vi"]) for i, m in enumerate(ai["m"])]
        example_rows += [(wid, i, x["ja"], x["vi"], x.get("src", "ai"), x.get("sid")) for i, x in enumerate(ai["ex"])]

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as out:
        out.write("-- Sinh bởi vn/export_sql.py. Chạy lại được; bỏ qua mục không có trong dict_entries hoặc đã bị admin sửa.\n")
        out.write("BEGIN;\n")
        out.write("CREATE TEMP TABLE feed_entries (id bigint, han_viet text) ON COMMIT DROP;\n")
        out.write("CREATE TEMP TABLE feed_meanings (entry_id bigint, idx smallint, meaning text) ON COMMIT DROP;\n")
        out.write("CREATE TEMP TABLE feed_examples (entry_id bigint, idx smallint, ja text, vi text, "
                  "source varchar(16), source_id bigint) ON COMMIT DROP;\n")
        insert_rows(out, "feed_entries", ["id", "han_viet"], entry_rows)
        insert_rows(out, "feed_meanings", ["entry_id", "idx", "meaning"], meaning_rows)
        insert_rows(out, "feed_examples", ["entry_id", "idx", "ja", "vi", "source", "source_id"], example_rows)
        out.write("""
CREATE TEMP TABLE feed_ids ON COMMIT DROP AS
    SELECT f.id FROM feed_entries f JOIN dict_entries e ON e.id = f.id WHERE NOT e.edited;

UPDATE dict_entries e SET han_viet = f.han_viet, updated_at = now()
    FROM feed_entries f JOIN feed_ids i ON i.id = f.id
    WHERE e.id = f.id AND f.han_viet IS NOT NULL;
DELETE FROM dict_meanings WHERE entry_id IN (SELECT id FROM feed_ids);
DELETE FROM dict_examples WHERE entry_id IN (SELECT id FROM feed_ids);
INSERT INTO dict_meanings (entry_id, idx, meaning)
    SELECT m.entry_id, m.idx, m.meaning FROM feed_meanings m JOIN feed_ids i ON i.id = m.entry_id;
INSERT INTO dict_examples (entry_id, idx, ja, vi, source, source_id)
    SELECT x.entry_id, x.idx, x.ja, x.vi, x.source, x.source_id FROM feed_examples x JOIN feed_ids i ON i.id = x.entry_id;
COMMIT;
""")
    size = os.path.getsize(args.out)
    print(f"{len(entry_rows)} từ, {len(meaning_rows)} nghĩa, {len(example_rows)} câu -> {args.out} ({size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
