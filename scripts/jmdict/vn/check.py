"""
Kiểm tra câu trả lời Gemini đã lưu ở data/results/<batch>.json.

    python vn/check.py            # kiểm tra mọi file kết quả
    python vn/check.py W001 K01   # chỉ vài batch

Với mỗi batch:
- Sửa nhẹ những lỗi định dạng hay gặp (```json, chữ thừa trước/sau mảng) và ghi đè file đã sạch.
- LỖI (phải làm lại): không đọc được JSON, thiếu mục, sai id, thiếu nghĩa hay ví dụ, Hán Việt không nằm
  trong danh sách cho chọn. Các mục lỗi bị bỏ khỏi file và gom vào work/batches/<batch>_retry.txt để dán lại.
- CẢNH BÁO (vẫn nhận, nên xem qua): câu ví dụ không thấy chứa từ, Hán Việt khác Unihan, nghĩa còn tiếng Anh.
Báo cáo chi tiết ở work/reports/<batch>.txt.
"""
import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from hanviet import HanViet, is_kanji, read_json_array, reading_key  # noqa: E402
from prepare import RESULTS, SOURCES, WORK, read_prompt  # noqa: E402

SYLLABLE = re.compile(r"^[a-zàáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]+$")
# "to", "or" cũng là chữ tiếng Việt (phóng to), nên không đưa vào
ENGLISH_WORD = re.compile(r"\b(the|of|and|with|for|something|someone|somebody)\b", re.I)
PART = re.compile(r"_part\d+$")
FULLWIDTH_DIGITS =str.maketrans("０１２３４５６７８９", "〇一二三四五六七八九")


def load_answer(path):
    """Đọc mảng JSON từ câu trả lời; bỏ ```json và chữ thừa quanh mảng."""
    with open(path, encoding="utf-8-sig") as f:
        text = f.read()
    if not text.strip():
        raise ValueError("file trống, câu trả lời chưa được lưu vào file")
    start, end = text.find("["), text.rfind("]")
    if start < 0 or end < start:
        raise ValueError("không thấy mảng JSON")
    data = json.loads(text[start:end + 1])
    if not isinstance(data, list):
        raise ValueError("không phải mảng")
    return data


def stems(item):
    """Những chuỗi mà câu ví dụ nên chứa: mặt chữ, cách đọc, và phần gốc khi từ được chia thể."""
    out = set()
    for form in filter(None, [item.get("w"), item.get("kanji"), item.get("r")]):
        out.add(form)
        out.add(form.translate(FULLWIDTH_DIGITS))  # １月 -> 一月
        if len(form) >= 2:
            out.add(form[:-1])  # 食べる -> 食べ, 高い -> 高
        lead = re.match(r"^[^\u3040-\u309f]+", form)
        if lead and lead.group(0) != form:
            out.add(lead.group(0))  # phần kanji đầu: 取り扱う -> 取
    return {s for s in out if s}


def check_kanji(batch, answer, hv, problems, warnings):
    by_key = {a.get("k"): a for a in answer if isinstance(a, dict)}
    good = []
    for item in batch:
        a = by_key.get(item["k"])
        if a is None:
            problems.append((item, "thiếu mục"))
            continue
        readings = a.get("hv")
        if not isinstance(readings, list) or not all(isinstance(r, str) and SYLLABLE.match(r.strip().lower()) for r in readings):
            problems.append((item, f"hv không hợp lệ: {readings!r}"))
            continue
        uni = hv.unihan(item["k"])
        if uni and readings and not {reading_key(r) for r in readings} & {reading_key(u) for u in uni}:
            warnings.append(f"{item['k']}: hv {readings} không trùng Unihan {uni}")
        good.append({"k": item["k"], "hv": [r.strip().lower() for r in readings], "nghia": a.get("nghia", "")})
    return good


def check_words(batch, answer, hv, problems, warnings):
    by_id = {a.get("id"): a for a in answer if isinstance(a, dict)}
    good = []
    stats = new_meaning_stats()
    for item in batch:
        a = by_id.get(item["id"])
        if a is None:
            problems.append((item, "thiếu mục"))
            continue
        meanings = a.get("m")
        if not isinstance(meanings, list) or not meanings or not all(isinstance(m, dict) and str(m.get("vi", "")).strip() for m in meanings):
            problems.append((item, "thiếu nghĩa"))
            continue
        examples = a.get("ex")
        if not isinstance(examples, list) or not examples or not all(
                isinstance(e, dict) and str(e.get("ja", "")).strip() and str(e.get("vi", "")).strip() for e in examples):
            problems.append((item, "thiếu câu ví dụ"))
            continue

        reading = None
        if "hv_chon" in item or "hv_tu_do" in item:
            reading = str(a.get("hv", "")).strip().lower()
            syllables = reading.split()
            kanji_count = sum(1 for ch in item.get("kanji") or item["w"] if is_kanji(ch) and ch not in hv.no_reading)
            if "hv_chon" in item:
                choices = item["hv_chon"]
                if len(syllables) != len(choices) or any(s not in c for s, c in zip(syllables, choices)):
                    problems.append((item, f"hv \"{reading}\" không khớp lựa chọn {choices}"))
                    continue
            elif len(syllables) != kanji_count or not all(SYLLABLE.match(s) for s in syllables):
                problems.append((item, f"hv \"{reading}\" không đủ {kanji_count} âm"))
                continue
            else:
                kanji_chars = [ch for ch in item.get("kanji") or item["w"] if is_kanji(ch) and ch != "々" and ch not in hv.no_reading]
                for ch, s in zip(kanji_chars, syllables):
                    uni = hv.unihan(ch)
                    if uni and reading_key(s) not in {reading_key(u) for u in uni}:
                        warnings.append(f"{item['w']}: âm \"{s}\" của {ch} khác Unihan {uni}")

        targets = stems(item)
        for e in examples:
            if not any(t in e["ja"] for t in targets):
                warnings.append(f"{item['w']}: câu ví dụ có vẻ không chứa từ: {e['ja']}")
        for m in meanings:
            if ENGLISH_WORD.search(m["vi"]):
                warnings.append(f"{item['w']}: nghĩa còn tiếng Anh? {m['vi']}")

        add_meaning_stats(stats, item, meanings)

        entry = {"id": item["id"], "m": [{"vi": m["vi"].strip()} for m in meanings[:4]],
                 "ex": [{"ja": e["ja"].strip(), "vi": e["vi"].strip()} for e in examples[:2]]}
        if reading is not None:
            entry["hv"] = reading
        good.append(entry)
    meaning_drift(stats, warnings)
    return good


def new_meaning_stats():
    return {"items": 0, "split": 0, "multi_en": 0, "single_m": 0}


def add_meaning_stats(stats, item, meanings):
    senses = min(len(item.get("en", [])), 4)
    stats["items"] += 1
    stats["split"] += len(meanings) > senses
    if senses >= 2:
        stats["multi_en"] += 1
        stats["single_m"] += len(meanings) == 1


def meaning_drift(stats, warnings):
    """Gemini hay 'trôi' khi chat dài: tách từ đồng nghĩa thành nhiều nghĩa, hoặc dồn mọi nghĩa làm một.
    Batch tốt: gần như không mục nào có nhiều nghĩa Việt hơn nghĩa Anh; batch trôi: 30-45%."""
    if stats["items"] >= 50 and stats["split"] / stats["items"] > 0.1:
        warnings.insert(0, f"NGHĨA: {stats['split']}/{stats['items']} từ có nhiều nghĩa Việt hơn nghĩa Anh, có vẻ đang "
                           "tách từ đồng nghĩa thành nhiều nghĩa. Nên mở chat mới và làm lại batch này.")
    if stats["multi_en"] >= 30 and stats["single_m"] / stats["multi_en"] > 0.6:
        warnings.insert(0, f"NGHĨA: {stats['single_m']}/{stats['multi_en']} từ nhiều nghĩa chỉ được ghi 1 nghĩa, "
                           "có vẻ đang dồn nghĩa. Nên mở chat mới và làm lại batch này.")


def check(name, hv):
    # W035_part1.json: một phần của batch W035 (khi dán/sinh làm nhiều lần)
    base = PART.sub("", name)
    batch_path = os.path.join(WORK, "batches", f"{base}.json")
    result_path = os.path.join(RESULTS, f"{name}.json")
    if not os.path.exists(batch_path):
        return f"{name}: không thấy work/batches/{base}.json (chạy lại prepare.py?)"
    with open(batch_path, encoding="utf-8") as f:
        batch = json.load(f)
    problems, warnings = [], []
    try:
        answer = load_answer(result_path)
    except (ValueError, json.JSONDecodeError) as err:
        return f"{name}: LỖI đọc JSON ({err}). Dán lại cả batch."
    part_note = ""
    if base != name:
        key = "k" if name.startswith("K") else "id"
        present = {a.get(key) for a in answer if isinstance(a, dict)}
        covered = set()
        for p in glob.glob(os.path.join(RESULTS, f"{base}.json")) + glob.glob(os.path.join(RESULTS, f"{base}_part*.json")):
            covered |= {a.get(key) for a in read_json_array(p) if isinstance(a, dict)}
        covered |= present
        batch_left = sum(1 for it in batch if it[key] not in covered)
        batch = [it for it in batch if it[key] in present]
        part_note = f" (cả {base} còn {batch_left} mục chưa có trong file nào)" if batch_left else f" ({base} đã đủ các phần)"

    is_kanji_batch = name.startswith("K")
    good = (check_kanji if is_kanji_batch else check_words)(batch, answer, hv, problems, warnings)

    with open(result_path, "w", encoding="utf-8") as f:
        json.dump(good, f, ensure_ascii=False, indent=1)

    os.makedirs(os.path.join(WORK, "reports"), exist_ok=True)
    with open(os.path.join(WORK, "reports", f"{name}.txt"), "w", encoding="utf-8") as f:
        f.write(f"{name}: {len(good)}/{len(batch)} mục đạt\n\nLỖI:\n")
        f.writelines(f"- {it.get('w') or it.get('k')}: {why}\n" for it, why in problems)
        f.write("\nCẢNH BÁO:\n")
        f.writelines(f"- {w}\n" for w in warnings)

    retry_name = f"{name}_retry"
    retry_txt = os.path.join(WORK, "batches", f"{retry_name}.txt")
    if problems:
        retry_items = [it for it, _ in problems]
        prompt = read_prompt("PROMPT_KANJI.md" if is_kanji_batch else "PROMPT_WORDS.md")
        with open(retry_txt, "w", encoding="utf-8") as f:
            f.write(f"{prompt}\n\nBATCH {retry_name} ({len(retry_items)} mục):\n"
                    f"{json.dumps(retry_items, ensure_ascii=False, separators=(',', ':'))}\n")
        with open(os.path.join(WORK, "batches", f"{retry_name}.json"), "w", encoding="utf-8") as f:
            json.dump(retry_items, f, ensure_ascii=False)
    elif os.path.exists(retry_txt):
        os.remove(retry_txt)

    status = "OK" if not problems else f"{len(problems)} LỖI -> dán work/batches/{retry_name}.txt"
    drift = "".join(f"\n   ! {w}" for w in warnings if w.startswith("NGHĨA:"))
    return f"{name}: {len(good)}/{len(batch)} đạt, {len(warnings)} cảnh báo, {status}{part_note}{drift}"


def main():
    # Không chỉ định batch thì bỏ qua các file còn trống (tạo sẵn để dán dần)
    names = sys.argv[1:] or sorted(os.path.splitext(os.path.basename(p))[0]
                                   for p in glob.glob(os.path.join(RESULTS, "*.json")) if os.path.getsize(p) > 0)
    if not names:
        print(f"Chưa có kết quả nào trong {RESULTS}")
        return
    # Kanji trước để bảng Hán Việt mới nhất được dùng khi kiểm tra batch từ
    names.sort(key=lambda n: (not n.startswith("K"), n))
    hv = HanViet(SOURCES, RESULTS)
    for name in names:
        if name.startswith("W") and hv.table == {}:
            hv = HanViet(SOURCES, RESULTS)
        print(check(name, hv))


if __name__ == "__main__":
    main()
