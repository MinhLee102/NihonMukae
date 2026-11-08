"""
Âm Hán Việt cho từng chữ kanji.

Nguồn chính là bảng do Gemini lập từ các batch K01, K02... (data/results/K*.json), vì chưa có bộ dữ liệu
Hán Việt nào vừa đủ chữ vừa sạch giấy phép: trường kVietnamese của Unihan thiếu nhiều chữ thông dụng
(食, 電, 濟) và lẫn âm Nôm (行 chỉ có "hàng"). Unihan vẫn dùng để đối chiếu: chữ nào Unihan có âm mà bảng
Gemini không trùng âm nào thì check.py báo để xem lại.
"""
import glob
import json
import os
import unicodedata

VARIANT_FIELDS = ("kJapaneseOldVariant", "kTraditionalVariant", "kSemanticVariant", "kZVariant")


def is_kanji(ch):
    return ch == "々" or "CJK UNIFIED IDEOGRAPH" in unicodedata.name(ch, "")


def read_json_array(path):
    """Mảng JSON trong file kết quả, bỏ ```json và chữ thừa quanh mảng. File trống hay hỏng trả về [] để
    check.py báo lỗi cho đúng batch đó thay vì dừng cả chương trình."""
    try:
        with open(path, encoding="utf-8-sig") as f:
            text = f.read()
        start, end = text.find("["), text.rfind("]")
        data = json.loads(text[start:end + 1]) if 0 <= start < end else []
        return data if isinstance(data, list) else []
    except (OSError, ValueError):
        return []


TONE_MARKS = {"̀", "́", "̃", "̉", "̣"}  # huyền, sắc, ngã, hỏi, nặng


def reading_key(syllable):
    """Bỏ khác biệt chính tả để so âm: vị trí dấu thanh (thủy/thuỷ, hóa/hoá) và i/y cuối (lý/lí)."""
    decomposed = unicodedata.normalize("NFD", syllable.strip().lower())
    tones = "".join(c for c in decomposed if c in TONE_MARKS)
    base = unicodedata.normalize("NFC", "".join(c for c in decomposed if c not in TONE_MARKS))
    if base.endswith("y") and not base.endswith(("uy", "ay", "ây")):
        base = base[:-1] + "i"
    return base + tones


def _parse_unihan(path, fields):
    out = {}
    if not os.path.exists(path):
        return out
    with open(path, encoding="utf-8") as f:
        for line in f:
            if not line.startswith("U+"):
                continue
            cp, field, value = line.rstrip("\n").split("\t", 2)
            if field in fields:
                ch = chr(int(cp[2:], 16))
                out.setdefault(ch, []).extend(value.split())
    return out


def _to_char(token):
    # Giá trị biến thể dạng "U+7D93" hoặc "U+98E0<kMatthews"
    return chr(int(token.split("<")[0][2:], 16))


class HanViet:
    def __init__(self, sources_dir, results_dir=None):
        raw_vi = _parse_unihan(os.path.join(sources_dir, "Unihan_Readings.txt"), {"kVietnamese"})
        self._unihan = {k: [v.lower() for v in vs] for k, vs in raw_vi.items()}
        self._variants = {
            k: [_to_char(t) for t in vs]
            for k, vs in _parse_unihan(os.path.join(sources_dir, "Unihan_Variants.txt"), set(VARIANT_FIELDS)).items()
        }
        self.table = {}
        if results_dir:
            for path in sorted(glob.glob(os.path.join(results_dir, "K*.json"))):
                for item in read_json_array(path):
                    if not isinstance(item, dict) or "k" not in item:
                        continue
                    readings = [r.strip().lower() for r in item.get("hv", []) if r.strip()]
                    if readings:
                        self.table[item["k"]] = readings

    def unihan(self, ch):
        """Âm theo Unihan của chữ hoặc các biến thể (経 -> 經 -> kinh). Danh sách rỗng nếu không có."""
        seen = []
        for c in [ch] + self._variants.get(ch, []):
            for r in self._unihan.get(c, []):
                if r not in seen:
                    seen.append(r)
        return seen

    def word_choices(self, word):
        """Các âm có thể cho từng chữ kanji của từ; None nếu có chữ chưa có trong bảng."""
        choices, prev = [], None
        for ch in word:
            if ch == "々" and prev is not None:
                choices.append(choices[-1])
                continue
            if not is_kanji(ch):
                continue
            readings = self.table.get(ch)
            if not readings:
                return None
            choices.append(readings)
            prev = ch
        return choices
