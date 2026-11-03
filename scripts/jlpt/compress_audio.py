"""
Nén audio đề nghe trước khi upload: mono, 64 kbps, thường nhẹ hơn 4 đến 8 lần mà giọng vẫn rõ.
Chạy trên máy cá nhân, cần cài ffmpeg (https://ffmpeg.org/download.html, thêm vào PATH).

    python scripts/jlpt/compress_audio.py de-n3-2021-12.mp3
    python scripts/jlpt/compress_audio.py thu-muc-audio --out audio-nen
    python scripts/jlpt/compress_audio.py a.mp3 b.wav --bitrate 48

Kết quả là file mp3 cùng tên ở thư mục --out (mặc định "audio-nen"), file gốc không bị sửa.
"""
import argparse
import shutil
import subprocess
import sys
from pathlib import Path

AUDIO_SUFFIXES = {".mp3", ".m4a", ".wav", ".ogg", ".flac", ".aac"}


def collect(inputs):
    files = []
    for item in inputs:
        p = Path(item)
        if p.is_dir():
            files += sorted(f for f in p.iterdir() if f.suffix.lower() in AUDIO_SUFFIXES)
        elif p.is_file():
            files.append(p)
        else:
            print(f"Không tìm thấy: {item}", file=sys.stderr)
    return files


def main():
    parser = argparse.ArgumentParser(description="Nén audio đề nghe (mono, mp3)")
    parser.add_argument("inputs", nargs="+", help="file audio hoặc thư mục chứa audio")
    parser.add_argument("--out", default="audio-nen", help="thư mục kết quả (mặc định: audio-nen)")
    parser.add_argument("--bitrate", type=int, default=64, help="kbps (mặc định 64; 48 vẫn nghe được, nhẹ hơn nữa)")
    args = parser.parse_args()

    if shutil.which("ffmpeg") is None:
        sys.exit("Chưa cài ffmpeg hoặc chưa thêm vào PATH.")

    files = collect(args.inputs)
    if not files:
        sys.exit("Không có file audio nào để nén.")

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    before_total = after_total = 0
    for src in files:
        dst = out_dir / (src.stem + ".mp3")
        if dst.resolve() == src.resolve():
            print(f"Bỏ qua {src.name}: file kết quả trùng file gốc", file=sys.stderr)
            continue
        cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", str(src), "-vn", "-ac", "1",
               "-b:a", f"{args.bitrate}k", str(dst)]
        if subprocess.run(cmd).returncode != 0:
            print(f"Lỗi khi nén {src.name}", file=sys.stderr)
            continue
        before, after = src.stat().st_size, dst.stat().st_size
        before_total += before
        after_total += after
        print(f"{src.name}: {before / 1e6:.1f} MB -> {after / 1e6:.1f} MB ({after / before:.0%})")

    if before_total:
        print(f"Tổng: {before_total / 1e6:.1f} MB -> {after_total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
