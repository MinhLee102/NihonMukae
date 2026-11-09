"""
Chọn từ từ JMdict, xếp theo độ ưu tiên và chia thành batch để dán vào Gemini (bản web).

    python vn/prepare.py kanji    # batch Hán Việt cho từng chữ kanji (K01, K02...) - làm trước
    python vn/prepare.py words    # batch nghĩa tiếng Việt + ví dụ cho từ (W001, W002...)

Kết quả nằm ở scripts/jmdict/work/ (không đưa lên git, chạy lại lúc nào cũng được):
  work/batches/K01.txt, W001.txt...  mỗi file = prompt + dữ liệu, dán nguyên vào Gemini
  work/MANIFEST.md                   danh sách batch theo thứ tự ưu tiên và các mốc "đủ nhóm"

Câu trả lời của Gemini lưu vào scripts/jmdict/data/results/<tên batch>.json rồi chạy vn/check.py.

Nguồn: JMdict (CC BY-SA 4.0), danh sách JLPT của Jonathan Waller qua stephenmk/yomitan-jlpt-vocab
(CC BY-SA 4.0), Unihan (Unicode License) để đối chiếu Hán Việt.
"""
import argparse
import csv
import glob
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(__file__))
from jmdict_parse import iter_entries  # noqa: E402
from hanviet import HanViet, is_kanji  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(ROOT, "sources")
WORK = os.path.join(ROOT, "work")
RESULTS = os.path.join(ROOT, "data", "results")
VN = os.path.dirname(os.path.abspath(__file__))

WORD_BATCH_SIZE = 150
KANJI_BATCH_SIZE = 300
MAX_SENSES = 4
MAX_GLOSSES = 4

# Thứ tự ưu tiên: hết nhóm nào là web đã tra được trọn nhóm đó
GROUPS = [
    ("core", "Cốt lõi: JLPT N5–N3 và 3000 từ phổ biến nhất"),
    ("n2", "JLPT N2"),
    ("n1", "JLPT N1"),
    ("common", "Từ phổ biến còn lại (JMdict đánh dấu thông dụng)"),
]

POS_VI = {
    "n": "danh từ", "pn": "đại từ", "vs": "danh từ + する", "adj-i": "tính từ い", "adj-na": "tính từ な",
    "adj-no": "danh từ + の", "adv": "phó từ", "int": "thán từ", "conj": "liên từ", "exp": "cụm từ",
    "prt": "trợ từ", "suf": "hậu tố", "pref": "tiền tố", "ctr": "lượng từ", "num": "số từ",
    "aux-v": "trợ động từ", "adj-pn": "liên thể từ", "adj-t": "tính từ たる", "vt": "tha động từ",
    "vi": "tự động từ",
}


def pos_label(codes):
    labels = []
    for c in codes:
        if c.startswith("v5") or c.startswith("v1") or c in ("vk", "vz", "vs-i", "vs-s"):
            label = "động từ"
        else:
            label = POS_VI.get(c)
        if label and label not in labels:
            labels.append(label)
    return ", ".join(labels)


def load_jlpt():
    """ent_seq -> cấp JLPT (5 = N5). Một từ có mặt ở nhiều danh sách thì lấy cấp dễ hơn."""
    levels = {}
    for n in range(1, 6):
        path = os.path.join(SOURCES, f"jlpt_n{n}.csv")
        with open(path, encoding="utf-8") as f:
            for row in csv.DictReader(f):
                if not row["jmdict_seq"]:
                    continue  # vài mục N1 là chữ đơn lẻ không gắn được với JMdict
                seq = int(row["jmdict_seq"])
                levels[seq] = max(levels.get(seq, 0), n)
    return levels


def group_of(entry, jlpt):
    level = jlpt.get(entry["id"])
    rank = entry["rank"]
    if (level and level >= 3) or (rank is not None and rank <= 3000):
        return "core"
    if level == 2:
        return "n2"
    if level == 1:
        return "n1"
    if rank is not None and rank <= 30000:
        return "common"
    return None


def select(jmdict_path):
    jlpt = load_jlpt()
    chosen = []
    for e in iter_entries(jmdict_path):
        g = group_of(e, jlpt)
        if g:
            e["group"] = g
            e["jlpt"] = jlpt.get(e["id"])
            chosen.append(e)
    order = {g: i for i, (g, _) in enumerate(GROUPS)}
    chosen.sort(key=lambda e: (order[e["group"]], e["rank"] if e["rank"] is not None else 99999, e["id"]))
    return chosen


def headword(e):
    """Mặt chữ hiển thị: từ thường viết bằng kana (&uk;) thì dùng kana."""
    return e["reading"] if (e["usually_kana"] or not e["word"]) else e["word"]


def compact_senses(e):
    out = []
    for s in e["senses"][:MAX_SENSES]:
        out.append("; ".join(s["gloss"][:MAX_GLOSSES]))
    return out


def read_prompt(name):
    with open(os.path.join(VN, name), encoding="utf-8") as f:
        return f.read().strip()


def write_batch(name, prompt, items):
    os.makedirs(os.path.join(WORK, "batches"), exist_ok=True)
    data = json.dumps(items, ensure_ascii=False, separators=(",", ":"))
    with open(os.path.join(WORK, "batches", f"{name}.txt"), "w", encoding="utf-8") as f:
        f.write(f"{prompt}\n\nBATCH {name} ({len(items)} mục):\n{data}\n")
    with open(os.path.join(WORK, "batches", f"{name}.json"), "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False)


def cmd_kanji(args):
    entries = select(args.jmdict)
    count, examples = Counter(), {}
    for e in entries:
        for form in [e["word"] or ""] + e["other_forms"][:1]:
            for ch in form:
                if is_kanji(ch):
                    count[ch] += 1
                    examples.setdefault(ch, [])
                    if len(examples[ch]) < 3 and form not in examples[ch]:
                        examples[ch].append(form)
    hv = HanViet(SOURCES)
    kanji = [k for k, _ in count.most_common()]
    items = [{"k": k, "vd": examples[k], **({"unihan": hv.unihan(k)} if hv.unihan(k) else {})} for k in kanji]
    prompt = read_prompt("PROMPT_KANJI.md")
    names = []
    for i in range(0, len(items), KANJI_BATCH_SIZE):
        name = f"K{i // KANJI_BATCH_SIZE + 1:02d}"
        write_batch(name, prompt, items[i:i + KANJI_BATCH_SIZE])
        names.append(name)
    print(f"{len(kanji)} chữ kanji -> {len(names)} batch: {names[0]}..{names[-1]}")


def cmd_words(args):
    entries = select(args.jmdict)
    hv = HanViet(SOURCES, os.path.join(RESULTS))
    prompt = read_prompt("PROMPT_WORDS.md")
    batches, current, rows = [], [], []
    stats = Counter()

    for e in entries:
        item = {"id": e["id"], "w": headword(e), "r": e["reading"]}
        if e["word"] and e["usually_kana"]:
            item["kanji"] = e["word"]
        pos = pos_label(e["senses"][0]["pos"]) if e["senses"] else ""
        if pos:
            item["pos"] = pos
        item["en"] = compact_senses(e)
        if e["word"]:
            choices = hv.word_choices(e["word"])
            if choices is not None and all(len(c) == 1 for c in choices):
                stats["hv_fixed"] += 1  # mọi chữ chỉ có một âm: tự ghép, không cần hỏi AI
            elif choices is not None:
                item["hv_chon"] = choices
                stats["hv_choose"] += 1
            else:
                item["hv_tu_do"] = True  # có chữ chưa có trong bảng Hán Việt: AI tự điền, check.py đánh dấu
                stats["hv_free"] += 1
        current.append(item)
        rows.append(e["group"])
        if len(current) == WORD_BATCH_SIZE:
            batches.append(current)
            current = []
    if current:
        batches.append(current)

    manifest = ["# Thứ tự batch từ vựng", "",
                f"Mỗi batch {WORD_BATCH_SIZE} từ. Làm theo thứ tự, hết batch ở cột \"Mốc\" là đủ trọn nhóm đó.", "",
                "| Batch | Nhóm | Từ đầu → cuối | Đã có sau batch này | Mốc |", "|---|---|---|---|---|"]
    done = 0
    group_last = {}
    for i, b in enumerate(batches):
        for _ in b:
            group_last[rows[done]] = i
            done += 1
    done = 0
    label = dict(GROUPS)
    for i, b in enumerate(batches):
        name = f"W{i + 1:03d}"
        write_batch(name, prompt, b)
        groups = sorted({rows[done + j] for j in range(len(b))}, key=[g for g, _ in GROUPS].index)
        done += len(b)
        milestone = "; ".join(f"đủ {label[g]}" for g, last in group_last.items() if last == i)
        manifest.append(f"| {name} | {', '.join(groups)} | {b[0]['w']} → {b[-1]['w']} | {done} | {milestone} |")

    counts = Counter(rows)
    manifest[3:3] = [f"- {label[g]}: {counts[g]} từ" for g, _ in GROUPS] + [""]
    with open(os.path.join(WORK, "MANIFEST.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(manifest) + "\n")
    print(f"{len(entries)} từ -> {len(batches)} batch. Hán Việt: {dict(stats)}")
    print("Xem work/MANIFEST.md")


# Từ W061 trở đi làm theo hai lượt nhẹ (xem docs/JMDICT_VI_PIPELINE.md):
#   N (nghĩa + Hán Việt, thêm 1 câu ví dụ cho từ không có câu Tatoeba), mỗi batch = 2 batch W
#   C (dịch câu ví dụ Tatoeba sang tiếng Việt)
NEW_FLOW_START = 61
NGHIA_DIR = os.path.join(ROOT, "data", "nghia")
CAU_DIR = os.path.join(ROOT, "data", "cau")
TATOEBA_CHOSEN = os.path.join(ROOT, "data", "tatoeba_chon.json")
CAU_BATCH_SIZE = 450


def new_flow_batches():
    names = sorted(os.path.splitext(os.path.basename(p))[0]
                   for p in glob.glob(os.path.join(WORK, "batches", "W[0-9][0-9][0-9].json")))
    return [n for n in names if int(n[1:]) >= NEW_FLOW_START]


def load_chosen():
    if not os.path.exists(TATOEBA_CHOSEN):
        sys.exit("Chưa có data/tatoeba_chon.json: chạy python vn/tatoeba.py trước")
    with open(TATOEBA_CHOSEN, encoding="utf-8") as f:
        return json.load(f)


def precreate(folder, name):
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, f"{name}.json")
    if not os.path.exists(path):
        open(path, "w").close()


def cmd_nghia(args):
    chosen = load_chosen()
    prompt = read_prompt("PROMPT_NGHIA_VD.md")
    names = new_flow_batches()
    made, need_ex = [], 0
    for i in range(0, len(names), 2):
        pair = names[i:i + 2]
        items = []
        for w in pair:
            with open(os.path.join(WORK, "batches", f"{w}.json"), encoding="utf-8") as f:
                for it in json.load(f):
                    item = dict(it)
                    if str(it["id"]) not in chosen:
                        item["vd"] = True
                        need_ex += 1
                    items.append(item)
        name = "N" + pair[0][1:]
        write_batch(name, prompt, items)
        precreate(NGHIA_DIR, name)
        made.append(name)
    print(f"{len(made)} batch nghĩa: {made[0]}..{made[-1]} (mỗi batch = 2 batch W); {need_ex} từ cần AI viết 1 câu ví dụ")
    print("Kết quả lưu vào data/nghia/<tên batch>.json")


def cmd_cau(args):
    chosen = load_chosen()
    prompt = read_prompt("PROMPT_CAU.md")
    order = []
    for w in new_flow_batches():
        with open(os.path.join(WORK, "batches", f"{w}.json"), encoding="utf-8") as f:
            order += [str(it["id"]) for it in json.load(f)]
    todo, seen = [], set()
    for wid in order:
        for s in chosen.get(wid, []):
            if not s["vi"] and s["sid"] not in seen:
                seen.add(s["sid"])
                todo.append({"sid": s["sid"], "ja": s["ja"], **({"en": s["en"]} if s["en"] else {})})
    made = []
    for i in range(0, len(todo), CAU_BATCH_SIZE):
        name = f"C{i // CAU_BATCH_SIZE + 1:03d}"
        write_batch(name, prompt, todo[i:i + CAU_BATCH_SIZE])
        precreate(CAU_DIR, name)
        made.append(name)
    print(f"{len(todo)} câu cần dịch -> {len(made)} batch: {made[0]}..{made[-1]}")
    print("Kết quả lưu vào data/cau/<tên batch>.json")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("step", choices=["kanji", "words", "nghia", "cau"])
    p.add_argument("--jmdict", default=os.path.join(ROOT, "JMdict_e"))
    args = p.parse_args()
    {"kanji": cmd_kanji, "words": cmd_words, "nghia": cmd_nghia, "cau": cmd_cau}[args.step](args)


if __name__ == "__main__":
    main()
