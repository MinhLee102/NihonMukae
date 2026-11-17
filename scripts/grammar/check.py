"""
Kiểm tra câu trả lời của AI cho các batch ngữ pháp (data/results/GN*-*.json).

    python -I -X utf8 check.py              # mọi file kết quả
    python -I -X utf8 check.py GN1-01       # chỉ vài batch

Với mỗi batch:
- Sửa nhẹ lỗi định dạng hay gặp (```json, chữ thừa quanh mảng) và ghi đè file đã sạch.
- LỖI (mục bị bỏ khỏi file, chạy prepare.py batches để làm lại): thiếu hoặc sai id, thiếu trường, ít hơn 2 ví dụ,
  nghĩa hay giải thích không phải tiếng Việt, câu ví dụ rỗng.
- Mục "invalid": true được ghi nhận và bỏ khỏi kết quả (mẫu AI không chắc), không tính là lỗi.
- CẢNH BÁO (vẫn nhận, nên xem qua): câu ví dụ không thấy chứa mẫu, ví dụ ít hơn 3, có furigana trong ngoặc.
Báo cáo chi tiết ở work/reports/<batch>.txt.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gram import RESULTS, WORK, add_invalid, batches, load_list, read_json_array  # noqa: E402

VI_LETTER = re.compile(r"[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", re.I)
VI_WORD = re.compile(r"\b(là|và|của|không|được|một|những|khi|để|cho|với|có|người|này|trong|thì|nên|phải|rất)\b", re.I)
JA_CHAR = re.compile(r"[぀-ヿ一-鿿]")
FURIGANA = re.compile(r"[一-鿿]+[（(][぀-ゟ]+[）)]")
PLACEHOLDER = re.compile(r"[A-Za-zＡ-Ｚ0-9〜～~…\s/／・(),（）、。]+")
LIMITS = {"meaning": 250, "explanation": 1200, "notes": 800}


def is_vietnamese(text):
    return bool(VI_LETTER.search(text) or VI_WORD.search(text))


def core_chunks(pattern):
    """Các đoạn chữ Nhật của mẫu (bỏ 〜, chữ cái ký hiệu như V, N). Dùng để kiểm tra câu ví dụ có chứa mẫu."""
    return [c for c in PLACEHOLDER.split(pattern) if len(c) >= 2]


def validate(entry, expected):
    """(entry đã làm sạch | None, [lỗi], [cảnh báo])."""
    errors, warnings = [], []
    if not isinstance(entry, dict):
        return None, ["không phải đối tượng JSON"], []
    if entry.get("invalid") is True:
        return None, [], []
    for key in ("meaning", "explanation"):
        v = entry.get(key)
        if not isinstance(v, str) or not v.strip():
            errors.append(f"thiếu {key}")
        elif not is_vietnamese(v):
            errors.append(f"{key} không phải tiếng Việt")
        elif len(v) > LIMITS[key]:
            errors.append(f"{key} quá dài ({len(v)} ký tự)")
    notes = entry.get("notes", "")
    if notes is None:
        notes = ""
    if not isinstance(notes, str) or len(notes) > LIMITS["notes"]:
        errors.append("notes không hợp lệ hoặc quá dài")

    conn = entry.get("connection")
    if not isinstance(conn, list) or not conn or not all(isinstance(c, str) and c.strip() for c in conn):
        errors.append("connection phải là mảng chuỗi, không rỗng")

    similar = entry.get("similar", [])
    if similar is None:
        similar = []
    if not isinstance(similar, list) or not all(
        isinstance(s, dict) and str(s.get("pattern", "")).strip() and str(s.get("diff", "")).strip() for s in similar
    ):
        errors.append("similar sai định dạng (cần mảng {pattern, diff})")
        similar = []

    examples = entry.get("examples")
    clean_examples = []
    if not isinstance(examples, list):
        errors.append("thiếu examples")
    else:
        for i, ex in enumerate(examples, 1):
            ja = ex.get("ja", "").strip() if isinstance(ex, dict) and isinstance(ex.get("ja"), str) else ""
            vi = ex.get("vi", "").strip() if isinstance(ex, dict) and isinstance(ex.get("vi"), str) else ""
            if not ja or not JA_CHAR.search(ja) or not vi:
                errors.append(f"ví dụ {i} thiếu câu Nhật hoặc bản dịch")
                continue
            if not is_vietnamese(vi):
                errors.append(f"ví dụ {i}: bản dịch không phải tiếng Việt")
                continue
            if FURIGANA.search(ja):
                warnings.append(f"ví dụ {i} có furigana trong ngoặc")
            clean_examples.append({"ja": ja, "vi": vi})
        if len(clean_examples) < 2:
            errors.append(f"chỉ có {len(clean_examples)} ví dụ hợp lệ (cần ít nhất 2)")
        elif len(clean_examples) < 3:
            warnings.append(f"chỉ có {len(clean_examples)} ví dụ")
        chunks = core_chunks(expected["pattern"])
        if chunks and clean_examples:
            probe = max(chunks, key=len)[:2]
            missing = [i for i, ex in enumerate(clean_examples, 1) if probe not in ex["ja"]]
            if missing:
                warnings.append(f"ví dụ {missing} không thấy chứa \"{probe}…\" của mẫu")

    if errors:
        return None, errors, warnings
    return {
        "id": expected["id"],
        "level": expected["level"],
        "pattern": expected["pattern"],
        "reading": str(entry.get("reading") or expected["reading"]).strip(),
        "connection": [c.strip() for c in conn],
        "meaning": entry["meaning"].strip(),
        "explanation": entry["explanation"].strip(),
        "notes": notes.strip(),
        "similar": [{"pattern": s["pattern"].strip(), "diff": s["diff"].strip()} for s in similar][:2],
        "examples": clean_examples[:3],
    }, [], warnings


def check_batch(name, items):
    path = os.path.join(RESULTS, f"{name}.json")
    if not os.path.exists(path):
        return f"{name}: chưa có file kết quả", None
    raw = read_json_array(path)
    if not raw:
        return f"{name}: LỖI đọc JSON (file trống hay hỏng). Dán lại cả batch.", None
    by_id = {}
    for e in raw:
        if isinstance(e, dict) and "id" in e:
            by_id.setdefault(str(e["id"]), e)
    expected = {i["id"]: i for i in items}
    kept, report, invalid, bad, invalid_ids = [], [], 0, 0, []
    for item in items:
        entry = by_id.get(item["id"])
        if entry is None:
            report.append(f"LỖI {item['id']} {item['pattern']}: thiếu trong câu trả lời")
            bad += 1
            continue
        clean, errors, warnings = validate(entry, item)
        if isinstance(entry, dict) and entry.get("invalid") is True:
            report.append(f"BỎ {item['id']} {item['pattern']}: AI đánh dấu không chắc/không có thật")
            invalid += 1
            invalid_ids.append(item["id"])
            continue
        for w in warnings:
            report.append(f"CẢNH BÁO {item['id']} {item['pattern']}: {w}")
        if errors:
            report.append(f"LỖI {item['id']} {item['pattern']}: " + "; ".join(errors))
            bad += 1
            continue
        kept.append(clean)
    extra = sorted(set(by_id) - set(expected))
    if extra:
        report.append("Id không thuộc batch này (bỏ qua): " + ", ".join(extra))
    if invalid_ids:
        add_invalid(invalid_ids)
    # Ghi lại file đã sạch, giữ thứ tự batch
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(kept, f, ensure_ascii=False, indent=1)
    os.makedirs(os.path.join(WORK, "reports"), exist_ok=True)
    with open(os.path.join(WORK, "reports", f"{name}.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(report) + ("\n" if report else ""))
    warn = sum(1 for r in report if r.startswith("CẢNH BÁO"))
    status = "OK" if bad == 0 else f"{bad} mục lỗi, chạy lại prepare.py batches để làm lại"
    skipped = f", {invalid} bị AI bỏ" if invalid else ""
    return f"{name}: {len(kept)}/{len(items)} đạt{skipped}, {warn} cảnh báo, {status}", kept


def main():
    listing = load_list()
    if not listing:
        sys.exit("Chưa có danh sách mẫu trong data/list/.")
    wanted = set(sys.argv[1:])
    for name, items in batches(listing):
        if wanted and name not in wanted:
            continue
        message, _ = check_batch(name, items)
        if not message.endswith("chưa có file kết quả") or wanted:
            print(message)


if __name__ == "__main__":
    main()
