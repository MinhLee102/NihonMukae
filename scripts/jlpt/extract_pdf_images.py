"""
extract_pdf_images.py

Tách hình minh họa (tranh đề bài, tranh đáp án của bài nghe) từ file PDF đề thi thành các file PNG riêng.

Cách 1, tự động: lấy các hình được nhúng riêng trong PDF. Một hình hay bị cắt thành vài mảnh sát nhau, script gom lại.
  python scripts/jlpt/extract_pdf_images.py de-n3.pdf --out hinh-n3 --prefix n3_2021_12

Cách 2, cắt tay theo tỉ lệ trang, dùng cho trang mà hình bị nhúng liền với nền nên cách 1 không thấy.
Vùng cắt viết là TRANG:x0,y0,x1,y1, mỗi số từ 0 đến 1 (0,0 là góc trên trái của trang).
  python scripts/jlpt/extract_pdf_images.py de-n3.pdf --out hinh-n3 --crop 3:0.25,0.08,0.95,0.40

Kết quả nằm trong thư mục --out, tên dạng n3_2021_12_p01_1.png (p là số trang, số sau là thứ tự từ trên xuống).
Dùng --preview để xuất kèm ảnh cả trang, giúp đối chiếu hình thuộc câu nào và chọn tỉ lệ cho --crop.
Đổi tên file theo câu hỏi (ví dụ n3_q01.png) rồi ghi vào JSON bằng [[img:ten_file.png]].

Cần cài: pip install pymupdf. Chỉ dùng cho nội dung bạn có quyền sử dụng.
"""
import argparse
import os
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

try:
    import fitz  # PyMuPDF
except ImportError:
    sys.exit("Thiếu thư viện. Chạy: pip install pymupdf")


def merge_boxes(boxes, gap):
    """Gom các hình chữ nhật chạm nhau hoặc cách nhau không quá `gap` điểm."""
    boxes = [fitz.Rect(b) for b in boxes]
    changed = True
    while changed:
        changed = False
        out = []
        while boxes:
            current = boxes.pop()
            grown = fitz.Rect(current.x0 - gap, current.y0 - gap, current.x1 + gap, current.y1 + gap)
            merged = False
            for i, other in enumerate(out):
                if grown.intersects(other):
                    out[i] = other | current
                    merged = True
                    changed = True
                    break
            if not merged:
                out.append(current)
        boxes = out
    return boxes


def save_clip(page, rect, path, dpi):
    clip = fitz.Rect(rect.x0 - 2, rect.y0 - 2, rect.x1 + 2, rect.y1 + 2) & page.rect
    page.get_pixmap(dpi=dpi, clip=clip).save(path)


def parse_crop(text, page_count):
    try:
        page_part, box_part = text.split(":")
        x0, y0, x1, y1 = (float(v) for v in box_part.split(","))
        number = int(page_part)
    except ValueError:
        sys.exit(f"--crop sai định dạng: {text}. Dạng đúng: 3:0.25,0.08,0.95,0.40")
    if not (1 <= number <= page_count) or not (0 <= x0 < x1 <= 1 and 0 <= y0 < y1 <= 1):
        sys.exit(f"--crop ngoài phạm vi: {text}")
    return number, (x0, y0, x1, y1)


def main():
    parser = argparse.ArgumentParser(description="Tách hình minh họa từ PDF đề thi")
    parser.add_argument("pdf")
    parser.add_argument("--out", default="hinh", help="Thư mục xuất ảnh")
    parser.add_argument("--prefix", default="hinh", help="Tiền tố tên file")
    parser.add_argument("--dpi", type=int, default=150)
    parser.add_argument("--min-width", type=float, default=70, help="Bỏ hình rộng dưới mức này (điểm PDF)")
    parser.add_argument("--min-height", type=float, default=50, help="Bỏ hình cao dưới mức này (điểm PDF)")
    parser.add_argument("--gap", type=float, default=6, help="Các mảnh cách nhau dưới mức này được gộp")
    parser.add_argument("--crop", action="append", default=[], help="Cắt tay TRANG:x0,y0,x1,y1 (tỉ lệ 0 đến 1), dùng được nhiều lần")
    parser.add_argument("--preview", action="store_true", help="Xuất thêm ảnh cả trang để đối chiếu")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    doc = fitz.open(args.pdf)
    total = 0

    if args.crop:
        counters = {}
        for text in args.crop:
            number, (x0, y0, x1, y1) = parse_crop(text, len(doc))
            page = doc[number - 1]
            counters[number] = counters.get(number, 0) + 1
            rect = fitz.Rect(page.rect.width * x0, page.rect.height * y0, page.rect.width * x1, page.rect.height * y1)
            name = f"{args.prefix}_p{number:02d}_c{counters[number]}.png"
            save_clip(page, rect, os.path.join(args.out, name), args.dpi)
            total += 1
            print(f"Đã cắt {name}")
    else:
        for number, page in enumerate(doc, start=1):
            # Dải ngang rộng gần cả trang thường là nền hoặc hình mờ của file, không phải tranh
            pieces = [info["bbox"] for info in page.get_image_info()
                      if (info["bbox"][2] - info["bbox"][0]) < 0.9 * page.rect.width]
            clusters = merge_boxes(pieces, args.gap) if pieces else []
            keep = [c for c in clusters if c.width >= args.min_width and c.height >= args.min_height]
            keep.sort(key=lambda r: (round(r.y0 / 20), r.x0))
            for index, rect in enumerate(keep, start=1):
                save_clip(page, rect, os.path.join(args.out, f"{args.prefix}_p{number:02d}_{index}.png"), args.dpi)
                total += 1
            print(f"Trang {number}: {len(keep)} hình" + ("" if keep else " (không thấy, dùng --crop nếu trang này có tranh)"))

    if args.preview:
        for number, page in enumerate(doc, start=1):
            page.get_pixmap(dpi=60).save(os.path.join(args.out, f"_preview_p{number:02d}.png"))
    print(f"Xong: {total} hình trong thư mục {args.out}")


if __name__ == "__main__":
    main()
