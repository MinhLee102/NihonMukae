-- Một mẫu ngữ pháp có thể thuộc nhiều cấp: level là cấp chính (thấp nhất), extra_levels là các cấp khác cũng hiện mẫu này.
ALTER TABLE grammar_points ADD COLUMN extra_levels SMALLINT[] NOT NULL DEFAULT '{}';
CREATE INDEX idx_grammar_points_extra_levels ON grammar_points USING GIN (extra_levels);

-- Cặp mẫu trùng tên ở các cấp nhưng khác nghĩa, admin đã xác nhận giữ riêng (không gợi ý gộp nữa).
-- Luôn lưu a < b.
CREATE TABLE grammar_distinct (
    a BIGINT NOT NULL REFERENCES grammar_points (id) ON DELETE CASCADE,
    b BIGINT NOT NULL REFERENCES grammar_points (id) ON DELETE CASCADE,
    PRIMARY KEY (a, b),
    CHECK (a < b)
);
