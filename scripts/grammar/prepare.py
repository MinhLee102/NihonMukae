"""
Chuẩn bị prompt cho AI (Gemini bản web), hai lượt:

  python -I -X utf8 prepare.py list [--level 1] [--with-file]
      Lượt 1: prompt nhờ AI liệt kê TÊN các mẫu ngữ pháp của từng cấp -> work/list/LIST_N1.txt
      Dán câu trả lời vào data/list/N1.json (lượt sau của cùng cấp: N1_2.json, N1_3.json...).
      --with-file: nhắc AI là có đính kèm file tham khảo (chỉ lấy tên mẫu, không chép nội dung).

  python -I -X utf8 prepare.py batches
      Lượt 2: chia danh sách thành batch 15 mẫu, mỗi batch một file prompt -> work/batches/GN1-01.txt
      Dán câu trả lời vào data/results/GN1-01.json rồi chạy check.py. Batch đã có kết quả hợp lệ được bỏ qua,
      mục bị lỗi thì tự vào lại batch đó khi chạy lại lệnh này.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gram import LEVELS, LIST_DIR, RESULTS, WORK, batches, load_list, read_json_array, read_prompt  # noqa: E402

MAX_PER_ROUND = 150


def cmd_list(args):
    template = read_prompt("PROMPT_LIST.md")
    out_dir = os.path.join(WORK, "list")
    os.makedirs(out_dir, exist_ok=True)
    existing = load_list()
    for level in ([args.level] if args.level else LEVELS):
        have = [i["pattern"] for i in existing.get(level, [])]
        skip = ("Các mẫu sau ĐÃ có, tuyệt đối không liệt kê lại, hãy liệt kê tiếp những mẫu CÒN THIẾU của cấp này (nếu cấp này đã đủ thì trả về mảng rỗng []): "
                + "、".join(have)) if have else "Hãy cố gắng liệt kê đầy đủ nhất có thể."
        note = ("Tôi có đính kèm một file tham khảo. Chỉ dùng nó để biết có những mẫu nào; tuyệt đối không chép lại câu ví dụ hay lời giải thích trong file."
                if args.with_file else "")
        text = (template.replace("{LEVEL}", str(level)).replace("{MAX}", str(MAX_PER_ROUND))
                .replace("{SKIP}", skip).replace("{FILE_NOTE}", note))
        path = os.path.join(out_dir, f"LIST_N{level}.txt")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        print(f"N{level}: {len(have)} mẫu đã có -> {path}")
    os.makedirs(LIST_DIR, exist_ok=True)


def done_ids():
    """Id đã có nội dung hợp lệ (check.py chỉ giữ mục đạt trong file kết quả)."""
    ids = set()
    if os.path.isdir(RESULTS):
        for name in os.listdir(RESULTS):
            if name.endswith(".json"):
                ids.update(str(x.get("id")) for x in read_json_array(os.path.join(RESULTS, name)) if isinstance(x, dict))
    return ids


def cmd_batches(_args):
    template = read_prompt("PROMPT_EXPLAIN.md")
    listing = load_list()
    if not listing:
        sys.exit("Chưa có danh sách mẫu. Làm lượt 1 (prepare.py list) và lưu kết quả vào data/list/ trước.")
    done = done_ids()
    out_dir = os.path.join(WORK, "batches")
    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(RESULTS, exist_ok=True)
    todo = total = 0
    for name, items in batches(listing):
        total += len(items)
        pending = [i for i in items if i["id"] not in done]
        path = os.path.join(out_dir, f"{name}.txt")
        if not pending:
            if os.path.exists(path):
                os.remove(path)
            continue
        todo += len(pending)
        lines = [json.dumps({"id": i["id"], "level": f"N{i['level']}", "pattern": i["pattern"],
                             "reading": i["reading"]}, ensure_ascii=False) for i in pending]
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(template.replace("{LIST}", "[\n" + ",\n".join(lines) + "\n]"))
        # tạo sẵn file trống để dán kết quả vào, giống pipeline từ vựng
        target = os.path.join(RESULTS, f"{name}.json")
        if not os.path.exists(target):
            open(target, "w", encoding="utf-8").close()
    print(f"{total} mẫu, {todo} mẫu cần làm -> {out_dir}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("list")
    p.add_argument("--level", type=int, choices=LEVELS)
    p.add_argument("--with-file", action="store_true")
    p.set_defaults(fn=cmd_list)
    sub.add_parser("batches").set_defaults(fn=cmd_batches)
    args = ap.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
