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

Ưu đãi trong Student Pack thay đổi theo thời gian. Theo các nguồn mình tra được ([Capital & Compute](https://capitalandcompute.net/blog/github-student-developer-pack-2026/), [80aj](https://www.80aj.com/2026/06/13/digitalocean-github-student-pack/)), **DigitalOcean đã rời Student Pack** (credit 200 USD hết hạn ngày 31/7/2026), trang của DigitalOcean vẫn còn quảng cáo nhưng đã cũ. Hãy luôn kiểm tra lại ở [trang chính thức](https://education.github.com/pack).

| Nhu cầu | Ưu đãi | Ghi chú |
| --- | --- | --- |
| VPS | **Azure for Students**: 100 USD credit, không cần thẻ tín dụng | Xem mục 4.1 về giới hạn của gói sinh viên |
| Tên miền | **Namecheap**: 1 năm tên miền `.me` (ngoài ra có Name.com, .TECH) | Cần tên miền để có HTTPS cho backend |

Cần tên miền vì: (1) Caddy lấy chứng chỉ HTTPS miễn phí chỉ cho tên miền, (2) cookie đăng nhập hoạt động ổn khi `app.<miền>` (Vercel) và `api.<miền>` (VPS) cùng một site. Chưa có tên miền thì xem mục 9.

Nếu Azure không tạo được VM phù hợp, có thể dùng bất kỳ VPS Ubuntu nào khác: toàn bộ stack không phụ thuộc nhà cung cấp, chỉ có bước tạo VM (4.1) khác nhau.

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

### 4.1 Tạo VM trên Azure for Students

Azure for Students tặng 100 USD credit trong 12 tháng (gia hạn khi còn là sinh viên), kèm 750 giờ VM Linux `B1s` miễn phí theo [trang Azure for Students](https://azureforeducation.microsoft.com/Student). Không cần thẻ tín dụng, và credit hết thì Azure dừng dịch vụ chứ không tính tiền.

**Hạn chế cần biết** (theo các trao đổi trên [Microsoft Q&A](https://learn.microsoft.com/en-us/answers/questions/5836257/cannot-create-a-vm-in-azure-for-students) và [Tech Community](https://techcommunity.microsoft.com/discussions/microsoft-learn-for-educators/sku-quota-and-policy-restrictions-on-azure-for-students-and-free-subscriptions/4525160)):

- Mỗi tài khoản sinh viên chỉ được dùng một danh sách region nhỏ (thường 3 đến 5 region), danh sách này khác nhau theo từng người.
- Size `B1s` hay bị báo không khả dụng ở nhiều region. Khi đó thử `B2ats_v2` hoặc `B2ts_v2`, hoặc đổi region. Các size này có thể không nằm trong 750 giờ miễn phí, nên xem giá hiển thị trên portal.
- Size nhỏ chỉ có khoảng **1 GiB RAM**. Mình đã thử: backend chạy ổn với heap 384 MB (khoảng 515 MB cho container), cả stack khoảng 750 MB. Chạy được trên VM 1 GiB nếu có swap (`setup-vps.sh` tạo sẵn 2 GB) và đặt `JAVA_OPTS` như trong `.env.prod.example`. Với vài người dùng thì đủ, nhiều người dùng cùng lúc sẽ chậm.
- 100 USD trong 12 tháng tương đương khoảng 8 USD mỗi tháng, nên đừng chọn size lớn (ví dụ 4 GiB RAM sẽ đốt hết credit trong vài tháng). Vào Cost Management để đặt cảnh báo ngân sách.

**Các bước:**

1. Kích hoạt Azure for Students bằng tài khoản GitHub Education hoặc email trường.
2. Portal Azure, **Create a virtual machine**: Image **Ubuntu Server 24.04 LTS**, Region chọn trong danh sách portal cho phép (ưu tiên Southeast Asia hoặc East Asia nếu có, gần Việt Nam), Size `B1s` hoặc size nhỏ thay thế như trên.
3. Authentication: **SSH public key**, username `azureuser`. Tạo key riêng cho CI:

   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/nihongo_deploy -C "github-actions-deploy" -N ""
   ```

   Dán nội dung file `.pub` vào ô SSH public key. Nội dung file private `~/.ssh/nihongo_deploy` (không có đuôi `.pub`) sẽ dán vào GitHub Secret `VPS_SSH_KEY` ở bước 4.4.
4. Mục **Inbound port rules**: tick **SSH (22), HTTP (80), HTTPS (443)**. Azure có tường lửa riêng (Network Security Group) mặc định chặn hết, quên bước này thì Caddy không lấy được chứng chỉ và không truy cập được từ ngoài. Có thể thêm sau trong VM, mục Networking.
5. Public IP: giữ SKU **Standard** (địa chỉ tĩnh) để IP không đổi khi tắt và bật VM. Ghi lại IP này.
6. Tắt chế độ **Auto-shutdown** nếu portal bật sẵn, và đừng "Stop" VM (VM đã dừng vẫn có thể mất IP hoặc không phục vụ).
7. Secret `VPS_USER` sẽ là `azureuser`.

**Phương án dự phòng** nếu Azure không có size nào dùng được: Oracle Cloud Always Free (VM ARM cấu hình lớn, miễn phí vĩnh viễn nhưng đăng ký cần thẻ và đôi khi hết chỗ), hoặc thuê VPS giá rẻ vài USD mỗi tháng. Hướng dẫn phần sau không đổi.

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

Điền: `API_DOMAIN`, `CORS_ALLOWED_ORIGIN` (địa chỉ Vercel, xem 4.5), `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, và `JAVA_OPTS` nếu VM chỉ có 1 GiB RAM (dòng gợi ý có trong file mẫu). Tạo chuỗi ngẫu nhiên bằng:

```bash
openssl rand -base64 32
```

`BACKEND_IMAGE` phải **viết thường**: `ghcr.io/minhlee102/nihonmukae/backend:latest`. File `.env` không bao giờ được commit.

### 4.4 Thêm GitHub Secrets

Vào repo, mục Settings, Secrets and variables, Actions, tạo:

| Secret | Giá trị |
| --- | --- |
| `VPS_HOST` | IP hoặc `api.<tên-miền>` |
| `VPS_USER` | user SSH (Azure: `azureuser`) |
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

## 6b. Thêm đề JLPT mới (không cần deploy lại)

Đề nằm trong database, nên nạp trực tiếp vào hệ thống đang chạy bằng script, từ máy bạn:

```bash
# 1. Kiểm tra file trước, chưa lưu gì
python scripts/jlpt/import_questions.py de-n3-2023-07.json --api https://<api-của-bạn> --email <email> --dry-run

# 2. Nạp thật (hỏi xác nhận trước khi lưu)
python scripts/jlpt/import_questions.py de-n3-2023-07.json --api https://<api-của-bạn> --email <email>
```

Script hỏi mật khẩu, đăng nhập, gọi `preview` (báo lỗi theo từng vị trí như `questions[3].options`, liệt kê câu trùng), rồi `commit` khi bạn xác nhận. Chạy lại cùng file là an toàn: câu trùng bị bỏ qua. File mẫu để làm theo format nằm ở `backend/src/main/resources/jlpt/*.json`.

Lưu ý:
- Audio cho câu nghe: điền `audio_url` là link công khai tới file (ví dụ Azure Blob Storage, gói sinh viên có 5 GB miễn phí). Câu nghe không có audio sẽ hiện thông báo thay thế.
- Chỉ nạp nội dung bạn có quyền sử dụng. Đề thi thật có bản quyền.
- Dữ liệu mẫu tự động chỉ có ở môi trường dev, production bắt đầu từ kho trống.

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
| Caddy không lấy được chứng chỉ | Bản ghi A chưa trỏ đúng IP, hoặc cổng 80 và 443 bị chặn (trên Azure là Network Security Group, xem bước 4 của mục 4.1). Xem `dc logs caddy` |
| Backend không healthy | `dc logs --tail 100 backend`. Thường do sai mật khẩu DB hoặc thiếu `JWT_SECRET` |
| Hết RAM, backend bị kill | Đặt `JAVA_OPTS` giới hạn heap (xem `.env.prod.example`), giữ swap (script đã tạo), hoặc dùng VM nhiều RAM hơn |
| Tìm kiếm từ điển trống | Chưa chạy `seed-data.sh` (mục 6) |

## 11. Lưu ý bảo mật

- Không commit `.env`, private key hay mật khẩu. Repo chỉ chứa file `*.example`.
- `JWT_SECRET` mặc định trong `docker-compose.yml` (dev) **không** được dùng ở production. `docker-compose.prod.yml` bắt buộc phải đặt (thiếu thì compose báo lỗi).
- Nên dùng một user riêng cho CI thay vì `root`, và tắt đăng nhập SSH bằng mật khẩu.
- JMdict dùng giấy phép CC BY-SA: ghi chú nguồn "JMdict/EDICT Project" ở trang Giới thiệu của app.
