# Định dạng JSON để import đề JLPT

Dùng ở Admin, mục **Ngân hàng đề**, nút **Import JSON**. Cùng định dạng này cũng dùng cho `scripts/jlpt/import_questions.py` (tài khoản chạy script cần là ADMIN).

Hệ thống kiểm tra toàn bộ file trước khi lưu. Nếu có lỗi, nút Lưu bị khóa và danh sách lỗi cho biết chính xác đường dẫn (ví dụ `groups[2].questions[1].correct`).

## Cấu trúc tổng

```json
{
  "test":      { ... },   // thông tin đề, bỏ trống nếu chỉ thêm câu lẻ vào kho
  "questions": [ ... ],   // câu đơn: một câu một mục
  "groups":    [ ... ]    // nhóm: nhiều câu dùng chung một đoạn văn, audio hoặc ảnh
}
```

Phải có ít nhất 1 câu trong `questions` hoặc trong `groups`.

### test

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `level` | nên có | 1 đến 5, hoặc viết `"N1"` đến `"N5"`. Là level mặc định cho mọi câu trong file |
| `title` | nên có | Ví dụ `JLPT N3 2023-07` |
| `year`, `month` | không | Tháng 1 đến 12 |
| `source_note` | không | Ghi chú nguồn |
| `audio_url` | không | Tên file audio của **cả phần nghe** (một file duy nhất, xem phần Audio) |

Có `test` thì các câu được gắn vào đề đó để thi thử nguyên đề. Không có thì chỉ vào kho luyện tập theo dạng bài.

### Câu hỏi (mục trong `questions`, hoặc trong `groups[].questions`)

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `type` | có | Mã dạng bài, xem bảng dưới. Câu trong nhóm có thể bỏ để kế thừa từ nhóm |
| `level` | không | Mặc định lấy từ nhóm rồi đến `test.level` |
| `question` | có | Nội dung câu hỏi. Dùng `<u>từ</u>` để gạch chân từ được hỏi |
| `options` | có | 3 hoặc 4 đáp án, không được để trống |
| `correct` | có | Một chữ cái: `A` là đáp án 1, `B` là 2, ... |
| `explanation` | không | Giải thích hiện sau khi nộp bài |
| `audio_url` | không | Tên file audio (xem phần Audio) |

Số `問題N` và câu hướng dẫn tiếng Nhật được hệ thống tự thêm theo dạng bài, **không** ghi vào JSON.

### Nhóm (mục trong `groups`)

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `type` | có | Mã dạng bài của cả nhóm |
| `level` | không | |
| `title` | không | Tiêu đề nhỏ phía trên đoạn văn |
| `passage` | một trong ba | Đoạn văn. Xuống dòng bằng `\n` |
| `audio_url` | một trong ba | Audio dùng chung cho các câu trong nhóm |
| `image_url` | một trong ba | Ảnh dùng chung (tên file ảnh) |
| `questions` | có | Ít nhất 1 câu, cùng định dạng ở trên |

Nhóm phải có ít nhất một trong `passage`, `audio_url`, `image_url`.

## Mã dạng bài (`type`)

| Mã | Phần thi | Tên | Dạng nhóm |
|---|---|---|---|
| `kanji_reading` | 文字 | Đọc Hán tự | không |
| `orthography` | 文字 | Viết Hán tự | không |
| `word_formation` | 文字 | Tiền tố, hậu tố | không |
| `context_word` | 語彙 | Chọn từ theo ngữ cảnh | không |
| `synonym` | 語彙 | Từ đồng nghĩa | không |
| `word_usage` | 語彙 | Cách dùng từ | không |
| `grammar_choice` | 文法 | Chọn ngữ pháp | không |
| `grammar_ordering` | 文法 | Sắp xếp câu (câu có ★) | không |
| `grammar_text` | 文法 | Ngữ pháp đoạn văn | **có** |
| `reading_short` | 読解 | Đoản văn | **có** |
| `reading_medium` | 読解 | Trung văn | **có** |
| `reading_long` | 読解 | Trường văn, hiểu nội dung | **có** |
| `reading_integrated` | 読解 | Bài so sánh A và B | **có** |
| `reading_argument` | 読解 | Trường văn, hiểu luận điểm | **có** |
| `info_search` | 読解 | Tìm kiếm thông tin | **có** |
| `listen_task` | 聴解 | Hiểu đề bài | không |
| `listen_point` | 聴解 | Hiểu điểm chính | không |
| `listen_overview` | 聴解 | Hiểu bao quát | không |
| `listen_expression` | 聴解 | Diễn đạt bằng lời nói (3 đáp án) | không |
| `listen_quick` | 聴解 | Phản hồi tức thì (3 đáp án) | không |
| `listen_integrated` | 聴解 | Hiểu tổng hợp | **có** |

Một dạng chỉ áp dụng cho một số level (ví dụ N5 không có `reading_argument`). Dạng sai level sẽ báo lỗi `không áp dụng cho N...`.

Dạng nhóm thì viết trong `groups`, dạng không nhóm thì viết trong `questions` (câu nghe có `audio_url` riêng ngay trong câu).

## Audio và ảnh

Audio không nằm trong JSON. Trong JSON chỉ ghi **tên file**, và ở hộp Import JSON bạn chọn kèm các file đó. Bấm Kiểm tra thì hệ thống tải từng file lên, thay tên file bằng địa chỉ phát, rồi mới kiểm tra đề.

### Audio cả phần nghe (cách dùng cho đề thật)

Như đề JLPT thật, phần nghe là **một file chạy liền từ đầu đến cuối**, không dừng, không tua.

```json
"test": { "level": 3, "title": "JLPT N3 2023-07", "audio_url": "n3_2023_07_listening.mp3" }
```

- Khi thi thử, audio tự bắt đầu ngay khi người làm bài vào khối nghe, chạy hết một lần, không có nút dừng hay tua, và vẫn chạy tiếp khi chuyển giữa các câu. Tải lại trang thì tiếp tục từ vị trí đang nghe.
- Các câu nghe (`listen_*`) vẫn phải có `question` và `options` (có thể ghi `1`, `2`, `3`, `4`) nhưng **không cần** `audio_url` riêng.
- Sau khi nộp bài, trang xem đáp án có trình phát đầy đủ để nghe lại.
- File tối đa 200 MB.

### Luyện nghe: làm cả bộ, không chia nhỏ từng câu

Vì cả phần nghe chung một file audio nên **không thể bốc ngẫu nhiên từng câu nghe riêng lẻ**. Ở trang Luyện theo dạng bài có chế độ **Nghe cả bộ đề**: hệ thống bốc ngẫu nhiên một đề có `test.audio_url` và cho làm trọn phần nghe của đề đó với file audio đi kèm (có nút tua và nghe lại). Đề thi thử ngẫu nhiên cũng ghép phần nghe theo cách này: lấy nguyên một bộ nghe.

Hệ quả cho dữ liệu:

- Câu nghe của đề có `test.audio_url` **không** xuất hiện trong chế độ chọn theo dạng bài (vì câu lẻ không có audio riêng). Chúng chỉ dùng được ở thi thử và ở "Nghe cả bộ đề".
- Bộ nghe nào cũng cần đủ: `test.audio_url`, các câu nghe theo đúng thứ tự trong audio, và `correct` cho từng câu.
- Nếu bạn vẫn muốn luyện nghe theo từng câu lẻ, cắt audio theo từng câu và ghi `audio_url` riêng ở câu (đề không có `test.audio_url`). Khi đó câu nghe dùng được ở mọi chế độ.

### Ghi chú chung

- Tên file trong JSON và file bạn chọn phải **trùng hẳn** (không phân biệt hoa thường). Nên đổi tên file trước khi chọn (ví dụ `n1_2017_07_listening.mp3`), tránh tên dài có dấu cách và ngoặc. Nếu hai bên lệch nhau, hộp Import báo rõ tên nào lệch và không tải gì lên.
- Ảnh: `image_url` ở nhóm (xem phần Bài đọc có định dạng), hoặc `[[img:ten_file.png]]` ngay trong chữ.
- Chấp nhận: audio `mp3`, `m4a`, `ogg`, `wav` (tối đa 200 MB); ảnh `png`, `jpg`, `webp` (tối đa 5 MB). Loại file được xác định bằng nội dung thật, đổi đuôi file không qua mặt được.
- Cùng một file chọn hai lần chỉ lưu một bản (so theo nội dung).

## Hình trong câu nghe (tranh ở đề bài hoặc ở đáp án)

Các câu nghe N3 đến N5 thường có hình. Dùng `[[img:ten_file.png]]` ở `question` và/hoặc `options`. Ba kiểu hay gặp:

| Kiểu trong đề | Cách ghi |
|---|---|
| Hình trên tờ đề, đáp án là chữ (ví dụ hình văn bản có ký hiệu ア イ ウ エ, đáp án `アウ`, `アエ`...) | `question` chỉ có hình, đáp án là chữ |
| Một hình ghép 4 tranh đánh số 1 đến 4, người học chọn số | `question` chứa hình ghép, `options` là `"1"`, `"2"`, `"3"`, `"4"` |
| Mỗi đáp án là một hình riêng | mỗi phần tử của `options` là một `[[img:...]]` (khi cả 4 đáp án đều là hình, giao diện tự xếp thành lưới 2 x 2) |

```json
{ "type": "listen_task", "question": "[[img:n3_2021_12_q03.png]]", "options": ["アウ", "アエ", "イウ", "イエ"], "correct": "A" },
{ "type": "listen_task", "question": "[[img:n3_2021_12_q01.png]]", "options": ["1", "2", "3", "4"], "correct": "C" },
{ "type": "listen_expression", "question": "[[img:n3_2021_12_q10_scene.png]]\n矢印の人は何と言いますか。",
  "options": ["[[img:q10_a.png]]", "[[img:q10_b.png]]", "[[img:q10_c.png]]"], "correct": "B" }
```

Chọn kèm tất cả file ảnh ở hộp Import JSON như với audio. Ảnh tối đa 5 MB, định dạng png, jpg, webp.

### Tách hình từ file PDF đề thi

Dùng script `scripts/jlpt/extract_pdf_images.py` (cần `pip install pymupdf`):

```bash
# Tự tách các hình được nhúng trong PDF
python scripts/jlpt/extract_pdf_images.py de-n3-2021-12.pdf --out hinh-n3 --prefix n3_2021_12 --preview

# Trang nào hình bị nhúng liền với nền (script báo "không thấy") thì cắt tay theo tỉ lệ trang
python scripts/jlpt/extract_pdf_images.py de-n3-2021-12.pdf --out hinh-n3 --prefix n3_2021_12 --crop 3:0.10,0.09,0.95,0.35
```

Kết quả là các file `n3_2021_12_p01_1.png`... (p là số trang). Mở thư mục xem, đổi tên theo câu hỏi rồi ghi vào JSON. Tỉ lệ cho `--crop` là `TRANG:x0,y0,x1,y1`, mỗi số từ 0 đến 1 tính từ góc trên bên trái của trang (xem ảnh `_preview_pXX.png` để ước lượng).

## Bài đọc có định dạng (email, thông báo, tờ rơi, bảng)

Có hai cách, dùng riêng hoặc kết hợp, đều đặt ở **nhóm** nên chỉ làm một lần cho cả nhóm câu hỏi:

**Cách A, dùng ảnh:** chụp hoặc cắt nguyên tờ rơi, bảng giờ... rồi ghi `"image_url": "ten_file.png"` ở nhóm. Giữ nguyên 100% bố cục, nhưng chữ trong ảnh không tra từ được. Hợp với tờ rơi nhiều hình, màu sắc.

**Cách B, dùng markup nhẹ trong `passage`:** (chỉ là chữ, không phải HTML)

| Cú pháp | Kết quả |
|---|---|
| `<u>từ</u>` | gạch chân |
| `**từ**` | in đậm |
| `\| ô 1 \| ô 2 \|` mỗi dòng một hàng | bảng. Thêm dòng `\|---\|---\|` ngay sau hàng đầu thì hàng đầu là tiêu đề |
| `[[box]]` ... `[[/box]]` | khung viền (thông báo, tờ rơi) |
| `[[center]]` ... `[[/center]]` | căn giữa (tiêu đề) |
| `[[right]]` ... `[[/right]]` | căn phải (ngày tháng, người ký) |
| `[[img:ten_file.png]]` | chèn ảnh giữa đoạn văn |

Các thẻ lồng nhau được (ví dụ `[[box]]` chứa bảng và `[[center]]`). Xuống dòng bằng `\n` như bình thường.

Ví dụ email và thông báo:

```json
{
  "type": "info_search",
  "title": "お知らせ",
  "passage": "[[box]]\n[[center]]**夏祭りのお知らせ**[[/center]]\n| 日時 | 8月5日（土）18:00〜21:00 |\n| 場所 | 中央公園 |\n| 費用 | 無料 |\n[[right]]町内会[[/right]]\n[[/box]]",
  "questions": [
    { "question": "祭りはいつありますか。", "options": ["8月4日", "8月5日", "8月6日", "8月7日"], "correct": "B" }
  ]
}
```

## Câu trùng khi import

Hệ thống chỉ coi một câu là "đã có" khi **cùng đề** (hoặc cùng kho luyện tập khi file không có `test`), **cùng nội dung** và **cùng thứ tự xuất hiện** trong file. Hệ quả:

- Câu giống nhau nằm ở hai đề khác nhau không bị coi là trùng, đề nào cũng đủ câu.
- Nhiều câu nghe giống hệt chữ trong cùng một đề (ví dụ đều là `質問を聞いてください。` với đáp án `1 2 3 4`) vẫn là các câu riêng.
- Import lại cùng một file thì bỏ qua các câu đã có, không tạo bản sao. Câu bạn đã xóa trong Ngân hàng đề sẽ được khôi phục.
- Chế độ **Bỏ qua** giữ nguyên câu đã có. Chế độ **Ghi đè** cập nhật đáp án đúng, giải thích và audio của các câu khớp nhau theo đúng vị trí.
- Sửa chữ của một câu rồi import lại sẽ tạo thêm câu mới (vì nội dung khác), câu cũ vẫn còn. Hãy xóa câu cũ ở Ngân hàng đề.

## Chuẩn bị dữ liệu cho một đề, theo thứ tự

1. Với từng đề (ví dụ N3 tháng 12 năm 2021), gom: file PDF hoặc ảnh đề và đáp án, một file audio của cả phần nghe.
2. Nén audio nếu quá nặng (xem `docs/GOOGLE_DRIVE_SETUP.md`), đặt tên dễ nhớ như `n3_2021_12_listening.mp3`.
3. Tách hình của câu nghe bằng `extract_pdf_images.py`, đổi tên theo câu.
4. Nhờ AI chuyển đề sang JSON bằng đoạn yêu cầu ở cuối tài liệu này. Một đề nên làm thành **một file JSON** (đủ phần chữ Hán, từ vựng, ngữ pháp, đọc hiểu, nghe hiểu).
5. Vào Admin, Ngân hàng đề, Import JSON, dán JSON, chọn kèm audio và các ảnh, bấm Kiểm tra. Sửa lỗi nếu có, rồi Lưu.
6. Mở thử vài câu (nhất là câu nghe có hình và bài đọc có bảng) để chắc đáp án và hiển thị đúng.

## Ví dụ đầy đủ

```json
{
  "test": { "level": 3, "title": "JLPT N3 mẫu", "year": 2023, "month": 7, "audio_url": "n3_2023_07_listening.mp3" },
  "questions": [
    {
      "type": "kanji_reading",
      "question": "<u>試験</u>に合格しました。",
      "options": ["しけん", "じけん", "しげん", "じげん"],
      "correct": "A",
      "explanation": "試験 đọc là しけん"
    },
    {
      "type": "listen_quick",
      "question": "質問を聞いてください。",
      "options": ["1", "2", "3"],
      "correct": "B"
    }
  ],
  "groups": [
    {
      "type": "reading_short",
      "title": "Email",
      "passage": "田中さんへ\n明日の会議は10時からです。\n山田",
      "questions": [
        {
          "question": "会議は何時からですか。",
          "options": ["9時", "10時", "11時", "12時"],
          "correct": "B"
        }
      ]
    }
  ]
}
```

## Nhờ AI trích xuất đề từ ảnh hoặc PDF

Đưa AI đề thi (ảnh chụp, PDF) kèm đoạn yêu cầu sau, rồi dán kết quả vào hộp Import JSON. Với tờ rơi quá phức tạp thì bỏ qua phần bài đó ở AI và dùng ảnh (cách A).

```text
Hãy chuyển đề thi JLPT đính kèm thành MỘT file JSON hợp lệ theo đúng định dạng sau, chỉ trả về JSON.

CẤU TRÚC
- Gốc có 3 khóa: "test" (level, title, year, month, audio_url), "questions", "groups".
- "audio_url" trong "test" là tên file audio của CẢ phần nghe, theo mẫu n3_2023_07_listening.mp3 (level, năm, tháng). Câu nghe KHÔNG cần audio_url riêng, nhưng PHẢI giữ đúng thứ tự như trong đề (1番, 2番, 3番...).
- Câu đơn vào "questions". Các câu dùng chung một đoạn văn, bảng, thông báo hoặc ảnh vào "groups", mỗi nhóm có "type", "passage" (hoặc "image_url") và "questions".

MỖI CÂU
- "type", "question", "options" (3 hoặc 4 chuỗi, KHÔNG kèm số thứ tự 1 2 3 4), "correct" (chữ cái A, B, C hoặc D), "explanation" (giải thích ngắn bằng tiếng Việt).
- Từ được gạch chân trong đề thì bọc bằng <u>...</u>. Chỗ trống giữ nguyên là （　）. Câu sắp xếp có ★ giữ nguyên ★.
- KHÔNG ghi số 問題N và câu hướng dẫn, hệ thống tự thêm.
- Câu nghe: "question" ghi 質問を聞いてください。 và "options" ghi "1","2","3","4" nếu đề không in chữ. Nếu đề in sẵn đáp án thì ghi đúng chữ.
- Câu nghe có hình: nếu hình nằm ở đề bài thì viết [[img:n3_2021_12_q03.png]] vào "question" (tên theo mẫu level, năm, tháng, số câu). Nếu 4 đáp án là một hình ghép đánh số thì để "options" là "1","2","3","4" và đặt hình vào "question". Nếu mỗi đáp án là một hình riêng thì "options" là [[img:ten_a.png]], [[img:ten_b.png]]... Đáp án bằng chữ thì giữ nguyên chữ. Chỉ ghi tên file ảnh, tôi sẽ tự tách ảnh từ PDF.

"type" CHỈ DÙNG CÁC MÃ
kanji_reading, orthography, word_formation, context_word, synonym, word_usage, grammar_choice, grammar_ordering, grammar_text, reading_short, reading_medium, reading_long, reading_integrated, reading_argument, info_search, listen_task, listen_point, listen_overview, listen_expression, listen_quick, listen_integrated.
Các dạng nhóm (grammar_text, reading_*, info_search, listen_integrated) bắt buộc đặt trong "groups".

ĐỊNH DẠNG BÀI ĐỌC (trong "passage", chỉ dùng các ký hiệu này, KHÔNG dùng HTML hay Markdown khác)
- **chữ** để in đậm, <u>chữ</u> để gạch chân.
- Bảng: mỗi hàng một dòng dạng | ô 1 | ô 2 |. Nếu hàng đầu là tiêu đề thì thêm dòng |---|---| ngay sau nó.
- [[box]] ... [[/box]] cho phần có khung viền (thông báo, tờ rơi, quảng cáo).
- [[center]] ... [[/center]] cho tiêu đề căn giữa, [[right]] ... [[/right]] cho ngày tháng, tên người gửi hoặc người ký ở bên phải.
- Có thể lồng các thẻ vào nhau. Xuống dòng bằng \n.
- Email: ghi đủ các dòng 宛先, 件名, nội dung, người gửi như bản gốc, mỗi dòng một dòng.
- Nếu một bài có hình ảnh, biểu đồ hoặc bố cục quá phức tạp không thể diễn tả bằng các ký hiệu trên, hãy ĐỂ "passage" là "[ẢNH]" và ghi "image_url" theo mẫu n3_2023_07_reading_q45.png, tôi sẽ tự gắn ảnh.

ĐÁP ÁN
- Nếu đề không có đáp án kèm theo, hãy tự giải và ghi "correct" cẩn thận, ghi chú những câu chưa chắc chắn vào "explanation".
```

Nên mở thử vài câu sau khi import để chắc đáp án đúng và định dạng bài đọc hiển thị đẹp, vì AI có thể sai khi tự giải hoặc khi dựng bảng.
