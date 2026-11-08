# Từ điển JMdict + tiếng Việt (Gemini bản web)

Thay dần dữ liệu Mazii bằng từ điển JMdict, phần tiếng Việt do Gemini viết qua giao diện web.
Mọi script nằm ở `scripts/jmdict/vn/`, chạy trong thư mục `scripts/jmdict` với venv sẵn có.

## Chuẩn bị một lần

1. `scripts/jmdict/JMdict_e` (đã có), cùng hai nguồn tải về `scripts/jmdict/sources/`:
   - `jlpt_n1.csv` … `jlpt_n5.csv` từ https://github.com/stephenmk/yomitan-jlpt-vocab (thư mục `original_data`)
   - `Unihan_Readings.txt`, `Unihan_Variants.txt` giải nén từ https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip
2. Tạo batch:

   ```
   python vn/prepare.py kanji
   python vn/prepare.py words
   ```

   Batch nằm ở `scripts/jmdict/work/batches/`, thứ tự và các mốc ở `scripts/jmdict/work/MANIFEST.md`.

## Mỗi lượt Gemini

1. Mở `work/batches/K01.txt` (hoặc `W001.txt`…), copy toàn bộ, dán vào Gemini.
2. Copy câu trả lời, lưu vào `scripts/jmdict/data/results/K01.json` (đúng tên batch).
3. `python vn/check.py K01`
   - `OK`: xong.
   - `N LỖI`: dán `work/batches/K01_retry.txt` vào Gemini, lưu thành `data/results/K01_retry.json`, check lại.
   - Cảnh báo: xem `work/reports/K01.txt` khi rảnh, sửa tay trong file kết quả nếu cần.

**Làm hết 11 batch kanji (K01–K11) trước.** Sau đó chạy lại `python vn/prepare.py words`: từ nào mọi chữ
chỉ có một âm Hán Việt thì script tự ghép, từ nào có chữ nhiều âm thì Gemini chỉ được chọn trong danh sách.
Batch W làm trước khi có bảng kanji vẫn dùng được, chỉ là Hán Việt do Gemini tự điền và bị đối chiếu kém hơn.

Mẹo: một cuộc chat Gemini dán được vài batch liên tiếp; khi câu trả lời bắt đầu cụt hoặc lặp thì mở chat mới.

## Nạp vào database

```
python vn/import_dict.py
```

Nạp mọi từ đã chọn (nghĩa tiếng Anh của JMdict) và phần tiếng Việt của các batch đã có. Chạy lại được bất cứ lúc nào.
Web chưa đọc các bảng `dict_*` cho tới bước chuyển đổi (tuần 5).

`data/results/` được đưa lên git: đó là phần tốn công nhất, đừng để mất.

## Giấy phép

- JMdict: CC BY-SA 4.0, © Electronic Dictionary Research and Development Group. Nghĩa tiếng Việt dịch từ JMdict
  là bản phái sinh nên cũng theo CC BY-SA 4.0.
- Danh sách JLPT: Jonathan Waller (CC BY), gắn mã JMdict bởi stephenmk/yomitan-jlpt-vocab (CC BY-SA 4.0).
- Unihan: Unicode License.

Trang web cần một dòng ghi nguồn các bộ dữ liệu trên khi chuyển sang từ điển mới.
