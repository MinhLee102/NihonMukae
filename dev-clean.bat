@echo off
REM dev-clean.bat — dọn những thứ Docker tích lại sau nhiều lần build, KHÔNG xoá dữ liệu Postgres/media
REM (volume có tên). Chạy khi ổ C đầy dần lên.
REM   dev-clean.bat       dọn image cũ, volume vô danh, build cache cũ hơn 3 ngày
REM   dev-clean.bat all   dọn sạch build cache (lần build sau sẽ chậm hơn)

setlocal

echo == Image khong con dung (dangling) ==
docker image prune -f

echo == Volume vo danh khong con container nao dung (node_modules, .next cu) ==
docker volume prune -f

if /i "%~1"=="all" (
    echo == Toan bo build cache ==
    docker builder prune -af
) else (
    echo == Build cache cu hon 3 ngay ==
    docker builder prune -f --filter until=72h
)

echo.
docker system df
echo.
echo Luu y: file docker_data.vhdx cua WSL khong tu nho lai sau khi don.
echo Neu dung luong o C chua giam, xem huong dan thu gon trong docs\DEV_DOCKER_DISK.md
endlocal
