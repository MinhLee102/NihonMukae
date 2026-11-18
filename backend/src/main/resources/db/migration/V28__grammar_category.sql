-- Nhóm ý nghĩa của mẫu ngữ pháp (ví dụ 時間関係, 逆接条件), dùng để gom danh sách trên web.
ALTER TABLE grammar_points ADD COLUMN category VARCHAR(60);
CREATE INDEX idx_grammar_points_category ON grammar_points(level, category);
