"""
Làm lại riêng phần nghĩa ("m") của các batch W đã có, giữ nguyên Hán Việt và câu ví dụ.
Dùng khi Gemini trôi khỏi yêu cầu (tách từ đồng nghĩa thành nhiều nghĩa, hoặc dồn mọi nghĩa làm một).

    python vn/remean.py prepare W020 W021   # tạo work/batches/NGHIA_W020.txt ... để dán vào Gemini
    # lưu câu trả lời vào data/fix_nghia/W020.json (file trống đã tạo sẵn)
    python vn/remean.py apply W020 W021     # kiểm tra rồi thay "m" trong data/results/W020.json

Không ghi tên batch thì tự chọn các batch có phần nghĩa bị lệch.
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from check import add_meaning_stats, load_answer, meaning_drift, new_meaning_stats  # noqa: E402
from hanviet import read_json_array  # noqa: E402
from prepare import RESULTS, ROOT, WORK, read_prompt  # noqa: E402

FIX_DIR = os.path.join(ROOT, "data", "fix_nghia")


def read_batch(name):
    with open(os.path.join(WORK, "batches", f"{name}.json"), encoding="utf-8") as f:
        return json.load(f)


def prepare(names):
    prompt = read_prompt("PROMPT_NGHIA.md")
    os.makedirs(FIX_DIR, exist_ok=True)
    for name in names:
        items = [{k: it[k] for k in ("id", "w", "r", "pos", "en") if k in it} for it in read_batch(name)]
        with open(os.path.join(WORK, "batches", f"NGHIA_{name}.txt"), "w", encoding="utf-8") as f:
            f.write(f"{prompt}\n\nBATCH NGHIA_{name} ({len(items)} mục):\n"
                    f"{json.dumps(items, ensure_ascii=False, separators=(',', ':'))}\n")
        out = os.path.join(FIX_DIR, f"{name}.json")
        if not os.path.exists(out):
            open(out, "w").close()
        print(f"{name}: work/batches/NGHIA_{name}.txt ({len(items)} mục) -> lưu kết quả vào data/fix_nghia/{name}.json")


def apply(names):
    for name in names:
        src = os.path.join(FIX_DIR, f"{name}.json")
        if not os.path.exists(src) or os.path.getsize(src) == 0:
            print(f"{name}: data/fix_nghia/{name}.json còn trống, bỏ qua")
            continue
        try:
            answer = load_answer(src)
        except (ValueError, json.JSONDecodeError) as err:
            print(f"{name}: LỖI đọc JSON ({err}). Dán lại cả batch.")
            continue
        batch = {it["id"]: it for it in read_batch(name)}
        new = {}
        bad = []
        for a in answer:
            if not isinstance(a, dict) or a.get("id") not in batch:
                continue
            m = a.get("m")
            if isinstance(m, list) and m and all(isinstance(x, dict) and str(x.get("vi", "")).strip() for x in m):
                new[a["id"]] = [{"vi": x["vi"].strip()} for x in m[:4]]
            else:
                bad.append(batch[a["id"]]["w"])
        missing = [it["w"] for i, it in batch.items() if i not in new and it["w"] not in bad]

        stats = new_meaning_stats()
        for i, m in new.items():
            add_meaning_stats(stats, batch[i], m)
        drift = []
        meaning_drift(stats, drift)
        if drift:
            print(f"{name}: KHÔNG áp dụng, nghĩa vẫn lệch:\n   ! " + "\n   ! ".join(drift))
            continue

        changed = 0
        for path in (os.path.join(RESULTS, f"{name}.json"), os.path.join(RESULTS, f"{name}_retry.json")):
            if not os.path.exists(path) or os.path.getsize(path) == 0:
                continue
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
            for r in data:
                if r.get("id") in new:
                    r["m"] = new[r["id"]]
                    changed += 1
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=1)
        msg = f"{name}: thay nghĩa {changed} mục"
        if bad or missing:
            msg += f"; giữ nghĩa cũ cho {len(bad) + len(missing)} mục thiếu/lỗi: {', '.join((bad + missing)[:10])}"
        print(msg)


def drifted():
    """Các batch W đã có kết quả mà phần nghĩa bị lệch."""
    out = []
    for path in sorted(glob.glob(os.path.join(RESULTS, "W[0-9][0-9][0-9].json"))):
        name = os.path.splitext(os.path.basename(path))[0]
        data = read_json_array(path)
        if len(data) < 100:
            continue
        batch = {it["id"]: it for it in read_batch(name)}
        stats = new_meaning_stats()
        for r in data:
            it = batch.get(r.get("id"))
            if it:
                add_meaning_stats(stats, it, r.get("m") or [])
        warn = []
        meaning_drift(stats, warn)
        if warn:
            out.append(name)
    return out


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("prepare", "apply"):
        print(__doc__)
        return
    names = sys.argv[2:] or drifted()
    print("Batch:", " ".join(names) or "(không có batch nào lệch nghĩa)")
    (prepare if sys.argv[1] == "prepare" else apply)(names)


if __name__ == "__main__":
    main()
