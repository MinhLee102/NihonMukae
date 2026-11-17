# Pipeline ngữ pháp

Nội dung ngữ pháp (giải thích, cách chia, ví dụ) do AI viết mới, không sao chép từ sách hay đề. Chạy từ thư mục `scripts/grammar`
bằng Python của venv trong `scripts/jmdict` (cần `psycopg2`, đã có). Luôn thêm `-I -X utf8`.

## Lượt 1: danh sách tên mẫu theo cấp

```
python -I -X utf8 prepare.py list              # work/list/LIST_N5.txt ... LIST_N1.txt
```

Dán từng file vào Gemini, lưu câu trả lời vào `data/list/N5.json`, `N4.json`... Nếu AI chưa liệt kê đủ, chạy lại lệnh trên
(prompt tự thêm danh sách đã có để AI liệt kê tiếp phần còn thiếu) và lưu lượt sau vào `N5_2.json`, `N5_3.json`.
Chỉ thêm file hoặc thêm cuối mảng, không chèn giữa, để id (`N5-001`...) của các mẫu cũ không đổi.

## Lượt 2: nội dung từng mẫu

```
python -I -X utf8 prepare.py batches           # work/batches/GN5-01.txt ... (15 mẫu mỗi batch)
```

Dán từng file vào Gemini, lưu câu trả lời vào `data/results/GN5-01.json` (file trống đã được tạo sẵn), rồi:

```
python -I -X utf8 check.py                     # hoặc check.py GN5-01
```

`check.py` làm sạch định dạng, bỏ mục lỗi khỏi file và báo cáo ở `work/reports/`. Chạy lại `prepare.py batches` thì mục lỗi
tự vào lại batch của nó (batch đã đạt hết thì bị bỏ). Mục AI đánh dấu `"invalid": true` được ghi vào `data/invalid.json`
và không hỏi lại.

## Nạp dữ liệu

```
python -I -X utf8 import_grammar.py            # vào DB local
python -I -X utf8 import_grammar.py --sql      # work/grammar_feed.sql để chạy trên VPS
```

Trên VPS (sau khi backend đã chạy migration V27):

```
dc exec -T postgres psql -U nihon -d nihon_mukae -v ON_ERROR_STOP=1 < grammar_feed.sql
```

Chạy lại bao nhiêu lần cũng được (ghi đè theo cấp độ và tên mẫu). Tab Ngữ pháp trên web vẫn khoá cho đến khi có trang đọc
bảng `grammar_points` và `grammar_examples`.
