"""
Chọn câu ví dụ từ Tatoeba (Tanaka Corpus) cho các từ JMdict.

Nguồn (CC BY 2.0 FR, tải từ https://downloads.tatoeba.org/exports/ vào sources/tatoeba/):
  jpn_sentences.tsv   id, "jpn", câu
  jpn_indices.csv     id câu Nhật, id câu Anh, dòng B (danh sách từ trong câu, đã gắn với JMdict)
  jpn-eng_links.tsv / eng_sentences.tsv, jpn-vie_links.tsv / vie_sentences.tsv   bản dịch có sẵn

Dòng B, mỗi từ có dạng:  từ(cách đọc)[số nghĩa]{dạng trong câu}~
  ~ = câu đã được kiểm tra là ví dụ tốt cho từ đó.

    python vn/tatoeba.py            # đo độ phủ cho các batch W061 trở đi, ghi data/tatoeba_chon.json
    python vn/tatoeba.py --force    # chọn lại câu (chỉ khi chưa bắt đầu dịch batch C)
"""
import csv
import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from jmdict_parse import iter_entries  # noqa: E402
from prepare import ROOT, SOURCES, WORK  # noqa: E402

TATOEBA = os.path.join(SOURCES, "tatoeba")
TOKEN = re.compile(r"^(?P<head>[^(\[{~|]+)(?:\((?P<read>[^)]+)\))?(?:\[(?P<sense>\d+)\])?(?:\{(?P<form>[^}]+)\})?(?P<good>~)?$")
MIN_LEN, MAX_LEN = 6, 28
# Tatoeba có cả câu tục tĩu / nhạy cảm: lọc theo bản dịch tiếng Anh (lượt dịch còn cho AI đánh dấu bỏ thêm)
BAD_EN = re.compile(r"\b(fuck\w*|shit\w*|bitch\w*|cunt|dick|cock|pussy|blowjob|porn\w*|sex\w*|rape\w*|whore|slut|"
                    r"masturbat\w*|nigger|faggot|penis|vagina|nude|naked|orgasm|kill (?:my|your|him|her)self|suicide)\b", re.I)


def read_tsv(name, cols=3):
    path = os.path.join(TATOEBA, name)
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            if len(row) >= cols:
                yield row


def load_sentences():
    jpn = {int(r[0]): r[2] for r in read_tsv("jpn_sentences.tsv")}
    eng_links, vie_links = {}, {}
    for a, b in read_tsv("jpn-eng_links.tsv", 2):
        eng_links.setdefault(int(a), int(b))
    for a, b in read_tsv("jpn-vie_links.tsv", 2):
        vie_links.setdefault(int(a), int(b))
    want_eng = set(eng_links.values())
    eng = {int(r[0]): r[2] for r in read_tsv("eng_sentences.tsv") if int(r[0]) in want_eng}
    vie = {int(r[0]): r[2] for r in read_tsv("vie_sentences.tsv")}
    eng_of = {k: eng[v] for k, v in eng_links.items() if v in eng}
    # Bỏ hẳn câu Nhật có bản dịch tiếng Anh nhạy cảm
    jpn = {k: v for k, v in jpn.items() if not BAD_EN.search(eng_of.get(k, ""))}
    return jpn, eng_of, {k: vie[v] for k, v in vie_links.items() if v in vie}


def parse_index():
    """Câu -> danh sách (từ, cách đọc, dạng trong câu, có dấu ~)."""
    out = {}
    with open(os.path.join(TATOEBA, "jpn_indices.csv"), encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            toks = []
            for t in parts[2].split():
                m = TOKEN.match(t)
                if m:
                    toks.append((m["head"], m["read"], m["form"], bool(m["good"])))
            out[int(parts[0])] = toks
    return out


def build_lookup(ids):
    """(dạng chữ, cách đọc|None) -> các id JMdict có dạng đó, chỉ trong tập ids."""
    by_form, forms_of = {}, {}
    for e in iter_entries(os.path.join(ROOT, "JMdict_e")):
        if e["id"] not in ids:
            continue
        kanji = [f for f in [e["word"]] + e["other_forms"] if f]
        readings = [e["reading"]] + e["other_readings"]
        forms_of[e["id"]] = kanji + (readings if not kanji or e["usually_kana"] else [])
        for k in kanji:
            by_form.setdefault((k, None), set()).add(e["id"])
            for r in readings:
                by_form.setdefault((k, r), set()).add(e["id"])
        if not kanji or e["usually_kana"]:
            for r in readings:
                by_form.setdefault((r, None), set()).add(e["id"])
    return by_form, forms_of


def stem_forms(forms):
    """Như stems() của check.py: mặt chữ, bỏ đuôi 1 ký tự (食べる -> 食べ), phần kanji đầu (取り扱う -> 取)."""
    out = set()
    for f in forms:
        out.add(f)
        if len(f) >= 2:
            out.add(f[:-1])
        lead = re.match(r"^[^぀-ゟ]+", f)
        if lead and lead.group(0) != f:
            out.add(lead.group(0))
    return {x for x in out if x}


def really_contains(ja, form, forms):
    """Dòng B đôi khi gắn nhầm (vd 「川口」 gắn cho 河口, 「１２世紀」 cho 二世, 「ぼたぼた」 cho ポタポタ):
    chỉ nhận câu chứa mặt chữ (hoặc gốc khi chia thể) của chính mục đó. Biến thể hợp lệ như 覚せい剤 cũng bị bỏ,
    chấp nhận mất một ít câu cho chắc."""
    return any(t in ja for t in stem_forms(forms))


def add_substring_matches(target_ids, jpn, eng, vie, candidates):
    """Dự phòng cho từ chưa có câu nào trong dòng B: tìm câu chứa nguyên mặt chữ.
    Chỉ dùng cho mặt chữ >= 2 ký tự có chữ Hán (ít nhầm), và mặt chữ đó phải thuộc đúng một mục."""
    forms = {}
    for e in iter_entries(os.path.join(ROOT, "JMdict_e")):
        if e["id"] in target_ids and e["id"] not in candidates:
            for f in [e["word"]] + e["other_forms"]:
                if f and len(f) >= 2 and re.search(r"[一-鿿]", f):
                    forms.setdefault(f, set()).add(e["id"])
    bigrams = {}
    for sid, ja in jpn.items():
        if MIN_LEN <= len(ja) <= MAX_LEN:
            for k in {ja[i:i + 2] for i in range(len(ja) - 1)}:
                bigrams.setdefault(k, []).append(sid)
    for f, ids in forms.items():
        if len(ids) != 1:
            continue
        wid = next(iter(ids))
        for sid in bigrams.get(f[:2], []):
            ja = jpn[sid]
            if f in ja:
                # Sau các mục từ dòng B: điểm 2 ở vị trí đầu
                candidates.setdefault(wid, []).append(((2, 0 if sid in vie else 1, 0 if sid in eng else 1, abs(len(ja) - 16)), sid))


def choose(target_ids, max_per_word=2):
    """id JMdict -> tối đa max_per_word câu [{sid, ja, en, vi}], mỗi câu chỉ dùng cho một từ."""
    jpn, eng, vie = load_sentences()
    index = parse_index()
    lookup, forms_of = build_lookup(target_ids)
    candidates = {}
    for sid, toks in index.items():
        ja = jpn.get(sid)
        if not ja or not (MIN_LEN <= len(ja) <= MAX_LEN):
            continue
        for head, read, form, good in toks:
            hits = lookup.get((head, read)) or lookup.get((head, None)) or set()
            if len(hits) != 1:
                continue  # không gắn được hoặc mơ hồ giữa nhiều mục
            wid = next(iter(hits))
            if not really_contains(ja, form, forms_of.get(wid, [head])):
                continue
            score = (0 if good else 1, 0 if sid in vie else 1, 0 if sid in eng else 1, abs(len(ja) - 16))
            candidates.setdefault(wid, []).append((score, sid))
    add_substring_matches(target_ids, jpn, eng, vie, candidates)
    used, chosen = set(), {}
    # Từ ít câu chọn trước để không bị từ nhiều câu giành mất
    for wid in sorted(candidates, key=lambda w: len(candidates[w])):
        picks = []
        for _, sid in sorted(candidates[wid]):
            if sid in used:
                continue
            picks.append({"sid": sid, "ja": jpn[sid], "en": eng.get(sid, ""), "vi": vie.get(sid, "")})
            used.add(sid)
            if len(picks) == max_per_word:
                break
        if picks:
            chosen[wid] = picks
    return chosen, candidates


def target_batches(start=61):
    names = sorted(os.path.splitext(os.path.basename(p))[0] for p in glob.glob(os.path.join(WORK, "batches", "W[0-9][0-9][0-9].json")))
    return [n for n in names if int(n[1:]) >= start]


def main():
    names = target_batches()
    items = {}
    for n in names:
        with open(os.path.join(WORK, "batches", f"{n}.json"), encoding="utf-8") as f:
            for it in json.load(f):
                items[it["id"]] = n
    chosen, candidates = choose(set(items))
    total = len(items)
    two = sum(1 for p in chosen.values() if len(p) >= 2)
    one = sum(1 for p in chosen.values() if len(p) == 1)
    sents = [s for p in chosen.values() for s in p]
    has_vi = sum(1 for s in sents if s["vi"])
    has_en = sum(1 for s in sents if s["en"])
    good = sum(1 for w in chosen if any(c[0][0] == 0 for c in candidates[w]))
    print(f"Batch {names[0]}..{names[-1]}: {total} từ")
    print(f"  có 2 câu: {two} ({two / total:.0%}), có 1 câu: {one} ({one / total:.0%}), không có: {total - two - one} ({(total - two - one) / total:.0%})")
    print(f"  từ có câu được đánh dấu ví dụ tốt (~): {good}")
    print(f"  tổng câu đã chọn: {len(sents)}; có sẵn tiếng Việt: {has_vi}; có tiếng Anh: {has_en}; cần AI dịch: {len(sents) - has_vi}")
    # Độ phủ theo đoạn để thấy từ hiếm ở cuối bị thiếu nhiều hơn
    for lo in range(0, len(names), 30):
        part = set(names[lo:lo + 30])
        ids = [i for i, n in items.items() if n in part]
        cov = sum(1 for i in ids if i in chosen)
        print(f"  {names[lo]}..{names[min(lo + 29, len(names) - 1)]}: {cov}/{len(ids)} từ có câu ({cov / len(ids):.0%})")
    out = os.path.join(ROOT, "data", "tatoeba_chon.json")
    if os.path.exists(out) and "--force" not in sys.argv:
        print("data/tatoeba_chon.json đã có, giữ nguyên (các batch C dựa vào mã câu trong đó). Chạy lại với --force để ghi đè.")
        return
    with open(out, "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in sorted(chosen.items())}, f, ensure_ascii=False, indent=0)
    print("Đã ghi data/tatoeba_chon.json")


if __name__ == "__main__":
    main()
