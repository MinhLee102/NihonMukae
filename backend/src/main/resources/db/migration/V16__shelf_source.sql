-- Bài My Shelf có thể được lưu từ lịch sử luyện thi (bản sao độc lập của snapshot)
ALTER TABLE shelf_items ADD COLUMN source_type VARCHAR(12) NOT NULL DEFAULT 'manual';   -- manual | practice
ALTER TABLE shelf_items ADD COLUMN source_session_id UUID REFERENCES practice_sessions(id) ON DELETE SET NULL;
