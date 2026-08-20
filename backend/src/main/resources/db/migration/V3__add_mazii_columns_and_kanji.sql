ALTER TABLE words
  ADD COLUMN mazii_vocab_id INTEGER UNIQUE,
  ADD COLUMN han_viet       VARCHAR(255),
  ADD COLUMN pronunciation  VARCHAR(255),
  ADD COLUMN pos            VARCHAR(50);

-- 2. Bổ sung cột cho word_meanings: map ngược về meaning_details.csv
--    Cần cột này để examples.csv (FK: meaning_detail_id) biết insert vào
--    example_sentences ứng với word_meanings nào.
ALTER TABLE word_meanings
  ADD COLUMN mazii_meaning_id INTEGER UNIQUE;

-- 3. Bảng kanji riêng (tương đương KanjiDic2), độc lập với words/word_meanings.
--    Sau này khi có tính năng "1 từ -> các kanji cấu thành" sẽ thêm bảng
--    junction (vd: word_kanji(word_id, kanji_id)) nối words <-> kanji.
--    Chưa làm trong migration này vì chưa cần ngay.
CREATE TABLE kanji (
  id             BIGSERIAL PRIMARY KEY,
  mazii_kanji_id INTEGER UNIQUE,
  word           VARCHAR(10) NOT NULL,   -- ký tự kanji, vd "日"
  kunyomi        TEXT,
  onyomi         TEXT,
  strokes        SMALLINT,
  jlpt_level     SMALLINT,               -- 1=N1 ... 5=N5, null nếu không rõ
  meaning        TEXT,
  explain        TEXT,
  created_at     TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX idx_kanji_word ON kanji(word);