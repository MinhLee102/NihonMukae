import sys
import time
 
import psycopg2
 
import config
from import_mazii_csv import run_pipeline
 
DB_WAIT_RETRIES = 30
DB_WAIT_DELAY_SECONDS = 2
 
# Backend cần thời gian boot Spring Boot + chạy Flyway trước khi bảng `words`
# xuất hiện -> đợi lâu hơn (tổng ~5 phút) so với việc chỉ đợi kết nối DB.
SCHEMA_WAIT_RETRIES = 60
SCHEMA_WAIT_DELAY_SECONDS = 5
 
 
def wait_for_db(db_url):
    for attempt in range(1, DB_WAIT_RETRIES + 1):
        try:
            conn = psycopg2.connect(db_url)
            conn.close()
            return True
        except psycopg2.OperationalError:
            print(f"  DB chưa sẵn sàng, thử lại ({attempt}/{DB_WAIT_RETRIES})...")
            time.sleep(DB_WAIT_DELAY_SECONDS)
    return False
 
 
def words_table_exists(db_url):
    conn = psycopg2.connect(db_url)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT to_regclass('public.words')")
            return cur.fetchone()[0] is not None
    finally:
        conn.close()
 
 
def wait_for_schema(db_url):
    """Đợi backend/Flyway tạo xong bảng `words`."""
    for attempt in range(1, SCHEMA_WAIT_RETRIES + 1):
        if words_table_exists(db_url):
            return True
        print(f"  Bảng 'words' chưa có — backend/Flyway có thể đang chạy, "
              f"đợi thêm ({attempt}/{SCHEMA_WAIT_RETRIES})...")
        time.sleep(SCHEMA_WAIT_DELAY_SECONDS)
    return False
 
 
def words_count(db_url):
    conn = psycopg2.connect(db_url)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT count(*) FROM words")
            return cur.fetchone()[0]
    finally:
        conn.close()
 
 
def main():
    db_url = config.DB_URL
 
    print("== Kiểm tra kết nối Postgres ==")
    if not wait_for_db(db_url):
        print("Không kết nối được DB sau nhiều lần thử. Kiểm tra container Postgres "
              "đã chạy chưa (docker compose up -d).")
        sys.exit(1)
 
    print("== Đợi backend/Flyway tạo xong schema (nếu cần) ==")
    if not wait_for_schema(db_url):
        print("Bảng 'words' vẫn chưa xuất hiện sau nhiều lần thử. Kiểm tra log "
              "container backend (docker compose logs -f backend) xem Flyway có "
              "lỗi gì không.")
        sys.exit(1)
 
    count = words_count(db_url)
    if count > 0:
        print(f"Bảng 'words' đã có {count} dòng — bỏ qua, không cần import lại.")
        return
 
    print("Bảng 'words' đang rỗng — bắt đầu import lại dữ liệu Mazii từ CSV...")
    run_pipeline(db_url=db_url, data_dir=config.DATA_DIR, chunk_size=config.CHUNK_SIZE)
 
 
if __name__ == "__main__":
    main()