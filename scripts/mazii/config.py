import os
from pathlib import Path
from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent.parent

load_dotenv(BASE_DIR / ".env")
load_dotenv(Path(__file__).resolve().parent / ".env")
 
# Có thể override qua biến môi trường hoặc tham số dòng lệnh --db-url
DB_URL = os.environ.get("MAZII_DB_URL")
 
# Mặc định trỏ về thư mục data/ ở mazii/data
DATA_DIR = os.environ.get("MAZII_DATA_DIR")
 
# Số dòng đọc mỗi lần từ CSV (tránh load 20MB vào RAM cùng lúc)
CHUNK_SIZE = 5000