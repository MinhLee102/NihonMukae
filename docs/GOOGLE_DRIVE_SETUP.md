# Lưu audio đề thi trên Google Drive của bạn

Mặc định audio và ảnh lưu trên ổ đĩa VPS (`APP_MEDIA_PROVIDER=local`). Nếu muốn dùng dung lượng Google Drive (ví dụ 5 TB), làm theo tài liệu này. Việc làm **một lần**, mất khoảng 15 phút.

## Cách hoạt động

- Admin upload file ở trang quản trị. Backend nhận file, kiểm tra rồi ghi lên Drive **dưới tài khoản Google của bạn** qua một refresh token.
- Người học nghe qua địa chỉ `/media/<id>` của backend. Backend lấy file từ Drive và phát lại (có hỗ trợ tua). File trên Drive **không** cần chia sẻ công khai, nên không ai mở được link Drive trực tiếp.
- Backend chỉ có quyền `drive.file`: thấy và sửa **những file do chính app tạo**, không đọc được phần còn lại của Drive. App tự tạo thư mục `NihonMukae media` ở Drive của bạn.

## Cần gì

- Một tài khoản Google cá nhân (gmail) có dung lượng Drive. Nếu là tài khoản trường (Google Workspace), quản trị viên có thể chặn app bên ngoài hoặc xóa refresh token, và nếu tài khoản bị đóng khi bạn ra trường thì mất cả audio. Nên dùng gmail cá nhân.
- Chi phí: miễn phí.

## Bước 1. Tạo OAuth client

1. Vào https://console.cloud.google.com, tạo project mới (ví dụ `nihonmukae`).
2. **APIs & Services**, **Library**, tìm **Google Drive API**, bấm **Enable**.
3. **APIs & Services**, **OAuth consent screen** (hoặc Google Auth Platform):
   - User type: **External**.
   - Điền tên app, email hỗ trợ, email liên hệ.
   - Scopes: thêm `https://www.googleapis.com/auth/drive.file`.
   - **Publishing status: bấm Publish app để chuyển sang In production.** Nếu để Testing, refresh token **hết hạn sau 7 ngày** và audio sẽ ngừng phát.
   - Khi ở In production mà chưa xác minh, lúc cấp quyền Google hiện cảnh báo "chưa xác minh". Với app chỉ dùng cho chính bạn, bấm **Advanced** rồi **Go to ... (unsafe)** là được, không cần xác minh.
4. **Credentials**, **Create credentials**, **OAuth client ID**:
   - Application type: **Web application**.
   - Authorized redirect URIs: thêm `https://developers.google.com/oauthplayground`.
   - Lưu lại **Client ID** và **Client secret**.

## Bước 2. Lấy refresh token

1. Mở https://developers.google.com/oauthplayground.
2. Bấm biểu tượng bánh răng ở góc phải, tick **Use your own OAuth credentials**, dán Client ID và Client secret.
3. Ở khung bên trái, ô nhập scope: gõ `https://www.googleapis.com/auth/drive.file`, bấm **Authorize APIs**, đăng nhập bằng tài khoản Google chứa Drive và đồng ý.
4. Bấm **Exchange authorization code for tokens**. Sao chép giá trị **Refresh token**.

## Bước 3. Cấu hình backend

Trong `.env` trên VPS (xem `deploy/.env.prod.example`):

```bash
APP_MEDIA_PROVIDER=drive
GOOGLE_DRIVE_CLIENT_ID=<client id>
GOOGLE_DRIVE_CLIENT_SECRET=<client secret>
GOOGLE_DRIVE_REFRESH_TOKEN=<refresh token>
# Không bắt buộc: id thư mục Drive có sẵn. Để trống thì app tự tạo "NihonMukae media".
GOOGLE_DRIVE_FOLDER_ID=
```

Áp dụng: `dc up -d backend`. Trang **Trạng thái server** của admin sẽ ghi "Nơi lưu media: Google Drive". Upload thử một file mp3 nhỏ ở Ngân hàng đề, rồi kiểm tra Drive có thư mục `NihonMukae media` chứa file tên dạng UUID.

Không commit các giá trị này vào git. Chúng chỉ nằm trong `.env` của VPS.

## Lưu ý

- Đổi `APP_MEDIA_PROVIDER` giữa `local` và `drive` thì các file đã upload ở nơi cũ **không phát được nữa** (backend chỉ phát file ở nơi lưu đang bật). Chọn một nơi từ đầu, hoặc upload lại.
- Nếu bạn xóa file trong thư mục app tạo, hoặc thu hồi quyền ở https://myaccount.google.com/permissions, audio sẽ lỗi. Đừng đổi tên hay xóa thư mục đó.
- Giới hạn Drive: có hạn mức tải xuống mỗi ngày cho một file được tải rất nhiều lần, nhưng audio đề thi nhỏ nên hiếm khi chạm. Trình duyệt cũng cache audio một năm (địa chỉ không đổi).
- Chưa có kiểm thử với Google thật trong repo, chỉ có kiểm thử với máy chủ giả. Hãy làm bước "upload thử" ở trên ngay sau khi cấu hình.
