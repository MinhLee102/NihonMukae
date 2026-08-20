# Mazii CSV Import

Import 4 file CSV đã crawl sẵn từ Mazii vào PostgreSQL (bảng `words`,
`word_meanings`, `example_sentences`, `kanji`).

## 1. Chuẩn bị

```bash
cd scripts/mazii
pip install -r requirements.txt
cp .env.example .env
# rồi sửa .env, điền connection string DB thật của bạn
```

Áp dụng migration `V__add_mazii_columns_and_kanji.sql` trước (đổi tên theo
số Flyway tiếp theo trong dự án của bạn) — migration này thêm các cột
`mazii_vocab_id`, `han_viet`, `pronunciation`, `pos` vào `words`,
`mazii_meaning_id` vào `word_meanings`, và tạo bảng `kanji`.

Đặt 4 file CSV gốc vào `scripts/mazii/data/` (thư mục này đã gitignore,
KHÔNG commit CSV gốc vào repo):

```
scripts/mazii/data/
├── mazii_vocabs.csv
├── meaning_detail.csv
├── examples.csv
└── kanji.csv
```

## 2. Chạy import

```bash
python import_mazii_csv.py --db-url postgresql://dev:dev@localhost:5432/nihon_mukae
```

Script chạy theo thứ tự: `words` → `word_meanings` → `example_sentences` →
`kanji` → rebuild `search_vector`. Có thể chạy lại nhiều lần an toàn (idempotent) —
dữ liệu trùng sẽ được UPDATE thay vì tạo bản ghi mới, nhờ các cột
`mazii_vocab_id` / `mazii_meaning_id` / `mazii_kanji_id` là UNIQUE.

Nếu chỉ muốn chạy lại một phần (vd sau khi sửa lỗi ở kanji.csv):

```bash
python import_mazii_csv.py --skip-vocab --skip-meanings --skip-examples
```

## 3. Verify sau import (DATA-02)

Gợi ý kiểm tra nhanh:

```sql
-- Đếm số dòng từng bảng
SELECT count(*) FROM words;
SELECT count(*) FROM word_meanings;
SELECT count(*) FROM example_sentences;
SELECT count(*) FROM kanji;

-- Orphan check: word_meanings không trỏ được về words không nên tồn tại
-- (FK đã đảm bảo ở mức DB, nhưng kiểm tra thêm số dòng bị bỏ qua lúc import
-- qua log của script — script tự in ra số dòng skip vì thiếu id khớp)

-- Spot-check vài từ ngẫu nhiên
SELECT * FROM words WHERE mazii_vocab_id IS NOT NULL ORDER BY random() LIMIT 20;
```

## 4. Auto-seed lại sau khi mất Docker/WSL

Nếu bạn hay xoá Docker/WSL để dọn ổ đĩa (container Postgres bị xoá sạch,
mất hết data), không cần làm lại từ đầu thủ công. Vì backend chạy trong
cùng `docker compose`, chỉ cần:

```powershell
.\dev-up.bat
```

(chạy từ thư mục root repo). Script này:
1. `docker compose up -d` — bật lại toàn bộ container (Postgres, backend,
   frontend...). Backend tự chạy Flyway để tạo lại schema khi khởi động.
2. Gọi `ensure_seeded.py` — đợi Postgres sẵn sàng, đợi bảng `words` xuất
   hiện (backend cần thời gian boot + chạy Flyway), rồi kiểm tra:
   - Bảng rỗng → tự động chạy lại toàn bộ import CSV.
   - Đã có dữ liệu → bỏ qua, không làm gì.

An toàn để chạy `dev-up.bat` mỗi lần bạn bật máy — nếu data vẫn còn
nguyên (Docker không bị xoá) thì chỉ mất vài giây kiểm tra rồi bỏ qua.

Cũng có thể chạy riêng bước seed (nếu container đã up sẵn từ trước):

```bash
cd scripts/mazii
python ensure_seeded.py
```

## Lưu ý

- `han_viet` được bóc ngoặc 「...」, vd `「ĐỊA VỰC ĐẠI BIỂU」` → `ĐỊA VỰC ĐẠI BIỂU`.
- `type_word` được bỏ tiền tố trang trí `☆ `/`★ `, vd `☆ Danh từ` → `Danh từ`.
- `pronunciation` là ký hiệu **trọng âm (pitch accent)**, vd `chi＼ikida／ihyo–‾`
  — giữ nguyên văn, không parse thêm (chưa dùng đến trong Phase 1, để dành
  cho tính năng phát âm/nghe sau này nếu cần).
- Bảng `kanji` hiện chưa liên kết với `words` (chưa có bảng junction
  `word_kanji`) — sẽ bổ sung sau khi làm tính năng hiển thị kanji cấu
  thành từ.