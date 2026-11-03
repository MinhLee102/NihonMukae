-- Đề cung cấp audio cả phần nghe cho phiên (luyện nguyên bộ nghe, hoặc đề thi thử ngẫu nhiên ghép bộ nghe)
ALTER TABLE practice_sessions ADD COLUMN listening_test_id BIGINT REFERENCES jlpt_tests(id) ON DELETE SET NULL;
