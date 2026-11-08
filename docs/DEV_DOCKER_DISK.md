# Giữ Docker dev không ăn ổ C

Docker Desktop trên Windows lưu mọi image, container, volume và build cache trong một file
`%LOCALAPPDATA%\Docker\wsl\disk\docker_data.vhdx`. File này **chỉ phình ra, không tự nhỏ lại** khi bạn
xoá image, nên dọn trong Docker xong mà ổ C vẫn đầy là bình thường.

## Đã làm sẵn trong repo

- `backend/.dockerignore`, `frontend/.dockerignore`: không gửi `target/`, `node_modules/`, `.next/` vào
  build. Trước đây mỗi lần build frontend copy cả `node_modules` (hàng trăm MB) vào build cache.
- `docker-compose.yml`: log mỗi container tối đa 3 file × 10 MB.
- `dev-up.bat noseed`: bật container mà không nạp từ điển.
- `dev-clean.bat`: dọn image cũ, volume vô danh, build cache cũ (không đụng dữ liệu Postgres và media).
  `dev-clean.bat all` dọn sạch build cache.

## Nên chỉnh một lần trong Docker Desktop

1. **Chuyển file ổ đĩa sang ổ D** (hiệu quả nhất): Settings → Resources → Advanced →
   Disk image location → chọn thư mục trên D:, Apply & restart. Docker tự chuyển dữ liệu.
2. **Giới hạn kích thước ổ đĩa**: cùng trang, kéo *Virtual disk limit* xuống khoảng 32–48 GB.
3. **Giảm build cache giữ lại**: Settings → Docker Engine, đổi `"defaultKeepStorage": "20GB"` thành
   `"5GB"`, Apply & restart.

## Thu nhỏ file vhdx sau khi dọn

Chạy `dev-clean.bat`, rồi thoát hẳn Docker Desktop (Quit), mở PowerShell **quyền Administrator**:

```powershell
wsl --shutdown
diskpart
```

Trong diskpart:

```
select vdisk file="C:\Users\<tên>\AppData\Local\Docker\wsl\disk\docker_data.vhdx"
attach vdisk readonly
compact vdisk
detach vdisk
exit
```

Bước `attach vdisk readonly` là bắt buộc, thiếu nó `compact` chạy xong mà file không nhỏ đi.
