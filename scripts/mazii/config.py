import os

from dotenv import load_dotenv


load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))
 
# Có thể override qua biến môi trường hoặc tham số dòng lệnh --db-url
DB_URL = os.environ.get(
    "MAZII_DB_URL"
)
 
# Thư mục chứa 4 file CSV gốc (KHÔNG commit vào repo — đã gitignore)
DATA_DIR = os.environ.get("MAZII_DATA_DIR", os.path.join(os.path.dirname(__file__), "data"))
 
# Số dòng đọc mỗi lần từ CSV (tránh load 20MB vào RAM cùng lúc)
CHUNK_SIZE = 5000