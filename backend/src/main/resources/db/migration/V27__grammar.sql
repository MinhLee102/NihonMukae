-- Ngữ pháp: danh sách mẫu câu theo cấp độ, giải thích và ví dụ do AI viết (không sao chép từ sách).
-- Nạp bằng scripts/grammar/import_grammar.py. Tab Ngữ pháp trên web đọc bảng này khi được mở khoá.
CREATE TABLE grammar_points (
    id           BIGSERIAL    PRIMARY KEY,
    level        SMALLINT     NOT NULL CHECK (level BETWEEN 1 AND 5),
    pattern      VARCHAR(100) NOT NULL,
    reading      VARCHAR(100),
    -- ["Vない(bỏ ない) + ざるを得ない", ...]
    connection   JSONB        NOT NULL DEFAULT '[]',
    meaning      TEXT         NOT NULL,
    explanation  TEXT         NOT NULL,
    notes        TEXT,
    -- [{"pattern": "...", "diff": "..."}]: mẫu dễ nhầm
    related      JSONB        NOT NULL DEFAULT '[]',
    sort_order   INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_grammar_level_pattern UNIQUE (level, pattern)
);

CREATE INDEX idx_grammar_points_level ON grammar_points(level, sort_order);

CREATE TABLE grammar_examples (
    id        BIGSERIAL PRIMARY KEY,
    point_id  BIGINT   NOT NULL REFERENCES grammar_points(id) ON DELETE CASCADE,
    idx       SMALLINT NOT NULL,
    ja        TEXT     NOT NULL,
    vi        TEXT     NOT NULL,
    CONSTRAINT uq_grammar_example UNIQUE (point_id, idx)
);
