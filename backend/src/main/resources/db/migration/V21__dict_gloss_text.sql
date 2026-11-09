-- Toàn bộ JMdict (~218 nghìn mục) được nạp vào dict_entries, phần lớn chỉ có nghĩa tiếng Anh.
-- gloss_text: nghĩa tiếng Anh dạng chuỗi để tìm ILIKE bằng chỉ mục trigram (ép jsonb sang text mỗi lần quá chậm).
ALTER TABLE dict_entries ADD COLUMN gloss_text TEXT NOT NULL DEFAULT '';
UPDATE dict_entries SET gloss_text = gloss_en::text;
CREATE INDEX idx_dict_entries_gloss_trgm ON dict_entries USING GIN (gloss_text gin_trgm_ops);
CREATE INDEX idx_dict_entries_group ON dict_entries (priority_group);
