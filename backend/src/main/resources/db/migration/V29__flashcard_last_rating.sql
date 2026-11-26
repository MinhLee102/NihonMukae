-- Lần bấm gần nhất của mỗi thẻ (hard, normal, easy): dùng để ưu tiên thẻ hay quên khi chọn thẻ ôn.
-- Thẻ đã ôn trước migration này để NULL, khi đó ưu tiên được đoán từ hệ số SM-2.
ALTER TABLE flashcard_reviews ADD COLUMN last_rating VARCHAR(10);
