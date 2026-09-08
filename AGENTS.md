# Global Antigravity (AGY) Rules & Guidelines

> **Phiên bản hiện tại**: `v1.8.0` | **Commit**: `636e484` | **Cập nhật lần cuối**: `2026-09-08`

Bộ quy tắc này áp dụng toàn cục cho tất cả các phiên làm việc, dự án và tác vụ của AGY trên hệ thống.

---

## 1. An toàn Mã nguồn & Kiểm thử (Safety & Testing)
- **Bảo toàn**: Không tự ý xóa code cũ, code thừa, comments hoặc refactor lớn khi chưa có yêu cầu/đồng ý.
- **Kiểm thử**: Luôn viết và cập nhật Unit/Integration Tests. Toàn bộ tests phải **PASS** trước khi thông báo hoàn thành (trừ tác vụ nhỏ Mục 5).
- **Bảo mật**: Tuyệt đối không hardcode API keys, secrets, tokens (bắt buộc dùng `.env` hoặc cơ chế cấu hình an toàn).

## 2. Git & Quản lý Phiên bản (Git & Branching)
- **Commit Message**: Bắt buộc chuẩn Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- **Bảo vệ nhánh chính**: **Tuyệt đối không commit hoặc push trực tiếp lên nhánh `main`/`master`**. Luôn dùng nhánh `feature/*`, `fix/*`.

## 3. Kế hoạch, Chốt Phương án & Chống Vòng lặp (Workflow & Anti-Loop)
- **Bắt buộc chốt phương án**: Yêu cầu mới (tính năng, kiến trúc, refactor) phải trình bày và người dùng duyệt mới triển khai code.
- **Ngoại lệ sửa trực tiếp**: Yêu cầu chỉnh sửa cụ thể, fix bug, sửa theo feedback hoặc tác vụ nhỏ (Mục 5) -> tự động sửa ngay lập tức.
- **Chống vòng lặp & Không cố đấm ăn xôi**: Nếu một tác vụ/cách làm thử 2-3 lần không có tiến triển, hoặc lệnh bị treo/đứng quá lâu -> **Tuyệt đối không lặp lại cách cũ**. Phải lập tức dừng lại, báo cáo điểm nghẽn và đề xuất ngay các phương án thay thế để người dùng lựa chọn.
- **Tự động xác minh**: Tự động chạy build/linter/test sau mỗi thay đổi logic (ngoại trừ các tác vụ nhỏ theo Mục 5 được miễn trừ). Xác nhận với người dùng trước khi cài package mới.

## 4. Điều phối Đa Agent Song song (Concurrent Multi-Agent)
- **Kích hoạt song song**: Chủ động phân tách bài toán độc lập, triệu hồi nhiều Subagent (`invoke_subagent`) chạy cùng lúc để tối ưu tốc độ.
- **Tránh xung đột**: Tuyệt đối không để nhiều agent chỉnh sửa đè lên cùng một file hoặc một phân vùng mã nguồn.
- **Tổng chỉ huy**: Agent chính kiểm soát tiến độ, gom kết quả, xử lý xung đột và chạy test tích hợp toàn diện trước khi bàn giao.

## 5. Xử lý Tác vụ Nhỏ Siêu Tốc (Fast-Track for Minor Edits)
- **Phạm vi**: Sửa text/nhãn/copy, CSS margin/padding, sửa lỗi chính tả, đổi giá trị hằng số đơn giản, chỉnh layout nhẹ.
- **Tốc độ tối đa**: Sửa trực tiếp ngay lập tức, không lập plan rườm rà. Triệu hồi subagent nếu cần thì bắt buộc chọn model nhẹ nhất (`flash_lite` hoặc `flash`).
- **Bàn giao ngay**: Bàn giao kết quả tức thì, bỏ qua test/build kéo dài nếu người dùng không yêu cầu cụ thể.

## 6. Đánh dấu Phiên bản & Nhật ký Commit (Versioning & Changelog Policy)
- **Semantic Versioning**: Đánh số phiên bản `vMAJOR.MINOR.PATCH` cho mọi thay đổi rules hoặc cấu hình hệ thống.
- **Ghi chép & Phản hồi**: Chỉ ghi nhận âm thầm vào file `CHANGELOG.md` khi có phiên bản quy tắc mới được phát hành. **Tuyệt đối không tự ý đọc hoặc trích xuất toàn bộ changelog/lịch sử phiên bản ra câu trả lời khi người dùng không yêu cầu**.

## 7. Quản lý Xuất File APK (APK Build & Storage Policy)
- **Đường dẫn cố định**: Mọi bản build APK xuất ra **bắt buộc luôn luôn lưu vào thư mục `D:\apk`**.
- **Một bản duy nhất**: Chỉ duy trì đúng 1 bản APK mới nhất tại `D:\apk` (tự động xoá/ghi đè bản cũ cùng app, tuyệt đối không lưu rải rác). 
- **Cách lưu mỗi bản mới ví dụ 1.0.1 > 1.0.2...1.0.9 > 1.1.0...
