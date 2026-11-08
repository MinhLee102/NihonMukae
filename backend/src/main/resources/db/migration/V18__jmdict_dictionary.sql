-- Từ điển mới dựng từ JMdict (CC BY-SA 4.0) + nghĩa tiếng Việt và câu ví dụ do Gemini viết.
-- Đặt song song với bảng words/word_meanings của Mazii; web vẫn tra bảng cũ cho tới khi chuyển hẳn.
-- Dữ liệu nạp bằng scripts/jmdict/vn/import_dict.py.

CREATE TABLE dict_entries (
    id             BIGINT PRIMARY KEY,           -- ent_seq của JMdict
    word           TEXT,                         -- cách viết kanji chính, NULL với từ thuần kana
    reading        TEXT        NOT NULL,
    headword       TEXT        NOT NULL,         -- mặt chữ hiển thị (kana nếu từ thường viết bằng kana)
    other_forms    TEXT[]      NOT NULL DEFAULT '{}',
    other_readings TEXT[]      NOT NULL DEFAULT '{}',
    usually_kana   BOOLEAN     NOT NULL DEFAULT FALSE,
    pos            TEXT,
    jlpt_level     SMALLINT,
    freq_rank      INTEGER,                      -- nhỏ hơn = phổ biến hơn, NULL = JMdict không đánh dấu
    priority_group TEXT        NOT NULL,         -- core | n2 | n1 | common
    han_viet       TEXT,
    gloss_en       JSONB       NOT NULL DEFAULT '[]',
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dict_entries_word ON dict_entries (word);
CREATE INDEX idx_dict_entries_reading ON dict_entries (reading);
CREATE INDEX idx_dict_entries_other_forms ON dict_entries USING GIN (other_forms);

CREATE TABLE dict_meanings (
    entry_id BIGINT   NOT NULL REFERENCES dict_entries (id) ON DELETE CASCADE,
    idx      SMALLINT NOT NULL,
    meaning  TEXT     NOT NULL,
    PRIMARY KEY (entry_id, idx)
);

CREATE TABLE dict_examples (
    entry_id BIGINT   NOT NULL REFERENCES dict_entries (id) ON DELETE CASCADE,
    idx      SMALLINT NOT NULL,
    ja       TEXT     NOT NULL,                  -- chữ thường, furigana do kuromoji gắn khi hiển thị
    vi       TEXT     NOT NULL,
    PRIMARY KEY (entry_id, idx)
);

CREATE TABLE dict_kanji (
    kanji    TEXT PRIMARY KEY,
    han_viet TEXT[] NOT NULL DEFAULT '{}',
    meaning  TEXT
);
