# Changelog - AGY Remote & Antigravity Global System

Tất cả các thay đổi, bản phát hành ứng dụng và điều chỉnh hệ thống được ghi chép chi tiết dưới đây theo chuẩn Semantic Versioning và Conventional Commits.

---

## 📱 Phiên bản Ứng dụng AGY Remote (App Releases)

### [1.0.2] - 2026-09-06
- **Commit**: `2c44db4`
- **Loại thay đổi**: `fix`, `feat`
- **Tiêu đề**: `fix: update app branding to agy-remote-debug 1.0.2, fix send button, and restrict notifications to final conclusions`
- **Chi tiết thay đổi**:
  - **Định danh & Bản build**: Cập nhật tên ứng dụng hiển thị (App Name) và tên file APK thành `agy-remote-debug 1.0.2.apk` lưu tại `D:\apk` (duy trì đúng 1 bản duy nhất theo Quy tắc 7).
  - **Sửa nút gửi tin nhắn (Send Button)**:
    - Phóng to nút Send lên kích thước 44x44px trên di động, hỗ trợ `touch-action: manipulation` và bo góc mềm mại.
    - Tích hợp `window.__agyTriggerSend()` kích hoạt trực tiếp hàm `onClick` từ React Fiber props và bắt sự kiện `pointerdown` / `touchend` tức thì.
    - Bổ sung nút gửi nhanh trên thanh trạng thái gốc `TopStatusBar` (giao diện Compose) để luôn gửi được dù bàn phím ảo che khuất.
  - **Sửa lỗi nạp WebView / Trắng trang**:
    - Loại trừ các file bundle lớn (`/main.js` 9MB, `/prism_bundle.js`) khỏi bộ đệm nhị phân đồng bộ của OkHttp trong `AgyResourceCache`, giải quyết triệt để lỗi timeout và file cache rỗng.
  - **Khắc phục triệt để 100% thông báo rác**:
    - Nâng cấp `bridge/session_watcher.js` v7.0: Khởi tạo chỉ mục toàn bộ các bước cũ trên hệ thống, chỉ quét các file hoạt động trong 2 phút gần nhất, không bao giờ phát lại thông báo từ lịch sử cũ.
    - Lọc bỏ hoàn toàn các thẻ `<USER_REQUEST>`, lệnh tool execution (`tool_calls`, `type: GENERIC`), chỉ thông báo khi Agent đưa ra kết luận hoàn tất thực sự (`PLANNER_RESPONSE` có nội dung sạch).
    - Tự động tắt âm thanh/rung và thông báo nổi khi người dùng đang mở ứng dụng ở tiền cảnh (`isAppInForeground = true`), tránh làm phiền khi đang nhìn màn hình.
    - Duy nhất 1 ID thông báo (`NOTIFICATION_ALERT_ID`), tự động ghi đè thông báo cũ và tự động xóa sạch khi mở app.

---

### [1.0.1] - 2026-09-05
- **Commit**: `4b14170`
- **Loại thay đổi**: `chore`, `fix`
- **Tiêu đề**: `chore: bump version to 1.0.1 and display version in status bar and connection dialog`
- **Chi tiết thay đổi**:
  - Đánh dấu phiên bản `v1.0.1` hiển thị trực tiếp trên thanh `TopStatusBar` và `ConnectionDialog`.
  - Tăng cường khả năng chống rung (debounce) cho hệ thống thông báo tác vụ hoàn thành.
  - Nâng cấp độ tin cậy của bộ đệm tài nguyên mạng LAN.

---

### [1.0.0] - 2026-09-05
- **Commit**: `2b28517`
- **Loại thay đổi**: `feat`, `docs`
- **Tiêu đề**: `feat: initial release of AGY Remote Android Client with local bridge`
- **Chi tiết thay đổi**:
  - Khởi tạo ứng dụng Android Native (Jetpack Compose) kết hợp WebView điều khiển Antigravity từ xa qua mạng nội bộ LAN.
  - Tích hợp Node.js Bridge Server giám sát tiến trình và đồng bộ hóa thời gian thực qua WebSocket.
  - Cơ chế SPA Navigation 0ms không reload trang, bảo tồn trạng thái đa tab.
  - Hỗ trợ tải và chèn ảnh trực tiếp từ thư viện thiết bị và clipboard vào phiên chat.
  - Bổ sung tài liệu mã nguồn mở `README.md` và giấy phép MIT.

---

## 📜 Bộ Quy tắc Toàn cục AGY (Global Guidelines History)

### [v1.7.0] - 2026-09-04
- **Commit**: `f74813e`
- **Loại thay đổi**: `docs`
- **Tiêu đề**: `docs(rules): update system rules to v1.7.0`
- **Chi tiết thay đổi**:
  - Cập nhật chuẩn hóa bộ quy tắc AGY toàn cục áp dụng cho mọi phiên làm việc.
  - Tối ưu hóa điều phối Đa Agent Song song và chính sách xuất file APK cố định tại `D:\apk`.

---

## [v1.6.1] - 2026-09-04
- **Commit**: `eb97135` (`eb97135eb70dbd473173e1ab8d2ec56ecf9ed121`)
- **Loại thay đổi**: `docs`
- **Tiêu đề**: `docs(rules): compact global rules to optimize token consumption v1.6.1`
- **Chi tiết thay đổi**:
  - Tinh giản và cô đọng văn bản của toàn bộ 9 điều luật, giảm độ dài từ 143 dòng xuống ~45 dòng (tiết kiệm hơn 80% dung lượng token).
  - Giữ nguyên 100% nội dung, ý nghĩa và tính nghiêm ngặt của từng điều luật (từ Điều 1 đến Điều 9).
  - Rút gọn bảng lịch sử phiên bản để tối ưu tải context.

---
## [v1.6.0] - 2026-09-04
- **Commit**: `06214ae` (`06214aeef5e3ed1b134ca61db97cbbfa29f6b235`)
- **Loại thay đổi**: `feat`
- **Tiêu đề**: `feat(rules): require plan approval before execution with direct edit exception v1.6.0`
- **Chi tiết thay đổi**:
  - Cập nhật **Điều luật số 5: Quy trình Lập kế hoạch & Chốt Phương án (Workflow & Plan Approval)**.
  - Bắt buộc chốt phương án: Mọi yêu cầu mới đều phải trình bày phương án rõ ràng và chỉ khi người dùng đồng ý mới được triển khai.
  - Ngoại lệ chỉnh sửa trực tiếp: Đối với yêu cầu chỉnh sửa cụ thể, fix bug trực tiếp, vi chỉnh theo feedback hoặc tác vụ nhỏ theo Mục 7 thì được phép sửa luôn ngay lập tức.

---

## [v1.5.0] - 2026-09-04
- **Commit**: `0ca6644` (`0ca66444005b63cf3a19bda5324395ad602e1c94`)
- **Loại thay đổi**: `feat`
- **Tiêu đề**: `feat(rules): add rule 9 for single apk storage at D:\apk v1.5.0`
- **Chi tiết thay đổi**:
  - Bổ sung chính thức **Điều luật số 9: Quản lý Xuất File APK (APK Build & Storage Policy)**.
  - **Đường dẫn Lưu trữ Cố định**: Mọi bản build APK hoặc file APK xuất ra bắt buộc luôn luôn lưu vào thư mục `D:\apk`.
  - **Nguyên tắc Một Bản Duy Nhất (Single Instance)**: Chỉ duy trì đúng 1 bản APK duy nhất tại `D:\apk` (tự động ghi đè bản cũ cùng app hoặc dọn dẹp các bản build trước đó), tuyệt đối không lưu rải rác nhiều bản build thừa.

---

## [v1.4.1] - 2026-09-04
- **Commit**: `cd65650` (`cd65650d244f0b240fcb1a62933d6a362bf784b2`)
- **Loại thay đổi**: `feat`
- **Tiêu đề**: `feat(rules): add rule 8 for versioning and commit changelog tracking v1.4.1`
- **Chi tiết thay đổi**:
  - Ghi nhận chính thức **Điều luật số 8: Đánh dấu Phiên bản & Nhật ký Thay đổi kèm Mã Commit (Versioning & Commit Tracking)** trực tiếp vào bộ luật GEMINI.md và AGENTS.md.
  - Bắt buộc gắn số phiên bản theo Semantic Versioning (vX.Y.Z) cho mọi lần sửa đổi quy tắc hoặc cấu hình.
  - Bắt buộc cập nhật file CHANGELOG.md ghi nhận rõ mã Git Commit Hash, ngày giờ và mô tả chi tiết.
  - Tích hợp bảng tóm tắt Lịch sử Phiên bản (Version History) trực tiếp ở cuối tài liệu luật.

---
## [v1.4.0] - 2026-09-04
- **Commit**: `73ea292` (`73ea29211e842f35bf78fe406775b08ddab251f8`)
- **Loại thay đổi**: `feat`
- **Tiêu đề**: `feat(rules): add fast-track rule for minor edits v1.4.0`
- **Chi tiết thay đổi**:
  - Bổ sung **Điều luật số 7: Xử lý Tác vụ Nhỏ Siêu Tốc (Fast-Track for Minor Edits)**.
  - Phạm vi áp dụng: Vi chỉnh text, đổi nhãn, sửa lỗi chính tả (typo), căn lề/margin/padding CSS, đổi hằng số đơn giản, chỉnh layout nhẹ.
  - Tối ưu tốc độ: Xử lý trực tiếp ngay lập tức, không lập kế hoạch rườm rà (`implementation_plan.md`), nếu gọi subagent bắt buộc dùng model thấp nhất/nhẹ nhất (`flash_lite` hoặc `flash`).
  - Bàn giao tức thì: Bàn giao kết quả ngay khi sửa xong nếu người dùng không yêu cầu test cụ thể; không chạy bộ kiểm thử tự động kéo dài.

---

## [v1.3.0] - 2026-09-04
- **Commit**: `3bb214c` (`3bb214cbc55aa89a054f2c0857ecdcd378c88fe5`)
- **Loại thay đổi**: `refactor`
- **Tiêu đề**: `refactor(rules): replace codex routing with multi-agent orchestration v1.3.0`
- **Chi tiết thay đổi**:
  - Thay thế toàn bộ Điều luật số 6 cũ thành **Điều phối Đa Agent Song song (Concurrent Multi-Agent Orchestration)**.
  - Kích hoạt song song: Chủ động tách các hạng mục độc lập và triệu hồi nhiều Subagent (`invoke_subagent`) làm việc cùng lúc nhằm tối đa hóa tốc độ.
  - Nguyên tắc tránh xung đột: Tuyệt đối không để nhiều agent chỉnh sửa đè lên cùng 1 file hoặc cùng 1 phân vùng mã nguồn tại một thời điểm.
  - Điều phối & Hợp nhất: Agent chính đóng vai trò tổng chỉ huy tiếp nhận kết quả, giải quyết xung đột và chạy kiểm thử tích hợp trước khi bàn giao.

---

## [v1.2.0] - 2026-08-27
- **Commit**: `fba84c6` (`fba84c65e040efef13607a56cb1ee220fa913671`)
- **Loại thay đổi**: `feat`
- **Tiêu đề**: `feat(rules): add reasoning levels for terra and luna v1.2.0`
- **Chi tiết thay đổi**:
  - Mở rộng phân luồng các cấp độ suy luận (`model_reasoning_effort`):
    - `Terra Max`: `gpt-5.6-terra` với mức suy luận tối đa (`max` / `xhigh`) cho bài toán hóc búa, kiến trúc lớn.
    - `Terra Medium`: `gpt-5.6-terra` với mức suy luận `medium` cho lập trình hàng ngày.
    - `Luna Max`: `gpt-5.6-luna` với mức suy luận `max` / `high` cho xử lý logic nhanh.
    - `Luna Low`: `gpt-5.6-luna` với mức suy luận `low` cho viết unit test và script siêu tốc.
  - Bổ sung các Subagent chuyên trách và cập nhật skill `codex-delegation`.

---

## [v1.1.0] - 2026-08-27
- **Commit**: `6110661` (`6110661afe756a23cdac0fec623de9c5c6708fe3`)
- **Loại thay đổi**: `feat`
- **Tiêu đề**: `feat(rules): integrate codex models and codegraph mcp v1.1.0`
- **Chi tiết thay đổi**:
  - Bổ sung cấu hình MCP Server cho **CodeGraph** (`@colbymchenry/codegraph`) để tự động đồng bộ code knowledge graph.
  - Tích hợp hệ thống OpenAI Codex qua MCP Server `codex` và định tuyến model ban đầu cho Sol 5.6, Terra 5.6, Luna 5.6.
  - Bổ sung Điều luật số 6 ban đầu vào `GEMINI.md` và `AGENTS.md`.

---

## [v1.0.0] - 2026-08-22
- **Commit**: `52b3c92` (`52b3c9296e950f6ab7f9df5c70dbaaae1f061c23`)
- **Loại thay đổi**: `feat`
- **Tiêu đề**: `feat(rules): initialize global guidelines v1.0.0`
- **Chi tiết thay đổi**:
  - Khởi tạo bộ quy tắc toàn cục áp dụng cho tất cả các phiên làm việc của AGY trên hệ thống gồm 5 điều luật cốt lõi:
    1. **Ngôn ngữ & Tư duy**: Bắt buộc dùng 100% Tiếng Việt (kể cả thẻ `<thought>`), phong cách trực diện, súc tích.
    2. **Tiêu chuẩn Code**: Clean Architecture, SOLID, DRY, KISS, TypeScript Strict Mode (`strict: true`), cấm dùng `any`.
    3. **An toàn Mã nguồn**: Không tự ý xóa code cũ/thừa/comments, bắt buộc Unit Tests PASS 100%, bảo mật secrets.
    4. **Quy trình Git**: Chuẩn Conventional Commits, bảo vệ nhánh chính (cấm push trực tiếp lên `main`/`master`).
    5. **Quy trình Lập kế hoạch**: Bắt buộc tạo `implementation_plan.md` trước thay đổi lớn, chạy kiểm thử tự động sau mỗi lần sửa.

