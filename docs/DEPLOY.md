# Hướng dẫn deploy NihonMukae (CI/CD)

Tài liệu này dẫn từ máy trắng đến khi app chạy trên internet và tự deploy mỗi lần push lên `main`.

## 1. Kiến trúc và lý do chọn

```
Trình duyệt ──► Vercel (frontend Next.js)
     │
     └────────► api.<tên-miền> ──► Caddy (HTTPS tự động) ──► backend Spring Boot ──► Postgres, Redis
                                   └──────────────── 1 VPS, chạy bằng Docker Compose ───────────────┘

GitHub Actions: test ──► build image ──► đẩy lên ghcr.io ──► SSH vào VPS ──► deploy.sh
```

**Frontend: chọn Vercel, không dùng GitHub Pages.**

- GitHub Pages chỉ phục vụ file tĩnh. App này có các route động (`/flashcard/[deckId]`, `/shelf/[id]`, `/jlpt/session/[id]`...) mà `next build` đánh dấu là chạy trên server (ký hiệu `ƒ`). Muốn đưa lên Pages phải xuất tĩnh (`output: 'export'`), mà route động với id chỉ biết lúc chạy thì không xuất tĩnh được.
- Pages cũng không có biến môi trường theo môi trường, không có bản xem thử cho từng pull request, và URL nằm dưới `/<tên-repo>/` nên phải sửa đường dẫn.
- Vercel (gói Hobby, miễn phí) hiểu Next.js sẵn, tự build mỗi lần push, tự tạo preview cho mỗi PR, nên **không cần viết workflow cho frontend**.

**Backend không chạy được trên Vercel hay GitHub Pages**, vì cần Postgres, Redis và một tiến trình Java chạy liên tục. Cần một VPS.

## 2. Ưu đãi GitHub Student Pack dùng được

Theo [trang chính thức của GitHub Education](https://education.github.com/pack) (ưu đãi thay đổi theo thời gian, hãy kiểm tra lại trên trang đó):

| Nhu cầu | Ưu đãi | Ghi chú |
| --- | --- | --- |
| VPS | **DigitalOcean**: 200 USD credit trong 1 năm | Khuyên dùng. Tạo Droplet Ubuntu, 2 GB RAM là thoải mái |
| VPS | **Azure for Students**: 100 USD credit | Dành cho sinh viên từ 18 tuổi, không cần thẻ |
| Tên miền | **Namecheap**: 1 năm tên miền `.me` (ngoài ra có Name.com, .TECH) | Cần tên miền để có HTTPS cho backend |

Cần tên miền vì: (1) Caddy lấy chứng chỉ HTTPS miễn phí chỉ cho tên miền, (2) cookie đăng nhập hoạt động ổn khi `app.<miền>` (Vercel) và `api.<miền>` (VPS) cùng một site. Chưa có tên miền thì xem mục 9.

## 3. Những gì đã có trong repo

| File | Vai trò |
| --- | --- |
| `backend/Dockerfile` | Build image backend (cache dependency, chạy bằng user thường, có healthcheck) |
| `docker-compose.prod.yml` | Stack production: Caddy, backend, Postgres, Redis. Chỉ Caddy mở cổng 80 và 443 |
| `deploy/Caddyfile` | Reverse proxy và HTTPS tự động |
| `deploy/.env.prod.example` | Mẫu file `.env` trên VPS |
| `deploy/setup-vps.sh` | Cài Docker, tường lửa, swap, thư mục app (chạy 1 lần) |
| `deploy/deploy.sh` | Chạy trên VPS: kéo image mới, khởi động lại, đợi healthy |
| `deploy/seed-data.sh` | Nạp từ điển Mazii và JMdict vào DB production (chạy 1 lần) |
| `.github/workflows/ci.yml` | Mỗi PR và mỗi push ngoài `main`: test backend, lint, type check, build frontend, build thử Docker |
| `.github/workflows/deploy-backend.yml` | Push lên `main`: test, build, đẩy ghcr.io, deploy lên VPS |

RabbitMQ **không** nằm trong stack production vì app không còn dùng (furigana đã parse đồng bộ). Cổng 8080 của backend không mở ra ngoài, chỉ Caddy gọi tới.

## 4. Việc làm một lần

### 4.1 Tạo VPS

1. Xác minh GitHub Student Pack, nhận credit DigitalOcean theo trang Student Pack.
2. Tạo Droplet: Ubuntu 24.04, 2 GB RAM trở lên (1 GB chạy được nhờ swap nhưng sát), chọn đăng nhập bằng SSH key.
3. Tạo SSH key **riêng cho CI** trên máy bạn:

   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/nihongo_deploy -C "github-actions-deploy" -N ""
   ```

   - Nội dung `~/.ssh/nihongo_deploy.pub` dán vào `~/.ssh/authorized_keys` của user trên VPS.
   - Nội dung file private `~/.ssh/nihongo_deploy` (không có đuôi `.pub`) sẽ dán vào GitHub Secret ở bước 4.4.

### 4.2 Trỏ tên miền

Ở trang quản lý tên miền, tạo bản ghi **A**: `api` trỏ tới IP của VPS. Đợi vài phút rồi kiểm tra:

```bash
nslookup api.<tên-miền>
```

### 4.3 Cài đặt VPS

SSH vào VPS, chạy script (hoặc copy file lên rồi chạy):

```bash
curl -fsSL https://raw.githubusercontent.com/MinhLee102/NihonMukae/main/deploy/setup-vps.sh | sudo bash
```

Repo private thì copy file `deploy/setup-vps.sh` lên bằng `scp` rồi `sudo bash setup-vps.sh`. Script cài Docker, bật tường lửa (chỉ SSH, 80, 443), tạo swap 2 GB và thư mục `/opt/nihongo-app`. Đăng xuất rồi đăng nhập lại để nhóm `docker` có hiệu lực.

Tạo file cấu hình bí mật trên VPS:

```bash
cd /opt/nihongo-app
nano .env   # dán nội dung deploy/.env.prod.example rồi điền
```

Điền: `API_DOMAIN`, `CORS_ALLOWED_ORIGIN` (địa chỉ Vercel, xem 4.5), `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`. Tạo chuỗi ngẫu nhiên bằng:

```bash
openssl rand -base64 32
```

`BACKEND_IMAGE` phải **viết thường**: `ghcr.io/minhlee102/nihonmukae/backend:latest`. File `.env` không bao giờ được commit.

### 4.4 Thêm GitHub Secrets

Vào repo, mục Settings, Secrets and variables, Actions, tạo:

| Secret | Giá trị |
| --- | --- |
| `VPS_HOST` | IP hoặc `api.<tên-miền>` |
| `VPS_USER` | user SSH (ví dụ `root` hoặc `ubuntu`) |
| `VPS_SSH_KEY` | nội dung file private key ở bước 4.1 |
| `VPS_SSH_PORT` | chỉ cần nếu SSH không dùng cổng 22 |

`GITHUB_TOKEN` có sẵn, không cần tạo. Image trên ghcr.io để **private** cũng được: workflow tự đăng nhập ghcr trên VPS bằng token của lần chạy đó.

### 4.5 Frontend trên Vercel

1. Vào vercel.com, đăng nhập bằng GitHub, **Add New, Project**, chọn repo.
2. **Root Directory**: chọn `frontend`. Framework tự nhận Next.js.
3. Environment Variables: `NEXT_PUBLIC_API_URL` = `https://api.<tên-miền>`.
4. Deploy. Từ đây mỗi push lên `main` tự deploy, mỗi PR có link xem thử.
5. Quay lại VPS, sửa `CORS_ALLOWED_ORIGIN` trong `.env` thành địa chỉ Vercel, ví dụ `https://nihonmukae.vercel.app` (đúng từng ký tự, không có dấu `/` cuối), rồi áp dụng:

   ```bash
   cd /opt/nihongo-app
   docker compose -f docker-compose.prod.yml --env-file .env up -d backend
   ```

Có thể gắn tên miền riêng cho frontend (ví dụ `app.<tên-miền>`) trong Vercel, mục Domains, rồi nhớ cập nhật lại `CORS_ALLOWED_ORIGIN`.

## 5. Lần deploy đầu tiên

Vào tab Actions của repo, chọn **Deploy Backend**, **Run workflow** (hoặc push một thay đổi trong `backend/` lên `main`). Pipeline chạy 3 job:

1. **test**: chạy `mvnw verify` với Postgres và Redis tạm.
2. **build-and-push**: build image, đẩy lên `ghcr.io/minhlee102/nihonmukae/backend` với tag `latest` và `sha-<commit>`.
3. **deploy**: copy compose file, Caddyfile và script lên VPS, SSH vào chạy `deploy.sh`. Script kéo image, khởi động và **đợi backend healthy** (tối đa 180 giây). Quá hạn thì job đỏ và in 60 dòng log cuối.

Kiểm tra:

```bash
curl https://api.<tên-miền>/actuator/health
# {"status":"UP"}
```

Flyway tự tạo toàn bộ bảng ở lần chạy đầu.

## 6. Nạp dữ liệu từ điển (một lần)

Dữ liệu Mazii và JMdict quá lớn và không nằm trong git. Từ máy bạn, upload lên VPS:

```bash
scp scripts/mazii/data/*.csv scripts/jmdict/JMdict_e <user>@<vps>:~/data/
```

(cần tạo thư mục trước: `ssh <user>@<vps> mkdir -p ~/data`). Rồi trên VPS:

```bash
bash /opt/nihongo-app/deploy/seed-data.sh ~/data
```

Script chạy Python trong một container tạm, nối vào mạng của compose và nạp thẳng vào Postgres. Mazii chỉ chạy nếu bảng `words` còn rỗng (chạy lại an toàn). Backfill `frequency_rank` từ JMdict chỉ chạy khi có file `JMdict_e`. Mất vài phút.

Câu hỏi JLPT mẫu **không** tự nạp ở production (cờ `APP_JLPT_SEED_SAMPLE` chỉ bật trong `docker-compose.yml` dùng cho dev). Đề thật nạp qua `POST /questions/import/preview` rồi `/commit`.

## 7. Vận hành hằng ngày

```bash
cd /opt/nihongo-app
alias dc='docker compose -f docker-compose.prod.yml --env-file .env'

dc ps                      # trạng thái
dc logs -f backend         # log backend
dc restart backend         # khởi động lại
```

**Quay về bản cũ** (khi bản mới lỗi): sửa `BACKEND_IMAGE` trong `.env` thành tag cũ, ví dụ `...:sha-1a2b3c4`, rồi `dc up -d backend`. Các tag `sha-...` xem ở trang Packages của repo.

**Sao lưu Postgres** (nên đặt vào cron hằng ngày):

```bash
dc exec -T postgres pg_dump -U nihon nihon_mukae | gzip > ~/backup-$(date +%F).sql.gz
```

**Cập nhật bí mật** (ví dụ đổi `JWT_SECRET`): sửa `.env` rồi `dc up -d`. Đổi `JWT_SECRET` sẽ đăng xuất mọi người dùng.

## 8. Thử stack production ngay trên máy bạn

Không cần VPS. Trong thư mục gốc repo:

```bash
docker build -t nihongo-backend:local backend
cp deploy/.env.prod.example .env.prod
# sửa .env.prod: BACKEND_IMAGE=nihongo-backend:local, API_DOMAIN=:80,
# APP_COOKIE_SECURE=false, CORS_ALLOWED_ORIGIN=http://localhost:3000, và các mật khẩu
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
curl http://localhost/actuator/health
docker compose -f docker-compose.prod.yml --env-file .env.prod down -v   # dọn dẹp
```

Cần cổng 80 và 443 còn trống.

## 9. Chưa có tên miền thì sao

Tạm thời dùng IP, vẫn chạy được nhưng kém an toàn:

1. Trên VPS đặt `API_DOMAIN=:80` và `APP_COOKIE_SECURE=false` (Caddy phục vụ HTTP thuần, không có HTTPS).
2. Trên Vercel đặt: `BACKEND_PROXY_URL=http://<IP-VPS>` và `NEXT_PUBLIC_API_URL=/backend`.
3. `CORS_ALLOWED_ORIGIN` vẫn là địa chỉ Vercel.

Trình duyệt chỉ nói chuyện với Vercel (cùng origin nên cookie hợp lệ), Vercel chuyển tiếp sang VPS. **Nhược điểm**: đoạn Vercel tới VPS đi qua HTTP không mã hóa, JWT và mật khẩu đăng nhập bị lộ trên đường đi. Chỉ dùng để thử, hãy lấy tên miền `.me` từ Student Pack rồi chuyển sang cách ở mục 4.

## 10. Lỗi thường gặp

| Triệu chứng | Nguyên nhân và cách sửa |
| --- | --- |
| Trình duyệt báo CORS | `CORS_ALLOWED_ORIGIN` sai từng ký tự (có `/` cuối, sai `http`/`https`). Sửa rồi `dc up -d backend` |
| Đăng nhập xong tải lại trang bị đăng xuất | Cookie không lưu được: kiểm tra backend và frontend cùng site, `APP_COOKIE_SECURE=true` chỉ đi với HTTPS |
| Job deploy báo `denied` khi pull | Repo hoặc package bị đổi quyền. Kiểm tra `BACKEND_IMAGE` viết thường, và workflow có quyền `packages: write` |
| `deploy.sh` báo `Missing .env` | Chưa tạo `/opt/nihongo-app/.env` (bước 4.3) |
| Caddy không lấy được chứng chỉ | Bản ghi A chưa trỏ đúng IP, hoặc cổng 80 và 443 bị chặn. Xem `dc logs caddy` |
| Backend không healthy | `dc logs --tail 100 backend`. Thường do sai mật khẩu DB hoặc thiếu `JWT_SECRET` |
| Hết RAM, backend bị kill | Dùng VPS 2 GB, hoặc giữ swap (script đã tạo) |
| Tìm kiếm từ điển trống | Chưa chạy `seed-data.sh` (mục 6) |

## 11. Lưu ý bảo mật

- Không commit `.env`, private key hay mật khẩu. Repo chỉ chứa file `*.example`.
- `JWT_SECRET` mặc định trong `docker-compose.yml` (dev) **không** được dùng ở production. `docker-compose.prod.yml` bắt buộc phải đặt (thiếu thì compose báo lỗi).
- Nên dùng một user riêng cho CI thay vì `root`, và tắt đăng nhập SSH bằng mật khẩu.
- JMdict dùng giấy phép CC BY-SA: ghi chú nguồn "JMdict/EDICT Project" ở trang Giới thiệu của app.
