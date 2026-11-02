"""
import_questions.py

Nạp đề JLPT / câu hỏi từ file JSON vào hệ thống đang chạy, qua API import (preview rồi commit).
Format file: xem backend/src/main/resources/jlpt/sample-n3.json và mục 5.8.1 trong spec.

Ví dụ:
  # Chỉ kiểm tra file, chưa lưu gì
  python scripts/jlpt/import_questions.py de-n3-2023-07.json --api https://85-211-178-19.sslip.io --email you@example.com --dry-run

  # Nạp thật (hỏi xác nhận trước khi lưu)
  python scripts/jlpt/import_questions.py de-n3-2023-07.json --api https://85-211-178-19.sslip.io --email you@example.com

Mật khẩu được hỏi khi chạy (không hiện trên màn hình). Có thể đặt biến môi trường JLPT_PASSWORD để chạy không cần hỏi.
Chỉ dùng thư viện chuẩn của Python, không cần cài gì thêm.
"""
import argparse
import getpass
import json
import os
import sys
import urllib.error
import urllib.request


def call(api, method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(api.rstrip("/") + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            return res.status, json.loads(res.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"message": raw}
    except urllib.error.URLError as e:
        sys.exit(f"Không kết nối được tới {api}: {e.reason}")


def main():
    parser = argparse.ArgumentParser(description="Import JLPT questions from a JSON file")
    parser.add_argument("file", help="Đường dẫn file JSON")
    parser.add_argument("--api", required=True, help="Địa chỉ backend, ví dụ https://85-211-178-19.sslip.io")
    parser.add_argument("--email", required=True, help="Email tài khoản đăng nhập")
    parser.add_argument("--on-duplicate", choices=["skip", "overwrite"], default="skip",
                        help="Câu trùng: bỏ qua (mặc định) hoặc ghi đè (chỉ tài khoản ADMIN)")
    parser.add_argument("--dry-run", action="store_true", help="Chỉ preview, không lưu")
    parser.add_argument("--yes", action="store_true", help="Không hỏi xác nhận trước khi lưu")
    args = parser.parse_args()

    try:
        with open(args.file, encoding="utf-8") as f:
            payload = json.load(f)
    except (OSError, ValueError) as e:
        sys.exit(f"Không đọc được file JSON: {e}")

    password = os.environ.get("JLPT_PASSWORD") or getpass.getpass("Mật khẩu: ")

    status, body = call(args.api, "POST", "/auth/login", {"email": args.email, "password": password})
    if status != 200 or "accessToken" not in body:
        sys.exit(f"Đăng nhập thất bại ({status}): {body.get('message') or body.get('error') or body}")
    token = body["accessToken"]

    status, body = call(args.api, "POST", "/questions/import/preview", payload, token)
    if status != 200:
        sys.exit(f"Preview lỗi ({status}): {body.get('message', body)}")
    preview = body["data"]

    print(f"Đề: {preview.get('testTitle') or '(không gắn đề, vào kho chung)'}")
    print(f"Số câu: {preview['questionCount']}, số nhóm đoạn văn/audio: {preview['groupCount']}")

    if preview["errors"]:
        print(f"\nCó {len(preview['errors'])} lỗi, chưa thể nạp:")
        for err in preview["errors"]:
            print(f"  - {err['path'] or '(file)'}: {err['message']}")
        sys.exit(1)

    if preview["duplicates"]:
        print(f"\n{len(preview['duplicates'])} câu trùng ({'sẽ ghi đè' if args.on_duplicate == 'overwrite' else 'sẽ bỏ qua'}):")
        for dup in preview["duplicates"][:20]:
            print(f"  - {dup['path']}: {dup['existing']}")
        if len(preview["duplicates"]) > 20:
            print(f"  ... và {len(preview['duplicates']) - 20} câu nữa")

    if args.dry_run:
        print("\nDry run: file hợp lệ, chưa lưu gì.")
        return

    if not args.yes and input("\nLưu vào hệ thống? (y/N) ").strip().lower() != "y":
        sys.exit("Đã hủy, chưa lưu gì.")

    status, body = call(args.api, "POST", "/questions/import/commit",
                        {"previewId": preview["previewId"], "onDuplicate": args.on_duplicate}, token)
    if status != 200:
        sys.exit(f"Commit lỗi ({status}): {body.get('message', body)}")
    r = body["data"]
    print(f"\nXong: thêm {r['createdQuestions']} câu, {r['createdGroups']} nhóm, "
          f"bỏ qua {r['skipped']}, ghi đè {r['overwritten']}.")


if __name__ == "__main__":
    main()
