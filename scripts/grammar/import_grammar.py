"""
Nạp ngữ pháp đã kiểm tra (data/results/GN*.json) vào bảng grammar_points và grammar_examples.

    python -I -X utf8 import_grammar.py                 # nạp thẳng vào DB local (MAZII_DB_URL hoặc DB dev mặc định)
    python -I -X utf8 import_grammar.py --sql           # chỉ ghi work/grammar_feed.sql để chạy trên VPS
    python -I -X utf8 import_grammar.py --sql --out ...

Chạy lại bao nhiêu lần cũng được: mỗi mẫu được ghi đè theo (cấp độ, tên mẫu), ví dụ của mẫu đó được thay mới.
Trên VPS:  dc exec -T postgres psql -U nihon -d nihon_mukae -v ON_ERROR_STOP=1 < grammar_feed.sql
(Bảng phải có sẵn: migration V27 do backend tạo khi khởi động.)
"""
import argparse
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "jmdict"))
from gram import RESULTS, WORK, batches, load_list, read_json_array  # noqa: E402

CHUNK = 300


def lit(v):
    """Giá trị SQL: chuỗi (nhân đôi dấu nháy đơn), số hoặc NULL."""
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def insert_rows(out, table, columns, rows):
    for i in range(0, len(rows), CHUNK):
        chunk = rows[i:i + CHUNK]
        out.append(f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n")
        out.append(",\n".join("(" + ", ".join(lit(v) for v in r) + ")" for r in chunk))
        out.append(";\n")


def load_entries():
    """Các mẫu đạt, kèm thứ tự (sort_order) theo danh sách gốc."""
    order = {}
    for level, items in load_list().items():
        for n, item in enumerate(items):
            order[item["id"]] = n
    entries = []
    for path in sorted(glob.glob(os.path.join(RESULTS, "GN*.json"))):
        for e in read_json_array(path):
            if isinstance(e, dict) and e.get("id") in order and e.get("examples"):
                entries.append(e)
    entries.sort(key=lambda e: (-e["level"], order[e["id"]]))
    return entries, order


def build_sql(entries, order):
    point_rows, example_rows = [], []
    for e in entries:
        point_rows.append((
            e["level"], e["pattern"], e.get("reading") or None,
            json.dumps(e["connection"], ensure_ascii=False), e["meaning"], e["explanation"],
            e.get("notes") or None, json.dumps(e.get("similar", []), ensure_ascii=False), order[e["id"]],
        ))
        example_rows += [(e["level"], e["pattern"], i, x["ja"], x["vi"]) for i, x in enumerate(e["examples"])]

    out = []
    out.append("CREATE TEMP TABLE feed_points (level smallint, pattern text, reading text, connection text, meaning text, "
               "explanation text, notes text, related text, sort_order int) ON COMMIT DROP;\n")
    out.append("CREATE TEMP TABLE feed_examples (level smallint, pattern text, idx smallint, ja text, vi text) ON COMMIT DROP;\n")
    insert_rows(out, "feed_points", ["level", "pattern", "reading", "connection", "meaning", "explanation", "notes",
                                     "related", "sort_order"], point_rows)
    insert_rows(out, "feed_examples", ["level", "pattern", "idx", "ja", "vi"], example_rows)
    out.append("""
INSERT INTO grammar_points (level, pattern, reading, connection, meaning, explanation, notes, related, sort_order)
    SELECT level, pattern, reading, connection::jsonb, meaning, explanation, notes, related::jsonb, sort_order FROM feed_points
    ON CONFLICT (level, pattern) DO UPDATE SET reading = EXCLUDED.reading, connection = EXCLUDED.connection,
        meaning = EXCLUDED.meaning, explanation = EXCLUDED.explanation, notes = EXCLUDED.notes,
        related = EXCLUDED.related, sort_order = EXCLUDED.sort_order, updated_at = now();
DELETE FROM grammar_examples WHERE point_id IN
    (SELECT p.id FROM grammar_points p JOIN feed_points f ON f.level = p.level AND f.pattern = p.pattern);
INSERT INTO grammar_examples (point_id, idx, ja, vi)
    SELECT p.id, x.idx, x.ja, x.vi FROM feed_examples x JOIN grammar_points p ON p.level = x.level AND p.pattern = x.pattern;
""")
    return "".join(out), len(point_rows), len(example_rows)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sql", action="store_true", help="chỉ ghi file SQL, không nạp vào DB")
    ap.add_argument("--out", default=os.path.join(WORK, "grammar_feed.sql"))
    ap.add_argument("--db-url", default=None)
    args = ap.parse_args()

    entries, order = load_entries()
    if not entries:
        sys.exit("Chưa có mẫu nào đạt trong data/results/. Chạy check.py trước.")
    body, n_points, n_examples = build_sql(entries, order)

    if args.sql:
        os.makedirs(os.path.dirname(args.out), exist_ok=True)
        with open(args.out, "w", encoding="utf-8", newline="\n") as f:
            f.write("-- Sinh bởi scripts/grammar/import_grammar.py. Chạy lại được (ghi đè theo cấp độ và tên mẫu).\nBEGIN;\n")
            f.write(body)
            f.write("COMMIT;\n")
        print(f"{n_points} mẫu, {n_examples} ví dụ -> {args.out} ({os.path.getsize(args.out) / 1024:.0f} KB)")
        return

    import psycopg2
    import config
    with psycopg2.connect(args.db_url or config.DB_URL) as conn, conn.cursor() as cur:
        cur.execute(body)
    print(f"Đã nạp {n_points} mẫu, {n_examples} ví dụ vào DB.")


if __name__ == "__main__":
    main()
