-- Soạn ngữ pháp ngay trên trang Admin: lưu danh sách tên mẫu trước, nội dung (nghĩa, giải thích, ví dụ) điền sau.
-- Mẫu chưa có nội dung có meaning NULL; trang học chỉ hiện mẫu đã có nội dung.
ALTER TABLE grammar_points ALTER COLUMN meaning DROP NOT NULL;
ALTER TABLE grammar_points ALTER COLUMN explanation DROP NOT NULL;
-- Gợi ý cách nối (từ file tham khảo), chỉ để đưa vào prompt
ALTER TABLE grammar_points ADD COLUMN hint VARCHAR(300);
-- AI đánh dấu mẫu không chắc hoặc không có thật: không đưa vào batch nữa
ALTER TABLE grammar_points ADD COLUMN invalid BOOLEAN NOT NULL DEFAULT false;
