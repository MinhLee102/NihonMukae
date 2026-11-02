-- Audio của cả phần nghe (một file chạy liền từ đầu đến cuối), gắn vào đề thi
ALTER TABLE jlpt_tests ADD COLUMN listening_audio_url VARCHAR(500);
