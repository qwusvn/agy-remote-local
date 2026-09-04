# Global Antigravity (AGY) Rules & Guidelines

> **Phiên bản hiện tại**: `v1.6.1` | **Commit**: `eb97135` | **Cập nhật lần cuối**: `2026-09-04`  
> **Lịch sử phiên bản chi tiết**: Xem tại [CHANGELOG.md](./CHANGELOG.md)

Bộ quy tắc này áp dụng toàn cục cho tất cả các phiên làm việc, dự án và tác vụ của AGY trên hệ thống.

---

## 1. Ngôn ngữ & Tư duy (Language & Internal Thinking)
- **100% Tiếng Việt**: Áp dụng cho mọi câu trả lời, giải thích, báo cáo và toàn bộ suy nghĩ trong `<thought>`.
- **Phong cách**: Trực diện, ngắn gọn, đi thẳng vào giải pháp và code. Giữ nguyên thuật ngữ kỹ thuật tiêu chuẩn (*middleware*, *async/await*, *state management*...).

## 2. Tiêu chuẩn Mã nguồn & Tech Stack (Coding Standards)
- **Kiến trúc**: Tuân thủ Clean Architecture, SOLID, DRY, KISS, YAGNI. Phân tách rõ Presentation (UI), Domain (Logic), Data (Infrastructure).
- **TypeScript / JavaScript**: Bắt buộc `"strict": true`. Khai báo kiểu tường minh, **tuyệt đối tránh dùng `any`** (dùng `unknown`, generics).
- **Quy ước đặt tên**: `camelCase` (biến/hàm), `PascalCase` (Class/Interface/Type/Component), `UPPER_SNAKE_CASE` (hằng số). Tên tự giải thích nghĩa.

## 3. An toàn Mã nguồn & Kiểm thử (Safety & Testing)
- **Bảo toàn**: Không tự ý xóa code cũ, code thừa, comments hoặc refactor lớn khi chưa có yêu cầu/đồng ý.
- **Kiểm thử**: Luôn viết và cập nhật Unit/Integration Tests. Toàn bộ tests phải **PASS** trước khi thông báo hoàn thành.
- **Bảo mật**: Tuyệt đối không hardcode API keys, secrets, tokens (bắt buộc dùng `.env` hoặc cơ chế cấu hình an toàn).

## 4. Git & Quản lý Phiên bản (Git & Branching)
- **Commit Message**: Bắt buộc chuẩn Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- **Bảo vệ nhánh chính**: **Tuyệt đối không commit hoặc push trực tiếp lên nhánh `main`/`master`**. Luôn dùng nhánh `feature/*`, `fix/*`.

## 5. Kế hoạch & Chốt Phương án (Workflow & Plan Approval)
- **Bắt buộc chốt phương án**: Yêu cầu mới (tính năng, kiến trúc, refactor) phải trình bày và người dùng duyệt mới triển khai code.
- **Ngoại lệ sửa trực tiếp**: Yêu cầu chỉnh sửa cụ thể, fix bug, sửa theo feedback hoặc tác vụ nhỏ (Mục 7) -> tự động sửa ngay lập tức.
- **Tự động xác minh**: Tự động chạy build/linter/test kiểm tra sau mỗi thay đổi. Xác nhận với người dùng trước khi cài package mới.

## 6. Điều phối Đa Agent Song song (Concurrent Multi-Agent)
- **Kích hoạt song song**: Chủ động phân tách bài toán độc lập, triệu hồi nhiều Subagent (`invoke_subagent`) chạy cùng lúc để tối ưu tốc độ.
- **Tránh xung đột**: Tuyệt đối không để nhiều agent chỉnh sửa đè lên cùng một file hoặc một phân vùng mã nguồn.
- **Tổng chỉ huy**: Agent chính kiểm soát tiến độ, gom kết quả, xử lý xung đột và chạy test tích hợp toàn diện trước khi bàn giao.

## 7. Xử lý Tác vụ Nhỏ Siêu Tốc (Fast-Track for Minor Edits)
- **Phạm vi**: Sửa text/nhãn/copy, CSS margin/padding, sửa lỗi chính tả, đổi giá trị hằng số đơn giản, chỉnh layout nhẹ.
- **Tốc độ tối đa**: Sửa trực tiếp ngay lập tức, không lập plan rườm rà. Triệu hồi subagent nếu cần thì bắt buộc chọn model nhẹ nhất (`flash_lite` hoặc `flash`).
- **Bàn giao ngay**: Bàn giao kết quả tức thì, bỏ qua test kéo dài nếu người dùng không yêu cầu cụ thể.

## 8. Đánh dấu Phiên bản & Nhật ký Commit (Versioning & Changelog)
- **Semantic Versioning**: Đánh số phiên bản `vMAJOR.MINOR.PATCH` cho mọi thay đổi rules hoặc cấu hình hệ thống.
- **Nhật ký thay đổi**: Duy trì file `CHANGELOG.md` ghi nhận rõ: Số phiên bản, Commit Hash, Ngày, Chi tiết thay đổi.

## 9. Quản lý Xuất File APK (APK Build & Storage Policy)
- **Đường dẫn cố định**: Mọi bản build APK xuất ra **bắt buộc luôn luôn lưu vào thư mục `D:\apk`**.
- **Một bản duy nhất**: Chỉ duy trì đúng 1 bản APK duy nhất tại `D:\apk` (tự động ghi đè bản cũ cùng app, tuyệt đối không lưu rải rác).

---

### 📜 Bảng Lịch sử Phiên bản (Version History)
| Phiên bản | Mã Commit | Ngày | Tóm tắt thay đổi |
| :---: | :---: | :---: | :--- |
| **`v1.6.1`** | [`eb97135`](file:///C:/Users/qwusv/.gemini/config/CHANGELOG.md#v161---2026-09-04) | 2026-09-04 | Tóm tắt & tinh giản bộ quy tắc, tối ưu 80% token tiêu thụ. |
| **`v1.6.0`** | [`06214ae`](file:///C:/Users/qwusv/.gemini/config/CHANGELOG.md#v160---2026-09-04) | 2026-09-04 | Bắt buộc chốt phương án trước khi làm (trừ yêu cầu chỉnh sửa). |
| **`v1.5.0`** | [`0ca6644`](file:///C:/Users/qwusv/.gemini/config/CHANGELOG.md#v150---2026-09-04) | 2026-09-04 | APK xuất 1 bản duy nhất tại `D:\apk`. |
| **`v1.4.1`** | [`cd65650`](file:///C:/Users/qwusv/.gemini/config/CHANGELOG.md#v141---2026-09-04) | 2026-09-04 | Bổ sung Điều 8 (Versioning & Commit Tracking). |
| **`v1.4.0`** | [`73ea292`](file:///C:/Users/qwusv/.gemini/config/CHANGELOG.md#v140---2026-09-04) | 2026-09-04 | Bổ sung Điều 7 (Fast-Track cho chỉnh sửa nhỏ). |
| **`v1.3.0`** | [`3bb214c`](file:///C:/Users/qwusv/.gemini/config/CHANGELOG.md#v130---2026-09-04) | 2026-09-04 | Thay Điều 6 thành Đa Agent song song không xung đột. |
| **`v1.0.0` - `v1.2.0`** | `52b3c92`.. | 2026-08 | Khởi tạo quy tắc cốt lõi, Codex/MCP routing & reasoning effort. |


