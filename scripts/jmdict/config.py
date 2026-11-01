import os
 
from dotenv import load_dotenv
 
load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))
 
DB_URL = os.environ.get(
    "MAZII_DB_URL",  # dùng chung biến env với scripts/mazii cho tiện, cùng 1 DB
    "postgresql://dev:dev@localhost:5432/nihon_mukae",
)