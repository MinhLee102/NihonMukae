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
| `level` | nên có | 1 đến 5 (N1 đến N5). Là level mặc định cho mọi câu trong file |
| `title` | nên có | Ví dụ `JLPT N3 2023-07` |
| `year`, `month` | không | Tháng 1 đến 12 |
| `source_note` | không | Ghi chú nguồn |

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

Audio của đề nghe không nằm trong JSON. Quy trình:

1. Trong JSON, ghi **tên file** ở `audio_url` hoặc `image_url`, ví dụ `"audio_url": "n3_2023_07_q01.mp3"`.
2. Ở hộp Import JSON, bấm **Chọn file audio và ảnh** và chọn đúng các file đó.
3. Bấm Kiểm tra. Hệ thống tải từng file lên, thay tên file bằng địa chỉ phát, rồi mới kiểm tra đề.

Ghi chú:

- Tên file so khớp không phân biệt hoa thường, và phải trùng hẳn (`q01.mp3` khác `q01.MP3.bak`).
- Ảnh đặt trong đoạn văn hoặc câu hỏi: viết `[[img:ten_file.png]]` ngay trong chữ.
- Chấp nhận: audio `mp3`, `m4a`, `ogg`, `wav` (tối đa 60 MB); ảnh `png`, `jpg`, `webp` (tối đa 5 MB). Loại file được xác định bằng nội dung thật, đổi đuôi file không qua mặt được.
- Cùng một file chọn hai lần chỉ lưu một bản (so theo nội dung).
- Với câu nghe mà đề in sẵn không có chữ, vẫn phải có `question` (có thể ghi bản chép lời hoặc `質問を聞いてください`) và các `options` (có thể ghi `1`, `2`, `3`, `4`).
- Một file audio lớn ghép cả phần nghe thì cắt thành từng câu trước khi upload, vì mỗi câu cần audio riêng.

Cách phát: khi thi thử, audio tự phát đúng một lần, không tua, không nghe lại. Khi luyện tập và xem lại kết quả, có đủ nút điều khiển.

## Ví dụ đầy đủ

```json
{
  "test": { "level": 3, "title": "JLPT N3 mẫu", "year": 2023, "month": 7 },
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
      "correct": "B",
      "audio_url": "n3_listen_quick_01.mp3"
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

Đưa AI đề thi (ảnh chụp, PDF) kèm đoạn yêu cầu sau, rồi dán kết quả vào hộp Import JSON:

```text
Hãy chuyển đề thi JLPT đính kèm thành MỘT file JSON hợp lệ theo đúng định dạng sau, chỉ trả về JSON.

- Gốc có 3 khóa: "test" (level, title, year, month), "questions", "groups".
- Câu đơn vào "questions". Câu dùng chung một đoạn văn, audio hoặc ảnh vào "groups", mỗi nhóm có "type", "passage" (hoặc "audio_url", "image_url") và "questions".
- Mỗi câu có: "type", "question", "options" (3 hoặc 4 chuỗi, KHÔNG kèm số thứ tự 1 2 3 4), "correct" (chữ cái A, B, C hoặc D), "explanation" (giải thích ngắn bằng tiếng Việt).
- Từ được gạch chân trong đề thì bọc bằng <u>...</u>. Chỗ trống giữ nguyên là （　）. Câu sắp xếp có ★ giữ nguyên ★.
- KHÔNG ghi số 問題N và câu hướng dẫn, hệ thống tự thêm.
- "type" chỉ được dùng các mã: kanji_reading, orthography, word_formation, context_word, synonym, word_usage, grammar_choice, grammar_ordering, grammar_text, reading_short, reading_medium, reading_long, reading_integrated, reading_argument, info_search, listen_task, listen_point, listen_overview, listen_expression, listen_quick, listen_integrated.
- Các dạng nhóm (grammar_text, reading_*, info_search, listen_integrated) bắt buộc đặt trong "groups".
- Câu nghe: "audio_url" là tên file theo mẫu n3_2023_07_q01.mp3 (level, năm, tháng, số câu), "options" ghi "1","2","3","4" nếu đề không in chữ.
- Nếu đề không có đáp án kèm theo, hãy tự giải và ghi "correct" cẩn thận, ghi chú những câu chưa chắc chắn vào "explanation".
```

Nên mở thử vài câu sau khi import để chắc đáp án đúng, vì AI có thể sai khi tự giải.
