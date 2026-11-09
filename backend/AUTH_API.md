# Authentication API — Backend

Tài liệu ngắn cho FE để kết nối với backend (phiên bản dev).

Base URL: http://localhost:8080

Endpoints

1) POST /auth/register
- Mục đích: gửi đăng ký. Tài khoản mới CHƯA được duyệt (users.approved = false), admin duyệt trong /admin/users
  thì mới đăng nhập được. Không trả token, không set cookie.
- Request JSON: { "email": "...", "password": "...", "name": "..." }
- Success: 202 { "pending": true }
- Failure: 400 { "success": false, "message": "..." } (email sai định dạng, mật khẩu ngắn, email đã dùng)

2) POST /auth/login
- Mục đích: đăng nhập
- Request JSON: { "email": "...", "password": "..." }
- Success: 200
  Body: { "accessToken": "<JWT>" }
  Set-Cookie as above
- Failure: 401 { "error": "invalid_credentials" }
- Failure: 403 { "error": "account_locked" } (bị admin khoá) hoặc 403 { "error": "pending_approval" } (chưa được duyệt)
  /auth/refresh cũng từ chối tài khoản chưa duyệt hoặc đã khoá.

3) POST /auth/refresh
- Mục đích: cấp lại access token dùng refresh token từ cookie
- No body. Cookie must be sent: refresh_token=RAW_TOKEN
- Success: 200 { "accessToken": "<JWT>" } + Set-Cookie (new refresh_token)
- Failure: 401 { "error": "invalid_refresh_token" } or 401 { "error": "no_refresh_token" }

4) POST /auth/logout
- Mục đích: thu hồi refresh token và xóa cookie
- No body. Server will delete DB entry and set cookie max-age=0
- Success: 200 { "ok": true }

Important implementation details (BE)
- Access token: JWT signed with secret from env JWT_SECRET. FE should store access token in memory only (not localStorage).
- Refresh token: server generates a random raw token and sends it to client via HttpOnly cookie. Backend stores SHA-256(raw) base64url in refresh_tokens.token column. On refresh, server hashes cookie value and matches DB where expires_at > now().
- Cookie config (dev): HttpOnly, Path=/, SameSite=Lax, Secure=false. Note: SameSite=Lax blocks cross-site POST requests in browsers; for FE hosted at different origin use either SameSite=None + Secure=true (production with HTTPS) or for local dev set SameSite=None + Secure=false.

FE integration examples

- Using fetch / Axios with refresh call (example Axios):
  // include credentials when calling refresh
  await axios.post('http://localhost:8080/auth/refresh', {}, { withCredentials: true });

- Attaching Authorization header for protected endpoints:
  axios.get('http://localhost:8080/api/protected', { headers: { Authorization: `Bearer ${accessToken}` } });

CORS notes
- If FE runs on a different origin (eg http://localhost:3000), backend must allow CORS and credentials. In dev: allowOrigin http://localhost:3000 and Access-Control-Allow-Credentials: true.

DB quick checks
- Table refresh_tokens stores token (hashed), user_id, expires_at.
- To verify: SELECT id,user_id,token,expires_at FROM refresh_tokens;

Errors & troubleshooting
- 403/401 on /auth/refresh usually means cookie not sent or token invalid/expired. Test with curl:
  curl -v -X POST http://localhost:8080/auth/refresh --cookie "refresh_token=<RAW_TOKEN>"

Security notes
- DO NOT store refresh raw token in client-accessible storage; it's sent only via HttpOnly cookie.
- For production: SameSite=None + Secure=true + HTTPS; store JWT_SECRET in secrets manager or env, not in repo.

If FE team needs sample axios interceptors (auto-refresh on 401) or example code for cookie management, tôi có thể cung cấp.