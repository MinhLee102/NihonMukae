@echo off
REM dev-up.bat — chạy từ thư mục root repo
REM Bật toàn bộ container (Postgres, backend, frontend...) rồi tự đợi Flyway
REM tạo schema xong và seed lại dữ liệu Mazii nếu bảng `words` đang rỗng
REM (vd sau khi bạn xoá Docker/WSL để dọn ổ đĩa).
 
setlocal
 
echo == Bat toan bo container (docker compose up -d) ==
docker compose up --build -d
if errorlevel 1 (
    echo Loi khi chay docker compose. Dung lai.
    exit /b 1
)
 
echo == Doi Flyway tao schema xong + seed lai du lieu neu can ==
call scripts\mazii\venv\Scripts\activate.bat
python scripts\mazii\ensure_seeded.py
set SEED_RESULT=%errorlevel%
call deactivate
 
if %SEED_RESULT% neq 0 (
    echo.
    echo Seed du lieu chua thanh cong. Xem docker compose logs -f backend de biet
    echo Flyway co loi gi khong.
    exit /b 1
)
 
echo == San sang ==
endlocal