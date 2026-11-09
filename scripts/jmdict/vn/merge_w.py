"""
Ghép kết quả hai lượt (từ W061) thành data/results/W0xx.json đúng format cũ, rồi chạy check.py cho file đó.

    python vn/merge_w.py              # mọi batch W061+ có đủ nghĩa
    python vn/merge_w.py W061 W062    # vài batch

Nguồn:
  data/nghia/N*.json      nghĩa, Hán Việt, câu AI viết (mục có "vd")
  data/tatoeba_chon.json  câu Tatoeba đã chọn cho từng từ (kèm bản dịch tiếng Việt có sẵn nếu có)
  data/cau/C*.json        bản dịch tiếng Việt của câu Tatoeba (hoặc "bo": câu bị loại)

Mỗi câu ví dụ có thêm "src" ("tatoeba" kèm "sid", hoặc "ai") để import ghi nguồn đúng giấy phép.
Câu Tatoeba chưa được dịch thì tạm bỏ qua: chạy lại merge sau khi dịch xong batch C là đủ.
Chạy lại bao nhiêu lần cũng được (luôn ghép lại từ đầu).
"""
import glob
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
from hanviet import read_json_array  # noqa: E402
from prepare import CAU_DIR, NGHIA_DIR, RESULTS, TATOEBA_CHOSEN, WORK, new_flow_batches  # noqa: E402


def load_dir(folder, key):
    """Gộp N061.json, N061_part1.json, N061_retry.json... (file sau ghi đè file trước)."""
    out = {}
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        for x in read_json_array(path):
            if isinstance(x, dict) and key in x:
                out[x[key]] = x
    return out


def main():
    names = sys.argv[1:] or new_flow_batches()
    nghia = load_dir(NGHIA_DIR, "id")
    cau = load_dir(CAU_DIR, "sid")
    with open(TATOEBA_CHOSEN, encoding="utf-8") as f:
        chosen = json.load(f)

    written, totals = [], {"tatoeba": 0, "ai": 0, "cho_dich": 0, "bo": 0, "khong_vd": 0}
    for name in names:
        with open(os.path.join(WORK, "batches", f"{name}.json"), encoding="utf-8") as f:
            batch = json.load(f)
        missing = [it["w"] for it in batch if it["id"] not in nghia]
        if missing:
            print(f"{name}: chưa đủ nghĩa ({len(missing)}/{len(batch)} mục thiếu), bỏ qua")
            continue
        out = []
        for it in batch:
            n = nghia[it["id"]]
            ex = []
            for s in chosen.get(str(it["id"]), []):
                if s["vi"]:
                    ex.append({"ja": s["ja"], "vi": s["vi"], "src": "tatoeba", "sid": s["sid"]})
                elif s["sid"] in cau:
                    c = cau[s["sid"]]
                    if c.get("bo"):
                        totals["bo"] += 1
                    else:
                        ex.append({"ja": s["ja"], "vi": c["vi"], "src": "tatoeba", "sid": s["sid"]})
                else:
                    totals["cho_dich"] += 1
            if not ex and n.get("ex"):
                ex = [{**n["ex"][0], "src": "ai"}]
            for e in ex:
                totals["tatoeba" if e["src"] == "tatoeba" else "ai"] += 1
            entry = {"id": it["id"], "m": n["m"], "ex": ex[:2]}
            if "hv" in n:
                entry["hv"] = n["hv"]
            if not ex:
                entry["ex_none"] = True
                totals["khong_vd"] += 1
            out.append(entry)
        with open(os.path.join(RESULTS, f"{name}.json"), "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
        written.append(name)

    print(f"Đã ghép {len(written)} batch. Câu ví dụ: {totals['tatoeba']} Tatoeba, {totals['ai']} AI viết; "
          f"{totals['cho_dich']} câu Tatoeba chờ dịch, {totals['bo']} câu bị loại; {totals['khong_vd']} từ chưa có ví dụ.")
    if totals["cho_dich"]:
        print("Còn câu chờ dịch: làm tiếp batch C rồi chạy lại merge_w.py.")
    if written:
        subprocess.run([sys.executable, "-X", "utf8", os.path.join(os.path.dirname(__file__), "check.py"), *written])


if __name__ == "__main__":
    main()
