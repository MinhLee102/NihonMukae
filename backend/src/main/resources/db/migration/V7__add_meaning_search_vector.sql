ALTER TABLE word_meanings ADD COLUMN meaning_search_vector TSVECTOR;

UPDATE word_meanings SET meaning_search_vector = to_tsvector('simple', meaning);

CREATE INDEX idx_word_meanings_search ON word_meanings USING GIN(meaning_search_vector);

-- Trigger tự cập nhật meaning_search_vector mỗi khi insert/update meaning —
-- cần thiết vì import_mazii_csv.py sẽ còn chạy lại (idempotent re-import),
-- và sau này có thể có tính năng sửa nghĩa thủ công.
CREATE OR REPLACE FUNCTION word_meanings_search_vector_trigger() RETURNS trigger AS $$
BEGIN
  NEW.meaning_search_vector := to_tsvector('simple', coalesce(NEW.meaning, ''));
  RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_word_meanings_search_vector
  BEFORE INSERT OR UPDATE OF meaning ON word_meanings
  FOR EACH ROW EXECUTE FUNCTION word_meanings_search_vector_trigger();