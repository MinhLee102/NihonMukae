"""
Kiểm tra câu trả lời Gemini đã lưu ở data/results/<batch>.json.

    python vn/check.py            # kiểm tra mọi file kết quả
    python vn/check.py W001 K01   # chỉ vài batch
    python vn/check.py N061 C001  # batch nghĩa (data/nghia/) và batch dịch câu (data/cau/), từ W061

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
from prepare import CAU_DIR, NGHIA_DIR, RESULTS, SOURCES, WORK, read_prompt  # noqa: E402

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
        if examples == [] and a.get("ex_none"):
            pass  # từ W061+ mà mọi câu Tatoeba đều bị loại: chấp nhận không có ví dụ
        elif not isinstance(examples, list) or not examples or not all(
                isinstance(e, dict) and str(e.get("ja", "")).strip() and str(e.get("vi", "")).strip() for e in examples):
            problems.append((item, "thiếu câu ví dụ"))
            continue

        ok, reading = check_hv(item, a, hv, problems, warnings)
        if not ok:
            continue

        targets = stems(item)
        for e in examples:
            if not any(t in e["ja"] for t in targets):
                warnings.append(f"{item['w']}: câu ví dụ có vẻ không chứa từ: {e['ja']}")
        for m in meanings:
            if ENGLISH_WORD.search(m["vi"]):
                warnings.append(f"{item['w']}: nghĩa còn tiếng Anh? {m['vi']}")

        add_meaning_stats(stats, item, meanings)

        entry = {"id": item["id"], "m": [{"vi": m["vi"].strip()} for m in meanings[:4]],
                 "ex": [clean_example(e) for e in examples[:2]]}
        if reading is not None:
            entry["hv"] = reading
        if not examples:
            entry["ex_none"] = True
        good.append(entry)
    meaning_drift(stats, warnings)
    return good


def clean_example(e):
    out = {"ja": e["ja"].strip(), "vi": e["vi"].strip()}
    for k in ("src", "sid"):  # nguồn câu (merge_w.py), giữ lại để ghi nguồn Tatoeba
        if k in e:
            out[k] = e[k]
    return out


def check_hv(item, a, hv, problems, warnings):
    """Kiểm tra "hv" khi batch yêu cầu. Trả về (đạt?, âm Hán Việt hoặc None)."""
    if "hv_chon" not in item and "hv_tu_do" not in item:
        return True, None
    reading = str(a.get("hv", "")).strip().lower()
    syllables = reading.split()
    kanji_count = sum(1 for ch in item.get("kanji") or item["w"] if is_kanji(ch) and ch not in hv.no_reading)
    if "hv_chon" in item:
        choices = item["hv_chon"]
        if len(syllables) != len(choices) or any(s not in c for s, c in zip(syllables, choices)):
            problems.append((item, f"hv \"{reading}\" không khớp lựa chọn {choices}"))
            return False, None
    elif len(syllables) != kanji_count or not all(SYLLABLE.match(s) for s in syllables):
        problems.append((item, f"hv \"{reading}\" không đủ {kanji_count} âm"))
        return False, None
    else:
        kanji_chars = [ch for ch in item.get("kanji") or item["w"] if is_kanji(ch) and ch != "々" and ch not in hv.no_reading]
        for ch, s in zip(kanji_chars, syllables):
            uni = hv.unihan(ch)
            if uni and reading_key(s) not in {reading_key(u) for u in uni}:
                warnings.append(f"{item['w']}: âm \"{s}\" của {ch} khác Unihan {uni}")
    return True, reading


def valid_meanings(a):
    m = a.get("m")
    return isinstance(m, list) and m and all(isinstance(x, dict) and str(x.get("vi", "")).strip() for x in m)


def check_nghia(batch, answer, hv, problems, warnings):
    """Batch N (từ W061): nghĩa + Hán Việt, thêm đúng 1 câu ví dụ cho mục có "vd"."""
    by_id = {a.get("id"): a for a in answer if isinstance(a, dict)}
    good, stats = [], new_meaning_stats()
    for item in batch:
        a = by_id.get(item["id"])
        if a is None:
            problems.append((item, "thiếu mục"))
            continue
        if not valid_meanings(a):
            problems.append((item, "thiếu nghĩa"))
            continue
        ok, reading = check_hv(item, a, hv, problems, warnings)
        if not ok:
            continue
        entry = {"id": item["id"], "m": [{"vi": m["vi"].strip()} for m in a["m"][:4]]}
        if reading is not None:
            entry["hv"] = reading
        if item.get("vd"):
            ex = a.get("ex")
            if not isinstance(ex, list) or not ex or not all(
                    isinstance(e, dict) and str(e.get("ja", "")).strip() and str(e.get("vi", "")).strip() for e in ex):
                problems.append((item, "thiếu câu ví dụ (mục có vd)"))
                continue
            entry["ex"] = [{"ja": ex[0]["ja"].strip(), "vi": ex[0]["vi"].strip()}]
            if not any(t in entry["ex"][0]["ja"] for t in stems(item)):
                warnings.append(f"{item['w']}: câu ví dụ có vẻ không chứa từ: {entry['ex'][0]['ja']}")
            if stutter(entry["ex"][0]["vi"]):
                warnings.append(f"{item['w']}: bản dịch lặp từ: {entry['ex'][0]['vi']}")
        for m in entry["m"]:
            if ENGLISH_WORD.search(m["vi"]):
                warnings.append(f"{item['w']}: nghĩa còn tiếng Anh? {m['vi']}")
        add_meaning_stats(stats, item, entry["m"])
        good.append(entry)
    meaning_drift(stats, warnings)
    return good


JAPANESE = re.compile(r"[぀-ヿ一-鿿]")


def stutter(vi):
    w = vi.lower().split()
    return any(a == b and len(a) > 2 for a, b in zip(w, w[1:]))


def check_cau(batch, answer, hv, problems, warnings):
    """Batch C: bản dịch tiếng Việt cho câu Tatoeba, hoặc {"bo": true} nếu câu không phù hợp."""
    by_sid = {a.get("sid"): a for a in answer if isinstance(a, dict)}
    good, dropped = [], 0
    for item in batch:
        a = by_sid.get(item["sid"])
        if a is None:
            problems.append((item, "thiếu mục"))
            continue
        if a.get("bo") is True:
            good.append({"sid": item["sid"], "bo": True})
            dropped += 1
            continue
        vi = str(a.get("vi", "")).strip()
        if not vi:
            problems.append((item, "thiếu bản dịch"))
            continue
        if JAPANESE.search(vi):
            problems.append((item, f"bản dịch còn chữ Nhật: {vi}"))
            continue
        if stutter(vi):
            warnings.append(f"{item['sid']}: bản dịch lặp từ: {vi}")
        good.append({"sid": item["sid"], "vi": vi})
    if batch and dropped / len(batch) > 0.1:
        warnings.insert(0, f"BỎ: {dropped}/{len(batch)} câu bị đánh dấu bỏ, nhiều bất thường. Xem lại.")
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


# Loại batch theo chữ cái đầu: thư mục kết quả, hàm kiểm tra, prompt cho file làm lại, khoá của mục
KINDS = {
    "K": (RESULTS, "check_kanji", "PROMPT_KANJI.md", "k"),
    "W": (RESULTS, "check_words", "PROMPT_WORDS.md", "id"),
    "N": (NGHIA_DIR, "check_nghia", "PROMPT_NGHIA_VD.md", "id"),
    "C": (CAU_DIR, "check_cau", "PROMPT_CAU.md", "sid"),
}


def check(name, hv):
    # W035_part1.json: một phần của batch W035 (khi dán/sinh làm nhiều lần)
    base = PART.sub("", name)
    folder, fn_name, prompt_file, key = KINDS[name[0].upper()]
    batch_path = os.path.join(WORK, "batches", f"{base}.json")
    result_path = os.path.join(folder, f"{name}.json")
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
        present = {a.get(key) for a in answer if isinstance(a, dict)}
        covered = set()
        for p in glob.glob(os.path.join(folder, f"{base}.json")) + glob.glob(os.path.join(folder, f"{base}_part*.json")):
            covered |= {a.get(key) for a in read_json_array(p) if isinstance(a, dict)}
        covered |= present
        batch_left = sum(1 for it in batch if it[key] not in covered)
        batch = [it for it in batch if it[key] in present]
        part_note = f" (cả {base} còn {batch_left} mục chưa có trong file nào)" if batch_left else f" ({base} đã đủ các phần)"

    good = globals()[fn_name](batch, answer, hv, problems, warnings)

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
        prompt = read_prompt(prompt_file)
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
                                   for d in (RESULTS, NGHIA_DIR, CAU_DIR)
                                   for p in glob.glob(os.path.join(d, "*.json")) if os.path.getsize(p) > 0)
    if not names:
        print(f"Chưa có kết quả nào trong {RESULTS}")
        return
    # Kanji trước để bảng Hán Việt mới nhất được dùng khi kiểm tra batch từ
    names.sort(key=lambda n: (not n.startswith("K"), n))
    hv = HanViet(SOURCES, RESULTS)
    for name in names:
        if name[0] in "WN" and hv.table == {}:
            hv = HanViet(SOURCES, RESULTS)
        print(check(name, hv))


if __name__ == "__main__":
    main()
