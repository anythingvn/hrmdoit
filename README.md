# DOIT HRM

Phần mềm quản lý nhân sự nội bộ DOIT: hồ sơ nhân viên, hợp đồng & lương, lịch sử công tác, hồ sơ scan, nhật ký thay đổi, xuất Excel. Giao diện tiếng Việt.

## Cấu trúc repo

- `pom.xml` – Maven aggregator, hai module:
  - `core/` – lõi Java 17 thuần (package `vn.doit.hr`, **không phụ thuộc Spring**): quy tắc lịch sử vị trí, thuế TNCN / quy đổi Net → Gross, import bảng lương, sinh hợp đồng, nhắc hạn, báo cáo, chấm công. Mô tả nghiệp vụ ở phần dưới.
  - `app/` – ứng dụng JHipster 9.4.0 (Spring Boot 4.1, Java 21, package `vn.doit.hrm`) + giao diện **React 19** (TypeScript, Vite, react-bootstrap), xác thực JWT, cơ sở dữ liệu **PostgreSQL** (Liquibase, nhật ký thay đổi bằng Hibernate Envers). Dùng `core` làm thư viện.
- `Dockerfile`, `docker-compose.yml`, `ops/` – triển khai (app + PostgreSQL + backup hằng ngày + Caddy tùy chọn).
- `docs/` – thiết kế và kế hoạch.

Vai trò: Quản trị (`ROLE_ADMIN`, toàn quyền), Nhân sự (`ROLE_HR`), Kế toán (`ROLE_KE_TOAN`), Ban giám đốc (`ROLE_BGD`), Nhân viên (`ROLE_NHAN_VIEN`, chỉ xem hồ sơ của chính mình). Không có tự đăng ký; Quản trị (và HR, trên hồ sơ nhân viên) tạo tài khoản, tên đăng nhập là số CCCD, mật khẩu mặc định `Doit@2014` – xem mục "Tài khoản đăng nhập".

## Phát triển và chạy test

Cần JDK 21 (lõi `core` vẫn biên dịch ở mức Java 17), Node 24+ (≥ 24.21) và Docker đang chạy (test tích hợp dùng **Testcontainers PostgreSQL**; không dùng H2).

```bash
export JAVA_HOME=$(brew --prefix openjdk@21)          # máy macOS (brew install openjdk@21): java mặc định không có trên PATH
# Docker Desktop mới: chỉ cho Testcontainers socket của Docker Desktop
export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock

# Backend: lõi + app, unit test (surefire) + test tích hợp (failsafe, Testcontainers) + ArchUnit
mvn -pl app -am verify -Dskip.npm -Dskip.installnodenpm

mvn -pl core test                                     # chỉ lõi
mvn -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=NONE \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=EmployeeResourceIT -Dfailsafe.failIfNoSpecifiedTests=false   # một lớp IT

# Frontend (thư mục app/)
cd app && npm install
npm test                 # Vitest + Testing Library
npx tsc --noEmit         # kiểm tra kiểu
npm run lint             # ESLint
```

Nếu Docker Desktop báo phiên bản API quá cũ, tạo `~/.docker-java.properties` với `api.version=1.44`.

Chạy dev: cần một PostgreSQL ở `localhost:5432` (db/user `hrm`, không mật khẩu – PostgreSQL dev cho phép `trust`; xem `app/src/main/resources/config/application-dev.yml`; có thể dùng `docker compose -f app/src/main/docker/postgresql.yml up -d`), rồi `mvn -q -pl core -am install -DskipTests` một lần (cài pom gốc và lõi `core` vào ~/.m2, cần mỗi khi `core` đổi), rồi `cd app && ./mvnw` (backend, http://localhost:8080; hồ sơ `dev` tự bật PostgreSQL dev bằng Docker; khóa JWT dev nằm trong `application-dev.yml`, file này không có trong jar prod) và `npm start` (Vite, giao diện có tải lại nóng, http://localhost:9000).

Tài khoản mặc định `admin` / `admin` chỉ dùng khi phát triển; trên máy thật phải đổi ngay.

**Database dev tạo trước khi nâng cấp JHipster 9.4.0 phải xóa và tạo lại** (dữ liệu mẫu faker đổi nên checksum changeset
Liquibase khác, ứng dụng không khởi động): `docker compose -f app/src/main/docker/postgresql.yml down -v` rồi `up -d` lại
(hoặc `DROP DATABASE hrm; CREATE DATABASE hrm;`). Chỉ áp dụng cho máy dev: hồ sơ `prod` không nạp dữ liệu faker.
PostgreSQL dev, Testcontainers và production cùng dùng `postgres:16.15-bookworm`.

**Số test hiện tại** (22/10/2026, sau phần nghỉ phép và đơn từ): backend **1194 test** (gồm lõi `core`: 135; `app`: 195 unit + 864 tích hợp), frontend **400 test** Vitest — tất cả đạt. Kiểm tra script backup: xem mục "Backup & khôi phục".

## Triển khai lên VPS

Cần Docker + Docker Compose trên VPS.

1. `cp .env.example .env`, rồi điền trong `.env`:
   - `POSTGRES_PASSWORD`: mật khẩu mạnh.
   - `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`: sinh bằng `openssl rand -base64 64 | tr -d '\n'`. Thiếu biến này ứng dụng không khởi động.
   - `BACKUP_OFFSITE_RSYNC`, `DOMAIN`: xem các mục dưới.
   - Gửi thư (tùy chọn): `SPRING_MAIL_*`, `JHIPSTER_MAIL_FROM`, `JHIPSTER_MAIL_BASE_URL` – xem mục "Tài khoản đăng nhập".
   - `APPLICATION_ACCOUNT_DEFAULT_PASSWORD` (tùy chọn): mật khẩu mặc định của tài khoản mới; để trống = `Doit@2014`.
2. `docker compose up -d --build` (lần đầu build mất vài phút).
3. Kiểm tra: `curl -fsS http://127.0.0.1:8080/management/health` trả `{"status":"UP",...}`.
   Khi ứng dụng đã lên, chạy ngay một lần `docker compose exec backup /ops/backup/backup.sh` để có bản backup từ ngày đầu (không phải chờ 02:00).
4. **Đăng nhập `admin` / `admin` và đổi mật khẩu `admin` ngay lần đầu** (thanh trái → **Đổi mật khẩu**).
5. Ứng dụng chỉ lắng nghe `127.0.0.1:8080`. Nếu VPS chưa có reverse proxy, đặt `DOMAIN=ten-mien.example.com` trong `.env`, trỏ DNS về VPS rồi chạy `docker compose --profile proxy up -d`; Caddy tự cấp HTTPS (cổng 80/443). Nếu VPS đã có nginx/Caddy riêng, trỏ nó tới `127.0.0.1:8080`.

**Không publish `APP_BIND` ra Internet — dùng Caddy.** `APP_BIND` chỉ để đổi cổng nội bộ khi `8080` bận (vd `127.0.0.1:8081`).
Đặt `APP_BIND=0.0.0.0:8080` (hoặc IP công khai) là mở ứng dụng bằng HTTP không mã hóa: mật khẩu và JWT đi qua mạng dạng rõ, không
có HSTS, và giới hạn đăng nhập sai tính theo IP không còn đúng. Ra Internet luôn qua Caddy (`--profile proxy`) hoặc reverse proxy
HTTPS có sẵn trên VPS. Caddy chỉ cho `/management/*` đi qua với health/info và các endpoint màn hình Quản trị dùng (ứng dụng
vẫn yêu cầu `ROLE_ADMIN`); `/management/prometheus` và các endpoint khác trả 404 từ ngoài vào.

**Khôi phục trên VPS mới** (VPS cũ hỏng/mất): xem phần đầu của [`ops/backup/restore.md`](ops/backup/restore.md#khôi-phục-trên-vps-mới).

Cập nhật phiên bản: `git pull && docker compose up -d --build`.

Các image Docker được ghim tag chính xác (`postgres:16.15-bookworm`, `caddy:2.11.7`, `maven:3.9.16-eclipse-temurin-21-noble`,
`eclipse-temurin:21.0.12.1_1-jre-noble`, `gotenberg/gotenberg:8.37.0`); khi có bản vá bảo mật thì sửa tag trong
`docker-compose.yml`, `Dockerfile`, `ops/backup/Dockerfile`, `ops/pdf/Dockerfile` rồi build lại. Đổi bản vá PostgreSQL cùng
major 16 không cần dump/restore.

### Dịch vụ PDF

Service `pdf` (build từ `ops/pdf/Dockerfile`) là Gotenberg 8 – LibreOffice chạy qua HTTP – có cài thêm font Times New Roman
(`ttf-mscorefonts-installer`, tải font lúc build nên VPS cần ra được Internet khi `docker compose build`). Service không
publish cổng; app gọi `http://pdf:3000` (biến `APPLICATION_PDF_URL`, chờ tối đa 60 giây mỗi lần). Giới hạn RAM 1 GB.
Khi service `pdf` không chạy, sinh văn bản vẫn ra file `.docx`; giao diện báo "Chưa tạo được PDF" và có nút "Tạo lại PDF".
Kiểm tra riêng: `docker compose build pdf`; test tích hợp `GotenbergPdfServiceIT` (tag `pdf`) build đúng image này và
kiểm tra PDF có dấu tiếng Việt và nhúng font Times New Roman.

### Bảo mật

- **Đăng nhập sai:** sai mật khẩu 5 lần trong 15 phút (tính theo tên đăng nhập + IP; đăng nhập bằng email được tính chung với
  tên đăng nhập) thì bị chặn, kể cả nhập đúng, đến khi hết 15 phút tính từ lần sai cũ nhất (HTTP 429, câu báo tiếng Việt).
  Gửi nhiều request song song cũng chỉ tối đa 5 lần được kiểm tra mật khẩu. Ngoài ra mỗi IP chỉ được sai tổng cộng 50 lần
  trong 15 phút (mọi tài khoản). Đăng nhập đúng xóa bộ đếm của tài khoản đó. Bộ đếm nằm trong bộ nhớ (một instance; khởi
  động lại app thì về 0). Sau Caddy, IP thật lấy từ `X-Forwarded-For` và chỉ được tin khi request đến từ mạng nội bộ (proxy),
  nên client không giả được IP.
  **Dùng chung IP (NAT văn phòng, wifi công ty):** mọi người sau cùng một IP công khai chia nhau giới hạn 50 lần sai/15 phút;
  khi bị chặn cả văn phòng phải chờ hết 15 phút (hoặc khởi động lại app). Một tài khoản bị khóa vì sai 5 lần: Quản trị vào
  **Quản trị → Tài khoản → Xem → Mở khóa đăng nhập** (API `DELETE /api/admin/login-attempts/{login}`, chỉ Quản trị).
- **CSP:** `script-src 'self'` (không script inline/eval), `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`.
  `style-src` còn `'unsafe-inline'` vì react-bootstrap/popper và react-toastify đặt style trực tiếp.
- **HSTS:** `max-age=31536000; includeSubDomains` (Caddy và ứng dụng khi chạy sau https).
- **Upload:** tối đa 20 MB/file, chặn ngay ở trình duyệt; vượt quá thì máy chủ trả 413.
- Số tiền (lương, Gross, phụ cấp, lương đóng BHXH) không được âm. Hợp đồng đã ký/đã kết thúc không xóa được (chỉ xóa bản
  nháp). Sửa hợp đồng, lương, phụ cấp không chuyển được sang nhân viên khác. Quản trị đặt mật khẩu thì link đặt lại mật
  khẩu cũ hết hiệu lực.

### Tài khoản đăng nhập

- **Tên đăng nhập = số CCCD.** Mọi tài khoản gắn hồ sơ nhân viên đăng nhập bằng số CCCD trong hồ sơ (12 chữ số; chấp nhận 9
  chữ số CMND cũ; khoảng trắng hai đầu được bỏ). Tài khoản quản trị hệ thống `admin` giữ nguyên.
  - **Trên hồ sơ nhân viên**, tab **Tài khoản đăng nhập** (Quản trị và HR): chọn vai trò, tùy chọn nhập **Mật khẩu ban đầu**, rồi
    **Tạo tài khoản** — không nhập tên đăng nhập. Hồ sơ chưa có CCCD (hoặc CCCD sai dạng) thì báo "Nhập số CCCD trong hồ sơ trước khi tạo tài khoản".
    Mỗi tài khoản gắn tối đa một hồ sơ, mỗi hồ sơ tối đa một tài khoản. Bỏ gắn không xóa tài khoản. Chỉ Quản trị được cấp vai
    trò Quản trị hoặc gắn/bỏ gắn tài khoản Quản trị.
  - **Quản trị → Tài khoản → Tạo tài khoản mới** (chỉ Quản trị): nhập **Số CCCD**, ô Tên đăng nhập tự điền đúng số đó và khóa.
    CCCD trùng hồ sơ nhân viên đang làm việc chưa có tài khoản thì tài khoản được gắn luôn vào hồ sơ đó; không có hồ sơ (vd quản
    trị viên ngoài) thì tài khoản không gắn hồ sơ, tên đăng nhập vẫn là CCCD.
  - **Gắn tài khoản có sẵn** vào hồ sơ: tên đăng nhập của tài khoản được đổi thành số CCCD của hồ sơ (mật khẩu giữ nguyên,
    phiên đăng nhập cũ hết hiệu lực). Không gắn được tài khoản `admin`.
  - **Sửa số CCCD** trên hồ sơ có tài khoản gắn: tên đăng nhập đổi theo trong cùng lần lưu, phiên cũ hết hiệu lực, người dùng
    đăng nhập lại bằng số mới. Số mới đã là tên đăng nhập của tài khoản khác → báo lỗi, hồ sơ không đổi. Hồ sơ có tài khoản
    không để trống CCCD. Thay đổi CCCD có trong Lịch sử thay đổi của hồ sơ (giá trị được che). Tài khoản đang bị chặn vì đăng
    nhập sai vẫn bị chặn với tên mới (đổi CCCD không mở khóa được). Hồ sơ gắn tài khoản Quản trị: chỉ Quản trị sửa được số CCCD
    (`adminaccountrequiresadmin`).
  - Quản trị không đổi tay tên đăng nhập của tài khoản gắn hồ sơ (sửa CCCD trên hồ sơ).
  - Tài khoản tạo **trước** khi có chức năng này giữ tên đăng nhập cũ cho đến khi sửa CCCD trên hồ sơ hoặc bỏ gắn rồi gắn lại.
  - Hồ sơ đã nghỉ việc: không tạo tài khoản mới; chỉ gắn được tài khoản đang khóa (tài khoản đó không mở lại được khi người đó
    còn ở trạng thái nghỉ — muốn mở thì dùng **Hoàn tác nghỉ việc**).
- **Mật khẩu ban đầu tùy người tạo:** cả hai màn tạo tài khoản có ô **Mật khẩu ban đầu (tùy chọn)** (8–100 ký tự, không hiện
  lại, không trả về, không ghi log). **Để trống = mật khẩu mặc định `Doit@2014`.** Giao diện đọc giá trị đang cấu hình qua
  `GET /api/admin/account-settings/default-password` (chỉ Quản trị và HR, không cache). Đổi mật khẩu mặc định bằng biến
  `APPLICATION_ACCOUNT_DEFAULT_PASSWORD` trong `.env` (ít nhất 8 ký tự; để trống = `Doit@2014`; cấu hình
  `application.account.default-password`).
- **Bắt đổi mật khẩu lần đầu:** tài khoản mới (mật khẩu mặc định hoặc mật khẩu ban đầu người tạo nhập), tài khoản Quản trị vừa
  **Đặt mật khẩu** hoặc **Đặt lại mật khẩu mặc định** đăng nhập được nhưng chỉ vào được trang **Đổi mật khẩu**
  ("Bạn cần đổi mật khẩu được cấp để tiếp tục"); mọi API khác trả 403 `error.mustchangepassword` (trừ
  `GET /api/account`, `POST /api/account/change-password`). Mật khẩu mới không được là mật khẩu mặc định (`error.defaultpassword`)
  và không được trùng mật khẩu đang dùng (`error.samepassword`, áp dụng cho mọi lần tự đổi mật khẩu). Đổi xong phải đăng nhập lại.
- **Email không bắt buộc**; có thể điền sau.
- **Không lộ CCCD qua tên đăng nhập:** chỉ Quản trị và HR (được xem CCCD) và chính chủ thấy đủ tên đăng nhập; Ban giám đốc, Kế
  toán thấy dạng che `********1234` (trường `userLogin`/`user.login` của hồ sơ, người tải hồ sơ lên). Danh sách tài khoản
  `GET /api/users` chỉ dành cho Quản trị; người tạo/sửa (`createdBy`, `lastModifiedBy`) ở `GET /api/account` cũng được che. Log
  (đăng nhập, mở khóa, quản lý tài khoản) chỉ ghi tên đăng nhập dạng che hoặc mã số tài khoản.
- **`ROLE_NHAN_VIEN` + "Hồ sơ của tôi":** nhân viên thường chỉ thấy mục **Hồ sơ của tôi** (`/ho-so-cua-toi`, API
  `GET /api/account/employee`), xem được thông tin nhạy cảm của chính mình (có ghi nhật ký), không vào được dữ liệu khác. Mục
  "Hồ sơ của tôi" cũng hiện với mọi vai trò khác khi tài khoản đã gắn hồ sơ nhân viên.
- **Khóa tài khoản có hiệu lực ngay:** khóa tài khoản (Quản trị bỏ chọn "Kích hoạt", hoặc cho nghỉ việc) thì phiên đăng nhập đang
  mở bị từ chối ở request kế tiếp, không chờ token hết hạn. Đổi mật khẩu (tự đổi, Quản trị đặt, đặt lại qua link), đổi vai trò,
  khóa rồi mở lại cũng làm mọi phiên cũ của tài khoản đó hết hiệu lực (phải đăng nhập lại). Tài khoản bị khóa đăng nhập thì
  nhận đúng câu báo như sai mật khẩu.
- **Không tự khóa mình / không mất Quản trị cuối cùng:** Quản trị không tự khóa, tự xóa hay tự bỏ quyền Quản trị của mình; không
  khóa, xóa hay bỏ quyền của tài khoản Quản trị đang hoạt động cuối cùng.
- **Mở khóa đăng nhập sai:** tài khoản bị chặn vì sai mật khẩu 5 lần → **Quản trị → Tài khoản → Xem → Mở khóa đăng nhập**
  (API `DELETE /api/admin/login-attempts/{login}`). Hoàn tác nghỉ việc cũng tự xóa bộ đếm này của tài khoản được mở lại.
- **Đặt lại mật khẩu cho người khác:** **Quản trị → Tài khoản → Xem → Đặt mật khẩu** (8–100 ký tự; người dùng phải đổi mật
  khẩu khi đăng nhập). API:
  `PUT /api/admin/users/{login}/password` (chỉ Quản trị; mật khẩu chỉ lưu dạng băm, không ghi log; link đặt lại cũ hết hiệu lực).
  Trong hộp thoại có nút **Đặt lại mật khẩu mặc định** (`PUT /api/admin/users/{login}/password/default`): mật khẩu về mặc
  định, người dùng phải đổi mật khẩu khi đăng nhập.

**Gửi thư (tùy chọn).** Người dùng có email tự yêu cầu link đặt lại mật khẩu qua email ở trang đăng nhập (tài khoản mới dùng
mật khẩu mặc định nên không còn gửi thư khi tạo). Cấu hình trong `.env` rồi
`docker compose up -d`: `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`,
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true` (khi máy chủ yêu cầu đăng nhập), `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`
(cổng 587), `JHIPSTER_MAIL_FROM` (địa chỉ gửi) và `JHIPSTER_MAIL_BASE_URL` (địa chỉ truy cập ứng dụng, vd `https://hrm.congty.vn`).

**Cấu hình email trên giao diện (Quản trị → Cấu hình email).** Quản trị nhập máy chủ SMTP, cổng, bảo mật (STARTTLS / SSL /
không), tài khoản, mật khẩu, email gửi và tên hiển thị; có hiệu lực ngay cho mọi thư (đặt lại mật khẩu, nhắc việc, phiếu lương),
không cần khởi động lại. Nút **Gửi thư thử** báo lỗi dễ hiểu (sai mật khẩu, không kết nối được máy chủ…). Chưa lưu gì ở đây thì
dùng các biến `SPRING_MAIL_*` / `JHIPSTER_MAIL_FROM` trong `.env`; **Dùng lại cấu hình .env** xóa cấu hình trên giao diện.
- Mật khẩu lưu trong cơ sở dữ liệu dạng mã hóa (AES-GCM) bằng khóa `APPLICATION_MAIL_SECRET_KEY` trong `.env` (ít nhất 16 ký tự,
  sinh bằng `openssl rand -base64 32`); không bao giờ hiện lại trên màn hình, không có trong lịch sử thay đổi. Chưa đặt khóa thì
  không lưu được mật khẩu trên giao diện. Đổi hoặc mất khóa: nhập lại mật khẩu email. Backup cơ sở dữ liệu chỉ chứa bản mã hóa —
  giữ khóa cùng chỗ với file `.env` khi khôi phục.

### Hồ sơ: mã nhân viên, thực tập, bảo hiểm bắt buộc

- **Mã tự gán, đánh số cộng dồn.** Nhân viên: `DOIT` + 2 số cuối năm vào làm + số thứ tự kế tiếp (sau DOIT2516, người vào
  năm 2026 là DOIT2617). Thực tập: `TT` + năm + dãy riêng (TT2601, TT2602…). Số thứ tự lấy theo số lớn nhất từng dùng (kể cả
  người đã nghỉ) nên không bao giờ cấp lại. Form tạo hồ sơ điền sẵn mã gợi ý (đổi theo năm vào làm và loại hồ sơ); HR sửa tay
  được; để trống thì máy chủ tự cấp lúc lưu (hai người tạo cùng lúc không trùng). Mã HĐ dịch vụ vẫn là `HDDVn` tự sinh.
- **Loại hồ sơ "Thực tập (học việc)"**: hồ sơ đầy đủ (CCCD, tài khoản, HĐ đào tạo/thử việc), có phiếu lương nếu có trong bảng
  lương, **không** tính vào số nhân viên đang làm, biến động, báo cáo nhân sự và nhắc việc. Lên chính thức: đổi loại sang Nhân
  viên và đổi mã sang DOIT… (lịch sử thay đổi giữ mã cũ).
- **Không thuộc diện đóng BHXH bắt buộc** (mục "Bảo hiểm bắt buộc" trên hồ sơ, HR/Quản trị sửa): lý do Đang hưởng lương hưu /
  Cộng tác viên, HĐ dịch vụ / HĐLĐ dưới 1 tháng / Thử việc theo HĐ thử việc riêng / Khác, kèm ghi chú. Khi đánh dấu: gợi ý
  Gross từ Net không cộng 10,5% bảo hiểm (Gross đã lưu trước đó không tính lại); import bảng lương không cảnh báo thiếu BH, nhưng
  cảnh báo nếu file vẫn có đóng BH cho người đó. Phiếu lương có BH người lao động bằng 0 ghi "Không đóng BHXH, BHYT, BHTN trong
  kỳ này". Hệ thống chỉ ghi nhận lý do; trường hợp cụ thể nên đối chiếu với cơ quan BHXH.

### Nhập danh sách nhân viên từ Excel

Dành cho Quản trị và HR: danh sách nhân viên → **Nhập từ Excel** (trang `/nhan-vien/nhap-excel`). Mỗi dòng tạo **một hồ sơ mới**.

1. **Tải file mẫu** (`mau-nhap-nhan-vien.xlsx`), điền từ dòng 2 của sheet "Danh sách nhân viên". Sheet "Hướng dẫn" có cột Cột |
   Bắt buộc | Kiểu | Ví dụ | Ghi chú; sheet ẩn "DanhMuc" chứa danh sách chọn thả xuống, không sửa.
2. **Tải file lên để kiểm tra** (chưa ghi gì). Lỗi chỉ đúng dòng Excel và cột; **Tải báo cáo lỗi** xuất danh sách lỗi ra Excel.
3. **Lưu** khi hết lỗi (có cảnh báo thì tích "Đã xem cảnh báo"). Kết quả hiện mã thật của từng hồ sơ.

- **Bắt buộc:** Họ và tên, Số CCCD (12 số, hoặc 9 số CMND cũ). Các cột khác tùy chọn; thứ tự cột không quan trọng.
- **Mã NV:** để trống thì tự gán như tạo tay (DOIT/TT + năm vào làm + số kế tiếp; thiếu ngày vào làm lấy năm nay); có ghi thì giữ
  (viết hoa, bỏ khoảng trắng). Mã ở bước kiểm tra chỉ là **dự kiến**, có thể lệch nếu có người tạo hồ sơ cùng lúc; mã thật hiện
  sau khi lưu. HĐ dịch vụ luôn tự sinh `HDDVn`.
- **Trùng là lỗi, không cập nhật hồ sơ cũ:** trùng CCCD hoặc mã với hồ sơ đã có (kể cả người đã nghỉ) hoặc giữa hai dòng trong file.
- **Phòng ban** không phân biệt hoa thường, dấu; chưa có thì **tự tạo** (cảnh báo, liệt kê khi kiểm tra).
- **Quản lý trực tiếp:** ghi mã NV hoặc số CCCD của người quản lý (có sẵn trong hệ thống hoặc trong cùng file); không tìm thấy,
  tự trỏ vào mình hoặc vòng lặp là lỗi.
- **Lưu tất cả hoặc không:** còn lỗi, hoặc có lỗi lúc lưu, thì không hồ sơ nào được tạo. Trạng thái luôn "Đang làm việc".
- **File không được lưu lại** trên máy chủ (chứa CCCD, số tài khoản); trình duyệt gửi file hai lần, lúc kiểm tra và lúc lưu.
- **Giới hạn:** `.xlsx`, tối đa 20 MB và 2.000 dòng mỗi lần.
- **Ngoài phạm vi:** cập nhật hồ sơ đã có, hợp đồng, lương, phụ cấp và tài khoản đăng nhập (làm sau trên từng hồ sơ).

### Nghỉ việc / hẹn ngày / hủy / hoàn tác

Trên hồ sơ nhân viên (Quản trị, HR): **Cho nghỉ việc** → nhập ngày làm việc cuối, ngày nghỉ việc (mặc định ngày làm việc cuối + 1),
hình thức, lý do. Nhân viên có tài khoản Quản trị thì chỉ Quản trị cho nghỉ được; không tự cho mình nghỉ; không khóa tài khoản
Quản trị đang hoạt động cuối cùng.

- **Áp dụng:** trạng thái "Đã nghỉ", chốt giai đoạn làm việc đang mở và kết thúc HĐ đang hiệu lực ở ngày làm việc cuối, khóa tài
  khoản đã gắn. **Không xóa hay sửa dữ liệu lịch sử** (HĐ, lương, phụ cấp, vị trí, tài liệu); người đã nghỉ vẫn có trong danh sách,
  báo cáo, xuất Excel.
- **Ngày làm việc cuối đã qua:** áp dụng ngay. **Hôm nay hoặc sau hôm nay:** hẹn lịch — nhân viên vẫn "Đang làm việc", tài khoản
  vẫn dùng được đến hết ngày làm việc cuối, danh sách và hồ sơ có nhãn **"Sắp nghỉ (dd/MM/yyyy)"**. Job hằng ngày **00:05** (giờ Việt
  Nam) áp dụng các lịch đã đến hạn; nếu ứng dụng tắt đúng lúc đó, lần chạy bị lỡ được chạy bù khi ứng dụng khởi động lại. Lịch
  đến hạn mà chưa áp dụng được (vd sau khi hẹn lại thêm giai đoạn làm việc sau ngày làm việc cuối) hiện nhãn **"Quá hạn nghỉ
  việc – chưa áp dụng được: lý do"** để HR xử lý; job thử lại mỗi ngày. Khi đang có lịch: không thêm được giai đoạn làm việc hoặc
  HĐ đã ký bắt đầu sau ngày làm việc cuối.
- **Hủy lịch:** nút **Hủy lịch nghỉ** (trước khi áp dụng) — không đổi gì khác, tài khoản vẫn mở.
- **Hoàn tác nghỉ việc** (lần cho nghỉ gần nhất đã áp dụng): trả lại đúng trạng thái, các trường nghỉ việc, ngày kết thúc giai đoạn
  làm việc và HĐ như trước khi cho nghỉ, mở lại tài khoản. Sửa của HR sau đó trên các trường khác được giữ nguyên.
  - **Bị từ chối (`undoconflict`, liệt kê lý do)** nếu sau khi cho nghỉ: trạng thái/thông tin nghỉ việc, ngày của giai đoạn hay
    trạng thái/ngày kết thúc HĐ do việc cho nghỉ đặt đã bị sửa hoặc xóa; có thêm giai đoạn làm việc bắt đầu sau ngày làm việc cuối;
    có thêm HĐ đã ký trùng thời gian với HĐ sẽ được mở lại. Hoàn tác hai lần → "Không có gì để hoàn tác".
  - **Không khôi phục được:** giai đoạn "quay về" đã xóa để cho nghỉ (hộp thoại báo trước, hoàn tác báo lại); tài khoản đã bị bỏ
    gắn hoặc đổi sang tài khoản khác sau khi cho nghỉ (không tự mở tài khoản cũ); tài khoản Quản trị khi người hoàn tác là HR
    (vẫn khóa, ghi chú "Tài khoản quản trị: cần Quản trị mở lại"). Phiên đăng nhập cũ của người được hoàn tác không dùng lại được
    — người đó đăng nhập lại.

### Hợp đồng & mẫu văn bản

Menu **Hợp đồng & mẫu** (Quản trị, Nhân sự) có hai trang: **Mẫu văn bản** và **Số văn bản**. Văn bản sinh ra và bản đã ký chứa
CCCD, tài khoản ngân hàng: chỉ Quản trị và Nhân sự tạo/tải được (Kế toán, Ban giám đốc nhận 403); mỗi lần sinh/tải được ghi nhật
ký truy cập dữ liệu nhạy cảm. Nhân viên tải được **bản đã ký** hợp đồng của chính mình ở **Hồ sơ của tôi**.

- **Sinh hợp đồng:** hồ sơ nhân viên → tab **Hợp đồng & lương** → nút **Văn bản** trên dòng HĐ (hoặc **Tạo văn bản từ mẫu** trên thẻ
  đầu: mở HĐ mới nhất) → **Tạo văn bản**. Ra file Word và PDF; mỗi lần tạo là một phiên bản mới, giữ nguyên số HĐ.
  - Hồ sơ thiếu trường bắt buộc (Họ tên, Ngày sinh, Số CCCD, Ngày cấp, Nơi cấp, Nơi cư trú, Chức danh, Ngày bắt đầu, Mức lương,
    Người đại diện công ty) → hiện danh sách; **Vẫn tạo** để in với ô trống.
  - Lương Net chỉ in số Net (số Gross quy đổi chỉ lưu cho kế toán). Ngày kết thúc in theo HĐ (mặc định bắt đầu + thời hạn − 1 ngày).
  - Dịch vụ PDF không chạy → vẫn có file Word, hiện "Chưa tạo được PDF" và nút **Tạo lại PDF**.
  - Sau khi ký: **Tải bản đã ký lên** (PDF ≤ 20 MB) rồi **Đánh dấu đã ký** (Nháp → Đã ký).
  - Người đại diện ký: biến `APPLICATION_COMPANY_REPRESENTATIVE_NAME` (bắt buộc), `APPLICATION_COMPANY_REPRESENTATIVE_TITLE`
    (mặc định "Giám đốc"), `APPLICATION_COMPANY_NAME` trong `.env`.
- **Quyết định cử nhân sự:** tab **Lịch sử công tác** → **Cử đi công tác / làm tại khách hàng**. Một lần lưu: ghi giai đoạn vị trí
  (tự chốt nơi cũ, tạo giai đoạn quay về), cấp số `QĐCNS`, sinh văn bản (số HĐ in trên quyết định là HĐ đang hiệu lực vào ngày ký).
  Giai đoạn chồng lấn hoặc có lịch nghỉ việc → báo lỗi, không tạo gì, không tốn số.
- **Đánh số:** mỗi loại văn bản một dãy, chạy lại từ 1 mỗi năm, dạng `DOIT/{năm}/{mã}/{số}` — mã `HĐLĐ` (cả HĐLĐ xác định và không
  xác định thời hạn), `HĐTV`, `HĐĐT`, `HĐDV`, `QĐCNS`. Năm là năm cấp số (quyết định: năm ký). Số được cấp khi tạo văn bản lần đầu
  (HĐ chưa có số). HR sửa tay được số trên HĐ nhưng không trùng số đã có; số gõ tay sẽ được bỏ qua khi cấp tự động. Văn bản bị
  hủy vẫn giữ số. Quản trị đặt **số bắt đầu** cho từng dãy (để nối tiếp số đã ký tay), không được nhỏ hơn hoặc bằng số đã cấp.
- **Sửa mẫu trong Word:** tải mẫu đang dùng về (**Tải mẫu gốc** / **Tải về**), sửa câu chữ, lưu `.docx`, **Tải mẫu mới**.
  Giữ nguyên các thẻ `{{...}}`; muốn sửa một thẻ thì **xóa cả thẻ rồi gõ lại một lần**, không bôi đậm/đổi font một phần của thẻ.
  Máy chủ từ chối mẫu có thẻ bị cắt (nêu tên thẻ) hoặc thẻ không thuộc loại mẫu đó, file không phải `.docx` hoặc lớn hơn 5 MB.
  Mẫu cũ vẫn giữ: **Dùng lại** một phiên bản cũ hoặc **Dùng mẫu gốc**. Danh sách thẻ dùng được hiện ở từng loại mẫu
  (HĐ: xem mục "2. Sinh hợp đồng" bên dưới; quyết định: mục "Quyết định cử nhân sự"). Cả 5 loại đều có mẫu gốc
  (HĐLĐ, HĐ thử việc, thỏa thuận đào tạo – tuyển dụng, HĐ dịch vụ CNTT, quyết định cử nhân sự); HĐ thử việc, đào tạo, dịch vụ dùng
  cùng bộ thẻ với HĐLĐ. Các mục hệ thống không lưu để **HR sửa trong file Word sau khi tạo**: ngày ký ("Hôm nay, ngày … tháng …"),
  địa điểm làm việc (HĐ thử việc), tên/lộ trình/chi phí khóa đào tạo (HĐ đào tạo), ô tích vị trí chuyên môn, cách tính phí, thuế và
  Phụ lục 01 (HĐ dịch vụ; phí trọn gói lấy từ mức lương của HĐ).
- **PDF:** xem mục "Dịch vụ PDF" ở trên.

### Bảng lương

Menu **Bảng lương** chỉ dành cho **Kế toán và Quản trị**. HR, Ban giám đốc, Nhân viên không thấy menu, không thấy khối Bảng lương
trên hồ sơ và nhận 403 khi gọi API.

- **File mẫu:** bước 1 có nút **Tải file mẫu bảng lương** (đúng các cột hệ thống đọc được, kèm sheet hướng dẫn) cho lần
  import đầu hoặc khi chưa có file theo mẫu DOIT.
- **Import 4 bước** (**Bảng lương → Import bảng lương**). Lần import là một bản nháp lưu trên máy chủ: đóng trang rồi mở lại
  (danh sách "Bản nháp đang dở") vẫn làm tiếp được.
  1. **Tải file:** chọn kỳ (tháng/năm) và file `.xlsx` theo mẫu bảng lương DOIT (sheet "Bảng lương", tối đa 20 MB). File không phải
     Excel hoặc không có dòng tiêu đề "Mã nhân viên" bị từ chối ngay.
  2. **Nhận diện cột:** 36 trường của mẫu và cột tìm thấy; thiếu cột bắt buộc thì không đi tiếp được.
  3. **Kiểm tra:** lỗi và cảnh báo theo dòng (tải được báo cáo lỗi Excel).
     - Dòng **thiếu mã, trùng mã, hoặc mã chưa có hồ sơ** → **Chọn hồ sơ**: gợi ý theo họ tên (gõ không dấu cũng tìm được), chọn xong
       là hết lỗi.
     - Không có hồ sơ phù hợp → **Tạo hồ sơ rút gọn** ngay tại đây (họ tên, loại, mã; hợp đồng dịch vụ tự sinh mã `HDDVn`). Kế toán
       được tạo hồ sơ rút gọn nhưng **không sửa** hồ sơ có sẵn; HR bổ sung hồ sơ sau.
     - Mọi dòng phải gắn với một hồ sơ; một hồ sơ không gắn cho hai dòng.
     - Còn cảnh báo thì tích **Đã xem và xác nhận các cảnh báo**.
  4. **Xác nhận:** lưu thành **phiên bản mới** của kỳ (import lại cùng kỳ → phiên bản 2, 3…; bản cũ và file gốc vẫn giữ).
- **Duyệt:** **Quản trị** bấm **Duyệt kỳ** (phiên bản mới nhất thành phiên bản chính thức). Kỳ đã duyệt không import đè được. Chỉ
  **Quản trị** **Mở lại** (bắt buộc nhập lý do); lịch sử duyệt / mở lại xem được trên trang kỳ.
- **Xem:** trang kỳ có tổng thực nhận, tổng chi phí, các dòng (bật "Hiện đủ 36 khoản"), file gốc, **luật áp dụng và cảnh báo lúc
  xác nhận** (bản chụp, không chạy lại khi luật đổi). Hồ sơ nhân viên (tab Hợp đồng & lương) có khối **Bảng lương** theo kỳ.

### Phiếu lương

Sau khi Quản trị **duyệt** kỳ lương, Kế toán (hoặc Quản trị) mở trang kỳ → khối **Phiếu lương** → **Gửi phiếu lương cho người
chưa nhận**.

- **Nội dung phiếu** theo tab "Phiếu lương" của file bảng lương (các dòng a → q, cùng công thức), lấy số từ phiên bản đã duyệt.
  Dòng "Thực lĩnh" phải khớp cột "Lương thực nhận"; lệch quá 1 đồng thì không gửi người đó. Thực tập / HĐ dịch vụ (không có
  ngày công chuẩn) lấy thẳng "Lương gộp trong tháng". Ghi chú cuối phiếu (trần bảo hiểm, giảm trừ gia cảnh, căn cứ) in theo mốc luật
  đã lưu cùng phiên bản, kỳ cũ giữ mức cũ.
- **Gửi:** mỗi người một email tới **email trên hồ sơ**, từ `JHIPSTER_MAIL_FROM`, kèm `PhieuLuong_MM-YYYY_<mã>.pdf`. PDF mở bằng
  **6 số cuối CCCD** trên hồ sơ. Thân email không có số tiền. Cần cấu hình SMTP (biến `SPRING_MAIL_*`, xem mục "Tài khoản đăng nhập").
- **Tình trạng:** bảng từng người (Chưa gửi / Đã gửi lúc … / Lỗi: lý do). Lỗi thường gặp: "Chưa có email trên hồ sơ", "CCCD chưa đủ
  6 số", "Phiếu lệch bảng lương", "Dịch vụ PDF không chạy", lỗi máy chủ mail. Sửa hồ sơ rồi bấm **Gửi lại** trên dòng đó, hoặc bấm
  lại nút gửi chung (chỉ gửi người chưa nhận; không bao giờ gửi trùng). Nút **Tải** để xem trước phiếu (không mật khẩu).
- **Kỳ mở lại:** lượt chưa gửi kịp bị hủy ("Kỳ đã mở lại, chưa gửi"). Duyệt lại phiên bản mới thì người đã nhận hiện "Theo phiên bản
  cũ"; Kế toán chủ động gửi lại.
- **Nhân viên tự xem:** **Hồ sơ của tôi** → khối **Phiếu lương** (các kỳ đã gửi thành công, tải PDF không cần mật khẩu). Mọi lần tải
  phiếu được ghi nhật ký truy cập dữ liệu nhạy cảm.
- **Mẫu phiếu:** **Bảng lương → Mẫu phiếu lương** (Kế toán, Quản trị) – tải mẫu Word, sửa câu chữ, tải mẫu mới như các mẫu khác.
  HR không thấy mẫu này. Hệ thống không lưu file PDF phiếu lương; phiếu được sinh lại từ dữ liệu khi gửi hoặc tải.

### Cập nhật luật hằng năm (thuế TNCN, bảo hiểm)

Gợi ý Gross (hợp đồng, điều chỉnh lương) và kiểm tra bảng lương dùng **mức luật định theo ngày hiệu lực** lưu trong cơ sở dữ
liệu: giảm trừ gia cảnh, trần đóng BHXH/BHYT và BHTN, biểu thuế TNCN. Hệ thống nạp sẵn 6 mốc từ 07/2020 đến 07/2026. Kỳ sau tháng
"đã biết chắc luật" (nạp sẵn **12/2026**) vẫn tính được nhưng bị gắn cờ "ước tính". **Hạn: trước kỳ lương 01/2027**, và mỗi khi
có nghị định/luật mới:

1. Kiểm tra văn bản mới: lương cơ sở / mức tham chiếu → trần BHXH = 20 ×; lương tối thiểu vùng I → trần BHTN = 20 ×; giảm trừ
   gia cảnh; biểu thuế.
2. Quản trị vào **Danh mục → Mức luật định** (hoặc **Bảng lương → Mức luật định**), bấm **Thêm mốc**: tháng hiệu lực, các mức,
   các bậc thuế (bậc cuối để trống mức trên), căn cứ văn bản.
3. Đặt **Đã biết chắc luật đến tháng** tới tháng cuối cùng đã biết chắc luật.

**Luật đổi không tác động ngược:** mốc đã có không sửa, không xóa; mốc mới phải có hiệu lực **sau kỳ lương đã duyệt gần nhất**
(nếu không, hệ thống từ chối). Mỗi phiên bản bảng lương lưu lại mốc luật đã dùng và các cảnh báo lúc xác nhận, nên kỳ đã lưu
không bị kiểm tra lại theo luật mới. Gross đã lưu trên hợp đồng/điều chỉnh lương không bị tính lại; luật mới chỉ dùng cho dữ liệu
nhập sau đó. HR và Kế toán xem được các mốc; chỉ Quản trị thêm.

Lõi `core` có sẵn các mốc mặc định (`RuleBook.defaults()` trong `core/src/main/java/vn/doit/hr/payroll/RuleBook.java`) chỉ để
test và làm dữ liệu nạp ban đầu; sửa luật **không** cần sửa code.

### Nhắc việc và email

Hệ thống tự nhắc 4 loại việc của **nhân viên đang làm việc** (người đã nghỉ không có nhắc): hết hạn HĐLĐ (trước 45, 30, 15, 7 ngày
và đúng ngày; quá hạn chưa ký mới thì nhắc tiếp tới 30 ngày), kết thúc thử việc (trước 7, 3 ngày và đúng ngày; chưa ghi kết quả thì
nhắc hằng ngày tới 14 ngày), đến hạn xét tăng lương (trước 30, 7 ngày; quá hạn nhắc mỗi tuần tới 28 ngày) và sinh nhật.

- **Người nhận:** mọi tài khoản đang hoạt động có vai trò **HR, Kế toán, Quản trị** nhận tất cả; **quản lý trực tiếp** (trường
  "Quản lý" trên hồ sơ) nhận các mục về nhân viên của mình. Mỗi người nhận **một email tổng hợp mỗi ngày**; quản lý trùng email với
  tài khoản HR chỉ nhận một email. Email **không có số tiền**. Email lấy từ tài khoản, không có thì từ hồ sơ gắn với tài khoản;
  địa chỉ `@localhost` coi như chưa có.
- **Giờ gửi:** **7:30 sáng giờ Việt Nam, chỉ vào ngày làm việc**. Mốc nhắc rơi vào ngày nghỉ được gửi vào ngày làm việc liền trước.
  Ứng dụng khởi động sau 7:30 mà hôm nay chưa gửi được thì tự gửi bù. Tắt máy vài ngày thì lần sau chỉ gửi mốc gấp nhất, không dồn
  cả loạt. Gửi lỗi tới một địa chỉ thì lần chạy sau chỉ gửi lại cho địa chỉ đó.
- **Cấu hình SMTP:** dùng chung các biến `SPRING_MAIL_*` và `JHIPSTER_MAIL_FROM` ở mục "Tài khoản đăng nhập". `SPRING_MAIL_HOST`
  để trống hoặc `localhost` nghĩa là **chưa cấu hình**: không gửi email, trang tình trạng báo cảnh báo.
- **Tình trạng (Quản trị):** **Danh mục → Lịch làm việc**, khối **Email nhắc việc**: SMTP đã cấu hình chưa, lần gửi gần nhất (số
  người nhận, số mục, lỗi), ai chưa có email, nút **Gửi thử ngay** (gửi bản tổng hợp 30 ngày tới email của chính Quản trị, không
  tính là đã nhắc).
- **Lịch làm việc, ngày lễ:** cùng trang. Mặc định thứ Hai–thứ Sáu (thứ Bảy, Chủ nhật nghỉ); chỉ Quản trị đổi ngày làm việc trong
  tuần. Quản trị, HR khai báo ngày lễ theo năm (có nút **Chép từ năm trước**). Đổi lịch chỉ ảnh hưởng các lần nhắc sau.
- **Đơn chờ bạn duyệt:** người đang là người duyệt của đơn nghỉ / đơn làm thêm giờ đang chờ nhận thêm mục **"Đơn chờ bạn duyệt
  (N)"** ở đầu email (kể cả khi không có nhắc việc nào khác), mỗi ngày một lần. Chỉ đơn giao cho chính người đó: trưởng nhóm /
  trưởng phòng ghi trên đơn, mọi tài khoản BGĐ với đơn cấp BGĐ, Quản trị với đơn cấp Quản trị. HR và Quản trị duyệt thay được mọi
  đơn nhưng không nhận danh sách đơn không giao cho mình. Không có đơn chờ thì không có mục này. Email không có lý do nghỉ.
- **Dừng nhắc:** cập nhật dữ liệu là hết nhắc. Ký HĐ mới (hoặc gia hạn) cho nhân viên; ghi **Kết quả thử việc** trên HĐ thử việc;
  thêm lần điều chỉnh lương mới hoặc đặt **Hẹn xét lại ngày** trên lần điều chỉnh gần nhất; nhân viên nghỉ thì cập nhật nghỉ việc.
  HR và Quản trị làm được ngay trên trang **Tổng quan** (nút **Ghi kết quả thử việc**, **Hẹn xét lại**, **Thêm điều chỉnh lương**).

### Nghỉ phép và đơn từ

Chỉ người loại **NHAN_VIEN** (gồm thử việc) dùng phần này; thực tập và dịch vụ không có sổ phép, không gửi đơn, không có trong tổng
hợp. Thay đổi quy định không áp dụng ngược.

- **Sổ phép:** mỗi biến động là một dòng không sửa, không xóa (cộng tháng, số dư đầu kỳ, duyệt / hủy đơn, chuyển năm, hết hạn, HR
  điều chỉnh, chốt / hoàn tác khi nghỉ việc, giờ làm thêm thành nghỉ bù). Muốn sửa thì ghi dòng đảo ngược. Hai quỹ: **phép năm**
  (ngày) và **nghỉ bù** (giờ, 8 giờ = 1 ngày), mỗi quỹ hai ngăn **năm trước** / **năm nay**.
- **Phép năm:** mức năm 12 ngày (HR đặt 14 hoặc 16 trên hồ sơ ở ô **Mức phép năm**), +1 ngày mỗi đủ 5 năm làm việc từ tháng kế
  tiếp. Mỗi tháng cộng mức năm ÷ 12; tháng vào làm được cộng khi số ngày làm việc từ ngày vào ≥ 50% ngày làm việc của tháng. Được
  ứng tối đa 1 ngày (số dư thấp nhất −1). Đơn vị nghỉ 0,5 ngày; số được xin = số dư làm tròn xuống 0,5 + 1 ngày ứng.
- **Chuyển năm, hết hạn:** ngày 01/01 phần dương chuyển sang ngăn năm trước, số âm chuyển thành số âm của năm nay. Phần năm trước
  dùng đến hết **31/03** (cài đặt được), khi nghỉ thì trừ phần năm trước trước. Quỹ nghỉ bù theo cùng cách.
- **Loại đơn:** phép năm; việc riêng có lương (kết hôn 3 ngày, con kết hôn 1 ngày, tang 3 ngày); ốm (đính kèm giấy nghỉ hưởng
  BHXH, tải sau được; vượt mức 30/40/60 ngày theo số năm đóng BHXH chỉ cảnh báo); không lương; nghỉ bù (không cho ứng). Số ngày bị
  trừ là số ngày làm việc trong khoảng theo **Lịch làm việc** (bỏ cuối tuần, ngày lễ). Quỹ chỉ bị trừ khi đơn **được duyệt** (có
  khóa sổ theo người, không bao giờ xuống dưới −1); được xin cho ngày đã qua tối đa 30 ngày.
- **Làm thêm giờ:** đăng ký trước → người duyệt chọn **Trả tiền** hoặc **Nghỉ bù** → nhân viên xác nhận giờ thực tế (vượt đăng ký
  thì duyệt lại phần chênh; quá ngày 05 tháng sau tự tính bằng giờ đăng ký). Hệ số tự phân loại 150% / 200% (nghỉ hằng tuần) /
  300% (lễ), giờ đêm 22:00–06:00 chỉ đánh dấu. Giới hạn 40 giờ/tháng, 200 giờ/năm (cài đặt được). Nghỉ bù cộng giờ thực tế vào
  quỹ khi xác nhận xong. Tháng đã duyệt kỳ lương thì không đăng ký, duyệt hay đổi hình thức cho tháng đó nữa.
- **Luồng duyệt:** nghỉ ≤ 3 ngày làm việc và làm thêm giờ → **trưởng nhóm** ("Quản lý" trên hồ sơ); không có thì **trưởng phòng**
  (ô "Trưởng phòng" trên phòng ban); nghỉ > 3 ngày, hoặc người xin là trưởng phòng → **Ban giám đốc** (bất kỳ người BGĐ nào); người
  xin là BGĐ → **Quản trị**. Người duyệt đã nghỉ việc hoặc tài khoản bị khóa → lên cấp kế tiếp; không ai tự duyệt đơn của mình. **HR
  và Quản trị duyệt thay** được mọi đơn (ghi "duyệt thay"). Trạng thái: chờ duyệt → đã duyệt / từ chối (bắt buộc lý do) / đã rút;
  đơn đã duyệt muốn hủy thì người gửi **Xin hủy**, người duyệt đồng ý thì hoàn quỹ; ngày nghỉ đã qua thì chỉ HR hủy. Email báo người
  duyệt khi có đơn mới / yêu cầu hủy và báo người gửi khi có quyết định (lỗi gửi thư không làm hỏng việc duyệt).
- **Nghỉ việc:** khi áp dụng nghỉ việc, đơn sau ngày làm việc cuối tự hủy và hoàn quỹ (đơn bắc qua ngày cuối được rút ngắn), đơn đang
  chờ người này duyệt chuyển lên cấp kế tiếp, số dư được **chốt** (dòng "Chốt khi nghỉ việc") để kế toán trả tiền hoặc trừ lương.
  Hoàn tác nghỉ việc ghi dòng ngược lại.
- **Trang:** **Nghỉ phép** (số dư, tạo đơn, đơn đã gửi, lịch và sổ phép của mình); **Duyệt đơn** (người duyệt, HR, Quản trị; duyệt
  nhiều đơn một lần, xem số dư người xin); **Lịch nghỉ** (BGĐ, HR, Quản trị: cả công ty; trưởng nhóm: nhóm mình; trưởng phòng: phòng
  mình; lọc phòng ban, loại; lịch không hiện lý do nghỉ); tab **Nghỉ phép** trên hồ sơ nhân viên (HR, Quản trị, trưởng nhóm và
  trưởng phòng của người đó: số dư, đơn, sổ). Trưởng nhóm / trưởng phòng không mở được hồ sơ thì bấm **tên người** trong danh sách
  của một ngày trên Lịch nghỉ hoặc trên Duyệt đơn để xem cùng nội dung trong hộp thoại. Lý do nghỉ và giấy ốm chỉ người gửi, người duyệt của đơn, HR và Quản trị xem được.
- **Việc chạy tự động (giờ Việt Nam):** **00:10** hằng ngày: chuyển năm (từ 01/01), hết hạn phần năm trước (sau ngày hết hạn), cộng
  phép tháng hiện tại — chạy lại không ghi trùng; khởi động ứng dụng cũng chạy bù (`application.leave.catch-up-on-startup`).
  **00:20**: chuyển đơn chờ có người duyệt đã nghỉ / bị khóa lên cấp kế tiếp. **00:30**: tự xác nhận giờ làm thêm quá hạn. **07:30**
  ngày làm việc: mục "Đơn chờ bạn duyệt" trong email nhắc việc.
- **Quản lý nghỉ phép (HR, Quản trị):** số dư cả công ty (tìm, lọc phòng ban), xem sổ từng người, **Điều chỉnh** (cộng / trừ, chọn
  quỹ và ngăn, **bắt buộc lý do**), **Số dư đầu kỳ** và **Cài đặt**.
- **Số dư đầu kỳ (làm một lần khi bắt đầu dùng):** tải **file mẫu** (Mã NV, phép năm còn lại, trong đó phép năm trước, giờ nghỉ bù
  còn lại) → **Kiểm tra file** (không ghi gì, hiện lỗi từng dòng) → **Lưu** (kiểm tra lại, ghi tất cả hoặc không ghi gì; mỗi người
  mỗi quỹ chỉ một lần). **Lưu ý:** lần chạy bù khi khởi động bản đầu tiên đã **cộng phép của tháng hiện tại**, nên số đầu kỳ phải là
  số còn lại **trước** phần cộng tháng này (không thì người đó được cộng hai lần). Sai thì HR ghi dòng điều chỉnh.
- **Cài đặt:** số ngày được ứng, ngưỡng chuyển giám đốc duyệt (3 ngày), giới hạn làm thêm tháng / năm, hạn dùng phép năm trước
  (31/03), số ngày được xin cho ngày đã qua (30), hạn xác nhận giờ làm thêm (ngày 05). **Chỉ Quản trị sửa**, HR xem; quy định mới
  chỉ áp dụng cho các dòng ghi sau đó.
- **Tổng hợp nghỉ & làm thêm (Kế toán, HR, Quản trị):** mục cạnh **Bảng lương** (có đường dẫn từ trang Bảng lương), chọn tháng, tải
  Excel. Mỗi người NHAN_VIEN có làm việc trong tháng một dòng: số ngày **phép năm, việc riêng có lương, ốm, không lương, nghỉ bù**
  (ngày làm việc của đơn đã duyệt rơi vào tháng; đơn bắc qua tháng chia theo ngày), **giờ làm thêm trả tiền** đã hoàn tất theo tháng
  của ngày làm, tách **150% / 200% / 300%** kèm số lần có giờ đêm (đơn chọn nghỉ bù không vào đây), và **phép năm chốt khi nghỉ
  việc** của người có ngày làm việc cuối trong tháng: **dương = "Trả tiền phép chưa nghỉ"**, **âm = "Trừ lương phép ứng"** (kèm giờ
  nghỉ bù còn lại được chốt). Đơn nằm trọn trong tháng lấy đúng số ngày đã trừ khi duyệt (ngày lễ khai báo sau không làm đổi số);
  đơn bắc qua tháng chia theo ngày làm việc. Hoàn tác nghỉ việc khi kỳ lương tháng chốt **chưa duyệt** thì số chốt mất; khi kỳ đó
  **đã duyệt** thì số chốt giữ nguyên ở tháng cũ và tháng hoàn tác có dòng **đảo ngược** (số đối, ghi "Hoàn tác nghỉ việc").

### Tổng quan và báo cáo

**Tổng quan** (trang chủ của Quản trị, HR, Kế toán, Ban giám đốc): số nhân viên đang làm, đang thử việc, HĐ hết hạn trong 30 ngày,
danh sách **nhắc việc sắp tới** (30 ngày, việc quá hạn lên đầu, lọc theo loại) và biểu đồ **biến động 12 tháng** (tuyển mới, nghỉ
việc; có bảng số liệu). Quản trị và Kế toán thấy thêm **chi phí lương kỳ gần nhất** (theo phiên bản bảng lương chính thức).

**Báo cáo** (cùng các vai trò): chọn từ tháng – đến tháng (tối đa 36 tháng, mặc định 12 tháng gần nhất) và cách gom chi phí (phòng
ban hoặc vị trí), bấm **Tải báo cáo** để nhận file Excel: Tổng hợp, Biến động, Nghỉ việc. File của **Quản trị và Kế toán** có thêm
sheet **Chi phí lương**; HR và Ban giám đốc không có số tiền nào trong file. Biến động chỉ tính nhân viên có **ngày vào làm**.

## Backup & khôi phục

Container `backup` chạy `ops/backup/backup.sh` mỗi ngày lúc 02:00 (giờ Việt Nam):

- `pg_dump -Fc` + `tar czf` thư mục file vào volume `backups`, thư mục `/backups/YYYY-MM-DD/`; giữ 14 bản mới nhất.
- Ghi `status.json` (volume `backup-status`) – ứng dụng đọc để hiện **băng cảnh báo cho Quản trị** khi chưa cấu hình sao chép ngoài VPS, lần sao chép gần nhất không thành công (kèm lý do) hoặc bản backup cũ hơn 36 giờ. API: `GET /api/admin/backup-status`.
- Chạy tay: `docker compose exec backup /ops/backup/backup.sh`.

**Sao chép ra ngoài VPS (nên bật):** đặt `BACKUP_OFFSITE_RSYNC=user@host:/duong/dan` trong `.env`. Container dùng rsync qua SSH với khóa nằm trong thư mục `ops/backup/ssh/` (hoặc thư mục khác đặt bằng `BACKUP_SSH_DIR`), gắn **chỉ đọc** vào `/ssh`:

```bash
ssh-keygen -t ed25519 -N '' -f ops/backup/ssh/id_ed25519     # tạo khóa riêng cho backup
ssh-copy-id -i ops/backup/ssh/id_ed25519.pub user@host        # cài khóa công khai lên máy nhận
ssh-keyscan host > ops/backup/ssh/known_hosts                 # (khuyến nghị) ghim host key
chmod 600 ops/backup/ssh/id_ed25519
```

Không có `known_hosts` thì lần đầu tự chấp nhận host key. Thư mục `ops/backup/ssh/` đã được git bỏ qua; không commit khóa.

**Mã hóa bản ngoài VPS (bắt buộc khi sao chép ra ngoài):** bản đẩy ra ngoài là một file `YYYY-MM-DD/hrm-YYYY-MM-DD.tar.age`
mã hóa bằng [age](https://age-encryption.org). Tạo cặp khóa **trên máy của bạn**, không phải trên VPS:

```bash
age-keygen -o hrm-backup-key.txt      # in ra "Public key: age1..."
```

Điền public key vào `.env`: `BACKUP_AGE_RECIPIENT=age1...` (nhiều khóa: cách nhau bởi dấu phẩy) rồi `docker compose up -d`.
Cất `hrm-backup-key.txt` (private key) ở nơi an toàn ngoài VPS (két mật khẩu, USB cất giữ); mất file này thì không giải mã
được bản ngoài VPS. Thiếu `BACKUP_AGE_RECIPIENT` thì backup **không đẩy ra ngoài** (không để bản chưa mã hóa rời VPS) và băng
cảnh báo hiện lý do. Ngoài VPS giữ 30 bản mới nhất (`BACKUP_OFFSITE_KEEP`), bản cũ được dọn bằng rsync (tài khoản nhận chỉ
cần quyền rsync). Bản trên VPS không mã hóa (cùng vùng tin cậy với database). Nếu trước đây đã đẩy bản chưa mã hóa ra
ngoài, hãy xóa các thư mục cũ đó trên máy nhận. Kiểm tra script: `docker build -t hrm-backup-test ops/backup && docker run --rm -v "$PWD/ops/backup:/src:ro" hrm-backup-test bash /src/test/offsite-test.sh`.

**Khôi phục:** xem [`ops/backup/restore.md`](ops/backup/restore.md). Nên diễn tập khôi phục vào database trống định kỳ.

---

## DOIT HR Extension – core (`core/`)

Module Java (không phụ thuộc Spring) cho 2 chức năng:

1. **Import bảng lương Excel** theo đúng mẫu "BẢNG LƯƠNG NHÂN VIÊN" của DOIT, kèm kiểm tra lỗi trước khi lưu.
2. **Sinh hợp đồng lao động** từ mẫu Word có thẻ `{{...}}`, xuất PDF.

Module `app/` dùng lõi này như một thư viện Maven.

## Ghi chú test của lõi

Số test hiện tại xem mục "Phát triển và chạy test" ở đầu file.

| Phần | Test |
|---|---|
| Đọc cột, kiểm tra nghiệp vụ, thuế TNCN, số → chữ, map trường HĐ, nhắc hạn, lịch sử vị trí, báo cáo, chấm công | unit test trong `core/src/test`, gồm bảng lương 06/2026 thật (họ tên đã ẩn danh, `core/src/test/resources`) |
| `PoiSheetGrid` – đọc file .xlsx gốc bằng Apache POI | `PoiIntegrationTest`: 14 dòng, ô ngày → `LocalDate`, giá trị công thức cache, tổng thực nhận khớp GRAND TOTAL |
| `ContractGenerator`, `DocxTemplateRenderer` – điền mẫu bằng poi-tl | `PoiIntegrationTest`: không còn thẻ `{{...}}`, file ra ở `target/HDLD_test.docx`, `target/QD_test.docx` |

Test đọc file Excel gốc tự bỏ qua nếu không có file (mặc định tìm `../DO it - Bang luong thang 06_2026 them luon.xlsx`, đổi bằng `-Dpayroll.file=...`). File lương thật **không** nằm trong project.

## 1. Import bảng lương

```java
SheetGrid grid = PoiSheetGrid.read(uploadedFile.getInputStream());     // sheet "Bảng lương"
ParsedPayroll parsed = new PayrollSheetParser().parse(grid);
List<Issue> issues = new ArrayList<>(parsed.issues());
issues.addAll(new PayrollValidator().validate(parsed, YearMonth.of(2026, 6),
        code -> employeeRepository.findByCode(code).map(Employee::getFullName)));

boolean blocked = issues.stream().anyMatch(i -> i.severity() == Issue.Severity.ERROR);
// ERROR  → không cho lưu, hiển thị danh sách cho kế toán sửa file
// WARNING → cho lưu sau khi người duyệt bấm "Đã xem và xác nhận"
```

**Cách nhận diện cột:** theo *tên tiêu đề* (dòng 6 + dòng tiêu đề phụ 7), không theo chữ cái cột → kế toán chèn thêm cột vẫn import được. Danh sách 36 cột trong `PayrollField`.

**Giá trị đọc:** giá trị công thức đã lưu sẵn trong file (đúng số kế toán nhìn thấy). File phải được lưu bằng Excel/LibreOffice.

**Kỳ lương:** người upload chọn kỳ trên giao diện; tiêu đề "Tháng MM/YYYY" trong file chỉ dùng để cảnh báo lệch.

### Các kiểm tra

| Mức | Kiểm tra |
|---|---|
| ERROR | Thiếu mã NV · Trùng mã trong file · Mã không có trong hệ thống · Ô lỗi công thức (#N/A, #REF!) · Ô số chứa chữ · Thiếu cột bắt buộc |
| WARNING | Kỳ trong file ≠ kỳ chọn · Họ tên khác hồ sơ · Cột tổng không khớp các cột thành phần (ô công thức bị gõ đè) · Có ngày ký HĐLĐ nhưng không đóng BH · Lương đóng BHXH/BHTN sai trần · Tỉ lệ BH người LĐ ≠ 8% / 1,5% / 1% · Giảm trừ gia cảnh ≠ mức luật · Thuế TNCN ≠ biểu lũy tiến hiện hành |

Mức luật định nằm trong `PayrollRules`, theo ngày hiệu lực từ 07/2020 – cách cập nhật xem mục "Cập nhật luật hằng năm".

### Gợi ý lưu trữ (JPA)

```
payroll_period (id, ky YYYY-MM, trang_thai [NHAP|DA_DUYET|DA_GUI_PHIEU], file_goc_id, nguoi_import, thoi_gian)
payroll_line   (id, period_id, employee_id, version, field_code, so_tien)   -- hoặc 1 cột/field nếu muốn query dễ
```
Import lại cùng kỳ → tạo `version` mới, giữ bản cũ. Lưu luôn file Excel gốc để đối chiếu.

## 2. Sinh hợp đồng

```java
try (InputStream tpl = getClass().getResourceAsStream("/templates/DOIT_HDLD_template.docx");
     OutputStream out = Files.newOutputStream(docxPath)) {
    new ContractGenerator().render(tpl, contractData, out);
}
Path pdf = new PdfConverter("soffice").toPdf(docxPath);
```

Server cần LibreOffice và font Times New Roman (gói `ttf-mscorefonts-installer` trên Ubuntu), nếu không PDF sẽ bị thay font.

### Các thẻ trong mẫu

| Thẻ | Nguồn | Thẻ | Nguồn |
|---|---|---|---|
| `soHopDong` | sinh tự động `DOIT/{năm}/HĐLĐ/{số}` | `loaiHopDong` | HĐ |
| `hoTen` | hồ sơ (in hoa) | `thoiHan`, `tuNgay`, `denNgay` | HĐ (ngày kết thúc = bắt đầu + thời hạn − 1 ngày) |
| `gioiTinh`, `ngaySinh`, `trinhDo` | hồ sơ | `boPhan`, `viTri` | hồ sơ / HĐ |
| `noiCuTru`, `dienThoai`, `email` | hồ sơ | `mucLuong`, `mucLuongBangChu` | HĐ (bằng chữ tự sinh) |
| `soCanCuoc`, `ngayCap`, `noiCap` | hồ sơ | `phuCap` | HĐ (danh sách phụ cấp) |
| `soTaiKhoan`, `nganHang` | hồ sơ | `cheDoNangLuong` | HĐ |
| `nguoiDaiDien` | cấu hình công ty | | |

HR tự sửa câu chữ trong Word thoải mái, chỉ cần **gõ lại cả thẻ trong một lần** (không định dạng một nửa thẻ) để Word không cắt thẻ ra nhiều đoạn.

## Chạy test

```bash
mvn -pl core test   # từ thư mục gốc repo
```

## 3. Nhắc hạn (package `vn.doit.hr.reminder`)

Nhắc 4 loại sự kiện. Mỗi người nhận chỉ nhận **một email tổng hợp mỗi ngày**.

| Sự kiện | Nhắc trước (mặc định) | Khi quá hạn mà chưa xử lý | Người nhận |
|---|---|---|---|
| Hết hạn HĐLĐ xác định thời hạn | 45, 30, 15, 7, 0 ngày | Ngày 1, rồi cứ 3 ngày một lần, đến ngày 30 (hạn ký HĐ mới theo Điều 20.2 BLLĐ 2019) | HR, quản lý (+ giám đốc khi quá hạn) |
| Hết thử việc | 7, 3, 0 ngày | Mỗi ngày, tối đa 14 ngày (Điều 27: phải thông báo kết quả) | HR, quản lý (+ giám đốc) |
| Đến hạn xét tăng lương | 30, 7 ngày | Mỗi 7 ngày, tối đa 28 ngày | HR, quản lý / giám đốc |
| Sinh nhật | 3, 0 ngày | — | HR, quản lý |

Quy tắc đáng chú ý (đều có test):
- **Ngày nhắc rơi vào Chủ nhật/ngày lễ** → nhắc vào ngày làm việc liền trước (lịch thứ Hai–thứ Bảy theo HĐLĐ của DOIT).
- **Server nghỉ vài ngày** → lần chạy sau chỉ gửi **một** mốc gấp nhất, không dồn cả loạt.
- **Đã ký 2 HĐ xác định thời hạn liên tiếp** → lời nhắc ghi rõ HĐ tiếp theo phải là HĐ không xác định thời hạn.
- **Dừng nhắc** khi: ký HĐ mới, ghi nhận kết quả thử việc, ghi nhận lần điều chỉnh lương (hoặc hẹn ngày xét lại), hoặc nhân viên nghỉ việc.
- Gia hạn HĐ (đổi ngày kết thúc) được coi là sự kiện mới, chu kỳ nhắc tự chạy lại từ đầu.

### Dữ liệu cần lưu thêm (HR Extension)

OrangeHRM có sẵn ngày sinh và trạng thái nghỉ việc. Lịch sử nhiều HĐ, thử việc và tăng lương thì nên lưu ở HR Extension. HĐ sinh từ module ở mục 2 sẽ tự ghi vào `employee_contract`.

```sql
CREATE TABLE employee_contract (
  id BIGSERIAL PRIMARY KEY, employee_code VARCHAR(20) NOT NULL,
  contract_no VARCHAR(50) NOT NULL, start_date DATE NOT NULL, end_date DATE,   -- NULL = không xác định thời hạn
  file_id BIGINT, created_at TIMESTAMP NOT NULL DEFAULT now());
CREATE TABLE probation (
  employee_code VARCHAR(20) PRIMARY KEY, start_date DATE, end_date DATE NOT NULL,
  result VARCHAR(10), result_date DATE);                                       -- result NULL = chưa có kết quả
CREATE TABLE salary_change (
  id BIGSERIAL PRIMARY KEY, employee_code VARCHAR(20) NOT NULL, effective_date DATE NOT NULL,
  base_salary BIGINT, review_cycle_months INT DEFAULT 12, next_review_override DATE, note TEXT);
CREATE TABLE reminder_log (
  event_key VARCHAR(120) NOT NULL, step INT NOT NULL, sent_at TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (event_key, step));
```

`ReminderLog` trên bảng này:
- `smallestLeadSent(k)` = `SELECT MIN(step) FROM reminder_log WHERE event_key = ? AND step >= 0`
- `overdueSent(k, d)` = có dòng `step = -d`

### Job hằng ngày (Spring – mẫu, chưa compile)

```java
@Scheduled(cron = "0 30 7 * * MON-SAT", zone = "Asia/Ho_Chi_Minh")
@SchedulerLock(name = "hr-reminders")            // ShedLock, nếu chạy nhiều instance
public void sendDailyReminders() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    List<Reminder> reminders = engine.run(today, snapshotService.activeEmployees(), reminderLogRepo);
    ReminderDigest.byEmail(reminders, recipientResolver).forEach((email, items) -> {
        mailSender.send(email, ReminderDigest.subject(today, items), ReminderDigest.renderText(today, items));
        items.forEach(r -> reminderLogRepo.save(r.eventKey(), r.step()));   // ghi SAU khi gửi thành công
    });
}
```

Lưu ý: chỉ ghi `reminder_log` sau khi email gửi thành công. Nếu gửi lỗi, lần chạy sau sẽ tự gửi lại.

## 4. Lịch sử vị trí làm việc (package `vn.doit.hr.assignment`)

Lưu nhân viên làm ở đâu theo từng giai đoạn: văn phòng, onsite tại khách hàng, làm từ xa, đi công tác.

### Hai lớp vị trí

| Lớp | Loại | Quy tắc |
|---|---|---|
| Nơi làm việc chính | `VAN_PHONG`, `KHACH_HANG`, `TU_XA` | Tại mỗi thời điểm chỉ có 1 nơi chính, không chồng lấn |
| Tạm thời | `CONG_TAC` | Bắt buộc có ngày về; đè lên nơi chính trong những ngày đi; các đợt công tác không chồng lấn nhau |

### Ghi lịch sử – hệ thống tự làm

- **Cử đi onsite có thời hạn** (vd 01/11/2026 – 31/01/2027): tự chốt văn phòng đến 31/10/2026, rồi tự tạo giai đoạn **quay về văn phòng** từ 01/02/2027. HR chỉ cần nhập 1 dòng.
- **Điều chuyển hẳn** (không có ngày kết thúc): chỉ chốt nơi cũ, không tạo giai đoạn quay về.
- **Về sớm hoặc gia hạn** (`changeEnd`): ngày quay về văn phòng được dời theo, không để hở ngày nào.
- **Từ chối** khi: chồng lấn thời gian, ngày kết thúc trước ngày bắt đầu, công tác không có ngày về, thiếu tên đơn vị, hoặc chèn giai đoạn chính vào quá khứ đè lên dữ liệu đã có.
- **Không bao giờ xóa.** Mỗi thao tác trả về danh sách `AssignmentChange` (CREATED / CLOSED / RESUMED / END_CHANGED / ADJUSTED) để ghi audit.

### Tra cứu và báo cáo

| Hàm | Dùng cho |
|---|---|
| `locationOn(ngày)` | Hôm đó nhân viên làm ở đâu (đang công tác thì trả về nơi công tác). Sau này dùng cho **chấm công GPS**: biết vị trí hợp lệ để chấm công |
| `timeline(từ, đến)` | Dòng thời gian theo giai đoạn để hiển thị trên hồ sơ |
| `workingDaysByPlace(từ, đến, lịch)` | Số ngày làm việc tại từng nơi: đối soát với khách hàng, tính công tác phí |
| `gaps(từ, đến)` | Các khoảng chưa có dữ liệu, để HR bổ sung |
| `AssignmentReport.whoIsWhere(ds, ngày)` | Ai đang ở đâu trong một ngày |

### Quyết định cử nhân sự

Mẫu `templates/DOIT_QD_CuNhanSu_template.docx` dùng chung cho 2 trường hợp: đi công tác và làm việc tại khách hàng (19 thẻ, lấy từ `AssignmentDecision.toFields()`). Luồng xử lý:

```java
AssignmentHistory.Result r = history.add(decision.assignment());   // kiểm tra trước, lỗi thì dừng
try (InputStream tpl = getClass().getResourceAsStream("/templates/DOIT_QD_CuNhanSu_template.docx")) {
    new DocxTemplateRenderer().render(tpl, decision.toFields(), out);
}
repository.saveAll(r.changes());                                   // cùng 1 transaction với việc lưu quyết định
```

Mẫu được sinh bằng `core/tools/make_decision_template.py`. HR có thể sửa thẳng trong Word.

```sql
CREATE TABLE work_assignment (
  id BIGSERIAL PRIMARY KEY, employee_code VARCHAR(20) NOT NULL,
  type VARCHAR(20) NOT NULL,            -- VAN_PHONG | KHACH_HANG | TU_XA | CONG_TAC
  unit VARCHAR(200), address VARCHAR(300),
  from_date DATE NOT NULL, to_date DATE, -- NULL = đến nay
  decision_no VARCHAR(50), decision_file_id BIGINT, note TEXT);
CREATE INDEX ix_assignment_emp ON work_assignment(employee_code, from_date);
CREATE TABLE work_assignment_audit (
  id BIGSERIAL PRIMARY KEY, assignment_id BIGINT, kind VARCHAR(20) NOT NULL,
  before_json JSONB, after_json JSONB, changed_by VARCHAR(100), changed_at TIMESTAMP NOT NULL DEFAULT now());
```

**Nhập dữ liệu ban đầu:** mỗi nhân viên tạo 1 dòng `VAN_PHONG` (hoặc `KHACH_HANG`) bắt đầu từ ngày vào làm, rồi nhập các đợt onsite/công tác đã biết theo thứ tự thời gian. `gaps()` sẽ chỉ ra chỗ còn thiếu.

## 5. Báo cáo nhân sự (package `vn.doit.hr.report`)

Có 3 báo cáo, xuất chung ra một file Excel 4 sheet (`ReportExcelWriter`): **Tổng hợp**, **Biến động**, **Nghỉ việc**, **Chi phí lương**.

| Báo cáo | Nội dung | Nguồn dữ liệu |
|---|---|---|
| `HeadcountReport` – biến động | Theo từng tháng: đầu kỳ, tuyển mới, nghỉ việc (trong đó tự nghỉ), cuối kỳ, bình quân, tỷ lệ nghỉ việc; nhân sự cuối kỳ theo phòng ban | Ngày vào làm, ngày nghỉ việc, phòng ban (OrangeHRM) |
| `TurnoverReport` – nghỉ việc | Phân theo hình thức nghỉ, thâm niên khi nghỉ, phòng ban, lý do; số người nghỉ trong 6 tháng đầu; danh sách người nghỉ | Như trên, kèm hình thức và lý do nghỉ |
| `PayrollCostReport` – chi phí lương | Theo tháng: lương theo ngày công, thưởng, phụ cấp, làm thêm giờ, BH công ty đóng, KPCĐ, tổng chi phí, bình quân/người, % so với tháng trước; chia theo vị trí hoặc phòng ban | Bảng lương đã import (mục 1) |

### Quy ước tính

- **Ngày nghỉ việc** là ngày làm việc cuối cùng. Người nghỉ đúng ngày cuối tháng không được tính vào cuối kỳ của tháng đó. Nhờ vậy luôn có: **Cuối kỳ = Đầu kỳ + Tuyển mới − Nghỉ việc** (có test kiểm tra).
- **Tỷ lệ nghỉ việc** = số người nghỉ ÷ nhân sự bình quân, với nhân sự bình quân của tháng = (đầu kỳ + cuối kỳ) / 2. Tỷ lệ cả kỳ dùng trung bình các tháng.
- **Tổng chi phí lương** = lương theo ngày công + thưởng + phụ cấp + làm thêm giờ + khoản khác + BH công ty đóng + KPCĐ + thừa/thiếu. Công thức này khớp cột "Tổng chi phí nhân viên" của bảng lương DOIT. Đã đối chiếu với bảng lương 06/2026 thật: 245.709.000 đ, đúng bằng dòng GRAND TOTAL.
- Bảng lương có cả HĐ dịch vụ và thực tập sinh thì số liệu chi phí cũng gồm họ, được tách thành nhóm riêng khi chia theo vị trí.

```java
HrReport report = HrReport.build("CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT",
        YearMonth.of(2026, 1), YearMonth.of(2026, 6),
        employmentRecords,                     // từ OrangeHRM
        payrollsByMonth,                       // SortedMap<YearMonth, List<PayrollLine>> từ các lần import
        line -> departmentOf(line.employeeCode()), "Phòng ban");
try (OutputStream out = Files.newOutputStream(Path.of("BaoCaoNhanSu_2026H1.xlsx"))) {
    new ReportExcelWriter().write(report, out);
}
```

**Dữ liệu cần thêm:** để báo cáo nghỉ việc có ý nghĩa, mỗi lần cho nhân viên nghỉ, HR phải chọn **hình thức nghỉ** (tự nghỉ / hết hạn HĐ / không đạt thử việc / công ty chấm dứt / khác) và ghi **lý do**. OrangeHRM có sẵn màn hình nghỉ việc với ngày và lý do; danh sách lý do nên cấu hình khớp với 5 hình thức trên.

## 6. Chấm công GPS/WiFi – phần server (package `vn.doit.hr.attendance`)

Phần lõi kiểm tra từng lần chấm công và tính bảng công. App Android sẽ gọi vào phần này (bước tiếp theo).

### Kiểm tra một lần chấm công (`PunchValidator`)

Các bước theo thứ tự. Bước nào từ chối thì dừng tại đó.

| # | Kiểm tra | Kết quả |
|---|---|---|
| 1 | Thiết bị: mỗi nhân viên gắn 1 điện thoại | Thiết bị lạ → từ chối · Thiết bị đầu tiên → chờ HR xác nhận |
| 2 | Vị trí giả lập (Android báo `isMock`) | Từ chối |
| 3 | Giờ điện thoại lệch giờ server > 5 phút | Từ chối. Giờ ghi nhận luôn là giờ server |
| 4 | Hôm nay phải làm ở đâu: lấy `locationOn(ngày)` từ lịch sử vị trí (mục 4) | Chưa có dữ liệu → chờ duyệt |
| 5 | Làm từ xa | Chấp nhận, không cần vị trí |
| 6 | Đúng WiFi (BSSID) hoặc đúng IP public của điểm chấm công | Chấp nhận (tin cậy hơn GPS trong tòa nhà) |
| 7 | GPS (bán kính mặc định **200 m**, đặt riêng cho từng điểm được) | Trong bán kính → chấp nhận · Sát ranh giới (trong bán kính + sai số) hoặc sai số > 100 m → chờ duyệt · Xa hơn → từ chối, báo rõ đang cách bao nhiêu mét và hôm nay phải làm ở đâu |

Nhờ bước 4, nhân viên đang onsite ở khách hàng mà chấm công tại văn phòng công ty sẽ bị từ chối. Ngày đi công tác thì điểm hợp lệ tự chuyển sang nơi công tác. Nơi chưa khai báo điểm chấm công thì chờ duyệt chứ không từ chối, để nhân viên không bị mất công vì lỗi dữ liệu.

**Giới hạn cần biết:** máy đã root hoặc jailbreak vẫn có thể giả GPS, giả BSSID mà không bị báo `isMock`. Kiểm tra theo IP public do server tự thấy nên khó giả nhất. Với văn phòng công ty, nên ưu tiên WiFi/IP và chỉ dùng GPS làm phương án dự phòng.

### Tính công (`WorkSchedule`, `Timesheet`)

- **Lịch làm việc:** thứ Hai–thứ Sáu 08:00–17:00 (nghỉ trưa 12:00–13:00), cho phép muộn 5 phút. Ngày lễ khai báo riêng.
- **Thứ Bảy có 2 chế độ** (`SaturdayMode`):
  - `NGHI` – **mặc định, đúng thực tế hiện nay**: thứ Bảy là ngày nghỉ, ai đi làm được ghi "Làm ngày nghỉ" và tính qua quy trình OT;
  - `LAM_NUA_NGAY` – theo HĐLĐ mẫu: thứ Bảy 08:00–12:00, đủ giờ = 0,5 công.
- **Giờ vào / giờ ra:** lần chấm VÀO sớm nhất / lần chấm RA muộn nhất trong ngày. Chỉ tính phần nằm trong ca, trừ giờ nghỉ trưa. Làm ngoài ca không tự tính, phải đăng ký OT.
- **Công ngày:** trọng số ngày × giờ làm ÷ giờ chuẩn, làm tròn xuống 0,01. Thứ Hai–Sáu đủ giờ = 1 công. Vào muộn trong 5 phút cho phép thì coi như vào đúng giờ, không bị trừ công.
- **Thiếu giờ vào hoặc giờ ra:** tạm tính 0 công và đưa vào danh sách cần giải trình.
- **Tổng hợp tháng:** công chuẩn, công thực tế, số lần và số phút đi muộn, số lần về sớm, ngày vắng, ngày thiếu chấm công.

### Giải trình và duyệt chấm công (`AttendanceCorrections`)

Có 3 đường để một lần chấm công được tính, ngoài trường hợp app chấp nhận ngay:

| Đường | Ai làm | Khi nào |
|---|---|---|
| **Duyệt lần chấm chờ duyệt** | Quản lý bấm Duyệt / Từ chối | Lần chấm trên app ở trạng thái chờ duyệt (GPS kém, sát ranh giới, thiết bị mới, nơi chưa khai báo) |
| **Giải trình** | Nhân viên gửi giờ vào/ra kèm lý do → quản lý duyệt | Lần chấm bị từ chối, quên chấm, app lỗi |
| **Chấm tay** | Quản lý ghi trực tiếp giờ vào/ra, bắt buộc có lý do | Điện thoại hỏng, hết pin, nhân viên không dùng được app |

Quy tắc (đều có test):
- Chỉ **quản lý trực tiếp hoặc HR** được duyệt hoặc chấm tay; **không ai tự duyệt cho mình**.
- Từ chối hoặc chấm tay **bắt buộc ghi lý do**. Mọi quyết định lưu người duyệt và thời điểm.
- Không giải trình hay chấm tay trước cho thời điểm chưa tới. Giờ đề xuất phải nằm đúng ngày được giải trình.
- **Khóa công:** sau ngày 05 của tháng sau (`LockPolicy`, đổi được), công tháng trước không sửa được nữa, để bảng lương không bị thay đổi sau khi đã chốt.
- Bảng công ghi rõ nguồn của từng lần chấm: chấm trên app, giải trình đã duyệt, hay quản lý chấm tay.

### API cho app (đề xuất)

```
POST /api/attendance/punches
{ "type": "IN", "clientTime": "2026-10-19T00:58:12Z", "deviceId": "…",
  "latitude": 21.0279, "longitude": 105.8360, "accuracy": 15.0, "mock": false, "wifiBssid": "aa:bb:cc:dd:ee:01" }
→ 200 { "status": "ACCEPTED", "method": "GPS", "serverTime": "…", "site": "Văn phòng DOIT", "message": "Đã chấm công tại Văn phòng DOIT" }
```
- Mã nhân viên lấy từ token đăng nhập, không lấy từ body.
- `clientIp` do server tự đọc (nếu sau reverse proxy thì lấy từ `X-Forwarded-For` của proxy tin cậy).

### Dữ liệu vị trí là dữ liệu cá nhân

Luật Bảo vệ dữ liệu cá nhân và Nghị định 356/2025/NĐ-CP (thay Nghị định 13/2023) có hiệu lực từ 01/01/2026. Dữ liệu vị trí thuộc nhóm phải thông báo cho người bị ảnh hưởng trong 72 giờ nếu bị lộ. Thiết kế hiện tại:
- **Chỉ lấy vị trí đúng lúc bấm chấm công,** không theo dõi nền, không chạy dịch vụ định vị liên tục.
- **Bảng công chỉ cần kết quả** (điểm chấm công, khoảng cách, trạng thái). Nên **xóa tọa độ gốc sau một thời hạn** (vd 90 ngày, sau khi chốt công và hết hạn khiếu nại), và xóa dữ liệu chấm công khi nhân viên nghỉ việc theo quy định.
- **Cần văn bản thông báo / xin đồng ý** của nhân viên về việc thu thập vị trí khi chấm công (mục đích, thời hạn lưu, ai được xem). Phần này nên nhờ người phụ trách pháp lý soạn. Tôi không phải luật sư.
