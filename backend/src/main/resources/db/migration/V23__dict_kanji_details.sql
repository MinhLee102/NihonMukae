-- Tab Hán tự: thông tin chữ kanji từ KANJIDIC2 (EDRDG, CC BY-SA 4.0) và thứ tự nét từ KanjiVG (CC BY-SA 3.0).
-- han_viet và meaning (tiếng Việt) giữ nguyên từ pipeline của mình; chữ nào chưa có Hán Việt thì lấy từ KANJIDIC2.
-- Nạp bằng scripts/jmdict/vn/import_kanji.py.
ALTER TABLE dict_kanji
    ADD COLUMN on_yomi      TEXT[]   NOT NULL DEFAULT '{}',
    ADD COLUMN kun_yomi     TEXT[]   NOT NULL DEFAULT '{}',
    ADD COLUMN meanings_en  TEXT[]   NOT NULL DEFAULT '{}',
    ADD COLUMN stroke_count SMALLINT,
    ADD COLUMN grade        SMALLINT,              -- lớp học ở Nhật: 1-6 kyōiku, 8 jōyō còn lại, 9-10 jinmeiyō
    ADD COLUMN frequency    SMALLINT,              -- thứ hạng thường dùng (1 = phổ biến nhất), NULL = ngoài 2.500 chữ đầu
    ADD COLUMN radical      SMALLINT,              -- số bộ thủ Khang Hy (1-214)
    ADD COLUMN stroke_paths TEXT[]   NOT NULL DEFAULT '{}';  -- thuộc tính d của từng nét theo thứ tự viết, hệ toạ độ 109x109
