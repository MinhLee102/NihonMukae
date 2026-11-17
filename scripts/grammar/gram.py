"""Phần dùng chung của pipeline ngữ pháp: đường dẫn, đọc danh sách mẫu, chia batch."""
import glob
import json
import os
import re
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
LIST_DIR = os.path.join(DATA, "list")        # kết quả lượt 1: danh sách tên mẫu theo cấp (N5.json, N4.json...)
RESULTS = os.path.join(DATA, "results")      # kết quả lượt 2: nội dung từng batch (GN1-01.json...)
WORK = os.path.join(HERE, "work")            # prompt sinh ra, báo cáo, file SQL (không đưa lên git)
BATCH_SIZE = 15
LEVELS = (5, 4, 3, 2, 1)


def read_json_array(path):
    """Mảng JSON trong file, bỏ ```json và chữ thừa quanh mảng. File trống hay hỏng trả về [] để
    báo lỗi đúng batch đó thay vì dừng cả chương trình."""
    try:
        with open(path, encoding="utf-8-sig") as f:
            text = f.read()
        start, end = text.find("["), text.rfind("]")
        data = json.loads(text[start:end + 1]) if 0 <= start < end else []
        return data if isinstance(data, list) else []
    except (OSError, ValueError):
        return []


def norm_pattern(p):
    """Khoá so trùng: bỏ khoảng trắng, đưa ~ về 〜, NFKC."""
    s = unicodedata.normalize("NFKC", str(p)).replace("~", "〜").replace("～", "〜")
    return re.sub(r"\s+", "", s)


def load_list():
    """Danh sách mẫu theo cấp, kèm id ổn định: {level: [{"id","level","pattern","reading"}]}.
    Id theo thứ tự trong các file data/list/N<cấp>*.json (đọc theo tên file). Chỉ nên THÊM file hoặc dòng
    vào cuối, không chèn giữa, thì id các mẫu cũ không đổi."""
    out = {}
    for level in LEVELS:
        seen, items = set(), []
        for path in sorted(glob.glob(os.path.join(LIST_DIR, f"N{level}*.json"))):
            for raw in read_json_array(path):
                if not isinstance(raw, dict) or not str(raw.get("pattern", "")).strip():
                    continue
                key = norm_pattern(raw["pattern"])
                if key in seen:
                    continue
                seen.add(key)
                items.append({
                    "id": f"N{level}-{len(items) + 1:03d}",
                    "level": level,
                    "pattern": str(raw["pattern"]).strip(),
                    "reading": str(raw.get("reading", "")).strip(),
                    # nhóm ý nghĩa và gợi ý cách nối (tuỳ chọn, từ lượt trích tên mẫu từ file tham khảo)
                    "category": str(raw.get("category", "") or "").strip(),
                    "hint": str(raw.get("hint", "") or "").strip(),
                })
        if items:
            out[level] = items
    return out


INVALID_FILE = os.path.join(DATA, "invalid.json")


def load_invalid():
    """Id các mục AI đánh dấu không chắc/không có thật: không đưa vào batch nữa."""
    return {str(x) for x in read_json_array(INVALID_FILE)}


def add_invalid(ids):
    os.makedirs(DATA, exist_ok=True)
    merged = sorted(load_invalid() | {str(i) for i in ids})
    with open(INVALID_FILE, "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=1)


def batches(listing):
    """[(tên batch, [mục...])], mỗi cấp chia theo BATCH_SIZE trên danh sách gốc (kể cả mục invalid, để tên
    batch ổn định): GN5-01, GN5-02... Mục invalid bị bỏ khỏi batch."""
    invalid = load_invalid()
    result = []
    for level, items in listing.items():
        for i in range(0, len(items), BATCH_SIZE):
            chunk = [x for x in items[i:i + BATCH_SIZE] if x["id"] not in invalid]
            if chunk:
                result.append((f"GN{level}-{i // BATCH_SIZE + 1:02d}", chunk))
    return result


def read_prompt(name):
    with open(os.path.join(HERE, name), encoding="utf-8") as f:
        return f.read()
