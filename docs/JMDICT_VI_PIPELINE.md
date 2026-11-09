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

## Từ W061: hai lượt nhẹ (nghĩa, rồi dịch câu Tatoeba)

W001–W060 (đủ nhóm cốt lõi, N2, N1) làm theo cách trên. Từ W061 trở đi phần lớn công sức của cách cũ là
viết câu ví dụ, nên tách làm hai lượt, đầu ra cuối cùng vẫn là `data/results/W0xx.json` đúng format cũ:

1. Một lần: tải từ https://downloads.tatoeba.org/exports/ vào `sources/tatoeba/` rồi giải nén:
   `per_language/jpn/jpn_sentences.tsv`, `jpn_indices.csv` (trong `jpn_indices.tar.bz2`),
   `per_language/jpn/jpn-eng_links.tsv`, `per_language/eng/eng_sentences.tsv`,
   `per_language/jpn/jpn-vie_links.tsv`, `per_language/vie/vie_sentences.tsv`.
2. `python vn/tatoeba.py`: chọn tối đa 2 câu cho mỗi từ (ưu tiên câu Tanaka đánh dấu `~`, câu ngắn, câu có sẵn
   bản dịch), lọc câu tục tĩu, ghi `data/tatoeba_chon.json` (đưa lên git; **đừng chạy lại với `--force` sau khi
   đã bắt đầu dịch batch C**, vì batch C dựa vào mã câu trong file này). Khoảng 50% từ có câu.
3. `python vn/prepare.py nghia` → batch **N061…N203** (mỗi batch = 2 batch W, 300 từ): AI viết nghĩa + Hán Việt;
   mục có `"vd": true` (từ không có câu Tatoeba) thì viết thêm đúng 1 câu ví dụ. Kết quả: `data/nghia/N061.json`.
4. `python vn/prepare.py cau` → batch **C001…C036** (450 câu): AI dịch câu Tatoeba sang tiếng Việt, hoặc
   `{"sid": …, "bo": true}` nếu câu không phù hợp. Kết quả: `data/cau/C001.json`.
5. `python vn/check.py N061` / `python vn/check.py C001`: như batch W (file `_retry` khi có lỗi, file `_partN`
   khi dán nhiều lần).
6. `python vn/merge_w.py`: ghép nghĩa + câu (Tatoeba đã dịch, hoặc câu AI) thành `data/results/W0xx.json`, rồi tự
   chạy `check.py`. Câu Tatoeba chưa dịch thì tạm bỏ qua; dịch xong batch C thì chạy lại merge là đủ.

Câu ví dụ có thêm `"src"` (`"tatoeba"` kèm `"sid"`, hoặc `"ai"`), import ghi vào cột `source`, `source_id` (V19).

## Nạp vào database

```
python vn/import_dict.py
```

Nạp mọi từ đã chọn (nghĩa tiếng Anh của JMdict) và phần tiếng Việt của các batch đã có. Chạy lại được bất cứ lúc nào.
Web tra từ điển trên các bảng `dict_*` (thay Mazii). Mục admin đã sửa tay (`dict_entries.edited`) hoặc đã xoá
(`dict_deleted`) trong trang `/admin/dictionary` được import bỏ qua, nên chạy lại import không mất chỉnh sửa.
Từ chưa có nghĩa tiếng Việt hiện nghĩa tiếng Anh của JMdict kèm nhãn.

`data/results/` được đưa lên git: đó là phần tốn công nhất, đừng để mất.

## Giấy phép

- JMdict: CC BY-SA 4.0, © Electronic Dictionary Research and Development Group. Nghĩa tiếng Việt dịch từ JMdict
  là bản phái sinh nên cũng theo CC BY-SA 4.0.
- Danh sách JLPT: Jonathan Waller (CC BY), gắn mã JMdict bởi stephenmk/yomitan-jlpt-vocab (CC BY-SA 4.0).
- Unihan: Unicode License.
- Câu ví dụ Tatoeba (Tanaka Corpus): CC BY 2.0 FR, https://tatoeba.org. Cần ghi nguồn; mỗi câu có `source_id`
  để dẫn về `https://tatoeba.org/sentences/show/<id>`.

Trang web cần một dòng ghi nguồn các bộ dữ liệu trên khi chuyển sang từ điển mới.
