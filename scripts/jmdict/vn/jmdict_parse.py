"""
Đọc JMdict_e thành danh sách entry gọn cho pipeline nghĩa tiếng Việt.

Dùng regex thay vì ElementTree vì ElementTree tự thay entity (&n;, &v5r;, &uk;...) bằng câu mô tả dài;
pipeline cần chính mã ngắn đó để gắn nhãn từ loại và nhận ra từ thường viết bằng kana (&uk;).

License: JMdict/EDICT Project (CC BY-SA 4.0), cần ghi nguồn ở trang web.
"""
import re

ENTRY_RE = re.compile(r"<entry>(.*?)</entry>", re.S)
K_ELE_RE = re.compile(r"<k_ele>(.*?)</k_ele>", re.S)
R_ELE_RE = re.compile(r"<r_ele>(.*?)</r_ele>", re.S)
SENSE_RE = re.compile(r"<sense>(.*?)</sense>", re.S)
NF_RE = re.compile(r"^nf(\d{2})$")

PRIORITY_TIER_1 = {"news1", "ichi1", "spec1", "spec2", "gai1"}
PRIORITY_TIER_2 = {"news2", "ichi2", "gai2"}
# Cách viết hiếm, sai chính tả hoặc chỉ để tìm kiếm: không chọn làm mặt chữ chính
BAD_FORM_TAGS = {"rK", "iK", "oK", "sK", "io", "ateji"}
BAD_READING_TAGS = {"ok", "ik", "sk", "rk"}


def _all(tag, text):
    return re.findall(rf"<{tag}>(.*?)</{tag}>", text, re.S)


def _codes(tag, text):
    """Mã entity trong một thẻ, vd <pos>&v5r;</pos> -> ['v5r']."""
    return [m for v in _all(tag, text) for m in re.findall(r"&([\w-]+);", v)]


def rank_of(tags):
    """Giống scripts/jmdict/backfill_frequency.py: số càng nhỏ càng phổ biến, None = JMdict không đánh dấu."""
    nf = [int(m.group(1)) for t in tags if (m := NF_RE.match(t))]
    if nf:
        return min(nf) * 500
    if any(t in PRIORITY_TIER_1 for t in tags):
        return 30000
    if any(t in PRIORITY_TIER_2 for t in tags):
        return 40000
    return None


def parse_entry(raw):
    seq = int(_all("ent_seq", raw)[0])
    kanji = []
    for k in K_ELE_RE.findall(raw):
        kanji.append({"text": _all("keb", k)[0], "pri": _all("ke_pri", k), "inf": _codes("ke_inf", k)})
    readings = []
    for r in R_ELE_RE.findall(raw):
        readings.append({
            "text": _all("reb", r)[0],
            "pri": _all("re_pri", r),
            "inf": _codes("re_inf", r),
            "restr": _all("re_restr", r),
            "nokanji": "<re_nokanji/>" in r,
        })
    senses = []
    last_pos = []
    for s in SENSE_RE.findall(raw):
        glosses = [re.sub(r"\s+", " ", g).strip() for g in re.findall(r"<gloss[^>]*>(.*?)</gloss>", s, re.S)]
        pos = _codes("pos", s) or last_pos  # JMdict bỏ <pos> khi giống sense trước
        last_pos = pos
        senses.append({"pos": pos, "misc": _codes("misc", s), "gloss": glosses})

    tags = [t for k in kanji for t in k["pri"]] + [t for r in readings for t in r["pri"]]
    good_kanji = [k for k in kanji if not set(k["inf"]) & BAD_FORM_TAGS] or kanji
    head = max(good_kanji, key=lambda k: bool(k["pri"])) if good_kanji else None
    good_readings = [r for r in readings if not set(r["inf"]) & BAD_READING_TAGS] or readings
    if head:
        fitting = [r for r in good_readings if not r["nokanji"] and (not r["restr"] or head["text"] in r["restr"])]
        reading = (fitting or good_readings)[0]
    else:
        reading = good_readings[0]
    usually_kana = bool(senses) and "uk" in senses[0]["misc"]

    return {
        "id": seq,
        "word": head["text"] if head else None,
        "reading": reading["text"],
        "other_forms": [k["text"] for k in good_kanji if not head or k["text"] != head["text"]],
        "other_readings": [r["text"] for r in good_readings if r["text"] != reading["text"]],
        "usually_kana": usually_kana,
        "rank": rank_of(tags),
        "tags": sorted(set(tags)),
        "senses": senses,
    }


def iter_entries(path):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    for m in ENTRY_RE.finditer(text):
        yield parse_entry(m.group(1))
