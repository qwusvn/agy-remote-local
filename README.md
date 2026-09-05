# AGY Remote - Android Client cho Antigravity AI 🚀

[![Release](https://img.shields.io/badge/Release-v1.0.1-brightgreen.svg)](https://github.com/qwusvn/agy-remote-local/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.7.3-blue.svg)](https://developer.android.com/studio/releases/gradle-plugin)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-2024.12.01-green.svg)](https://developer.android.com/jetpack/compose)
[![Target SDK](https://img.shields.io/badge/Target_SDK-36-orange.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

**AGY Remote** là ứng dụng Android Native chuyên dụng giúp kết nối, điều khiển và tương tác với máy chủ **Antigravity AI** (`agy`) chạy trên máy tính qua mạng LAN hoặc Wi-Fi. Ứng dụng được tối ưu hóa toàn diện để mang lại trải nghiệm mượt mà, độc lập và tiện lợi như ứng dụng di động gốc.

---

## ✨ Tính Năng Nổi Bật

- 🚀 **Kết Nối LAN Tự Động & Tiết Kiệm Băng Thông**:
  - Tự động quét và kết nối với máy chủ Antigravity qua cổng `4400`.
  - Bộ nhớ đệm tài nguyên tĩnh (`AgyResourceCache`) lưu cache CSS/JS/Font trên thiết bị, giúp tốc độ mở app và chuyển trang gần như tức thời (0ms).
- 📑 **Quản Lý Đa Tab (Multi-Tab) Độc Lập**:
  - Mở đồng thời nhiều phiên trò chuyện trong các Tab riêng biệt.
  - Chuyển Tab siêu tốc 0ms không tải lại trang (SPA Navigation qua `window.__TSR_ROUTER__`).
  - Tự động lưu và phục hồi đường dẫn phiên làm việc gần nhất.
- 🔔 **Thông Báo Hoàn Thành Tác Vụ Real-time (Push Notification + Sound + Haptic)**:
  - Tích hợp giám sát kép: Lắng nghe sự kiện từ WebSocket nền (`AgyWebSocketClient`) và theo dõi DOM trực tiếp (`AgySessionScript`).
  - Khi Agent làm xong tác vụ: Tự động **rung máy đa nhịp (Waveform)**, **phát chuông âm thanh**, **hiển thị Toast** và **bắn Heads-up Push Notification** lên khay thông báo ngay cả khi màn hình tắt hoặc app chạy ngầm.
- ✍️ **Trình Soạn Thảo Thông Minh (Smart Input Formatter)**:
  - Hỗ trợ gõ danh sách tự động (Auto-bullet list): Khi gõ `- Nội dung` và nhấn `Enter` / xuống dòng trên bàn phím ảo, dòng tiếp theo sẽ tự động thụt vào `- `.
  - Tương thích hoàn hảo với cả Lexical Editor của Antigravity và bàn phím ảo Android (Gboard, Samsung Keyboard, v.v.).
- 🖼️ **Đính Kèm & Dán Ảnh Tiện Lợi**:
  - Tích hợp `FileProvider` an toàn, hỗ trợ chọn ảnh từ thư viện hoặc camera.
  - Nút **Dán ảnh từ Clipboard** trên thanh trạng thái: Chỉ cần chụp màn hình hoặc sao chép ảnh rồi bấm nút, ảnh sẽ được nén và bơm trực tiếp Base64 vào khung chat.
- ⚡ **Mở Rộng Thẻ Hành Động (Expand Tool Actions)**:
  - Nút 1-chạm giúp mở rộng toàn bộ các thẻ thực thi lệnh, đọc/ghi file hoặc mã nguồn của Agent mà không cần bấm từng thẻ thủ công.

---

## 🏗️ Cấu Trúc Dự Án (Architecture Overview)

Dự án được xây dựng theo mô hình **MVVM + Jetpack Compose + Clean Architecture**:

```text
app/src/main/java/com/example/agyremote/
├── MainActivity.kt                 # Điểm khởi đầu ứng dụng, yêu cầu runtime permissions
├── data/
│   ├── ConnectionPreferences.kt    # Lưu trữ cấu hình IP, Port, Theme bằng DataStore
│   └── cache/
│       └── AgyResourceCache.kt     # Bộ đệm tài nguyên Web tĩnh (CSS, JS, Fonts)
├── media/
│   └── ImageOptimizer.kt           # Tối ưu hóa kích thước ảnh và cấp quyền FileProvider
├── network/
│   ├── AgyWebSocketClient.kt       # Client WebSocket nhận sự kiện real-time từ máy chủ
│   └── LanScanner.kt               # Tự động dò tìm IP máy chủ AGY trong mạng LAN
├── service/
│   └── AgyNotificationService.kt   # Foreground Service duy trì kết nối nền và bắn thông báo
├── ui/
│   ├── MainScreen.kt               # Giao diện chính Jetpack Compose
│   ├── ConnectionDialog.kt         # Hộp thoại cài đặt IP, Port, Theme và thử chuông
│   ├── AgyWebView.kt               # Nhân Chromium WebView tùy biến với JS Bridges
│   ├── components/                 # Các component giao diện: TabBar, TopStatusBar, Logs
│   └── webview/scripts/            # Các module JavaScript độc lập được tiêm vào WebView:
│       ├── AgySessionScript.kt     # Giám sát phiên làm việc & trạng thái Agent isWorking
│       ├── AgyInputScript.kt       # Xử lý tự động xuống dòng danh sách (- bullet)
│       ├── AgyActionScript.kt      # Tự động bung toàn bộ Tool Action cards
│       └── AgyThemeScript.kt       # Đồng bộ Dark/Light theme với hệ thống
```

---

## 🛠️ Hướng Dẫn Cài Đặt & Phát Triển (Developer Guide)

### 1. Yêu Cầu Môi Trường
- **JDK**: Java 17 trở lên.
- **Android Studio**: Ladybug (2024.2+) hoặc mới hơn.
- **Android SDK**: `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`.

### 2. Tải Mã Nguồn & Mở Dự Án
```bash
git clone <URL_REPO_CUA_BAN>
cd agy-remote
```
Mở thư mục dự án bằng **Android Studio**. Studio sẽ tự động đồng bộ hóa Gradle (`Sync Project with Gradle Files`).

### 3. Biên Dịch & Chạy Thử
- **Biên dịch bản Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Chạy Unit Tests**:
  ```bash
  ./gradlew test
  ```
- **Cài đặt vào điện thoại Android qua ADB**:
  ```bash
  adb install -r D:\apk\agy-remote-debug.apk
  ```

---

## 🤝 Quy Chuẩn Đóng Góp (Contributing Guidelines)

Chúng tôi rất hoan nghênh mọi đóng góp từ cộng đồng! Khi tham gia phát triển, xin vui lòng tuân thủ các quy tắc sau:

1. **Quy chuẩn nhánh Git**:
   - Nhánh `main` / `master` là nhánh ổn định, **không commit hoặc push trực tiếp**.
   - Tạo nhánh mới cho mỗi tính năng hoặc bản sửa lỗi:
     - `feature/ten-tinh-nang`
     - `fix/ten-loi`
2. **Quy chuẩn Commit (Conventional Commits)**:
   - `feat: thêm tính năng mới...`
   - `fix: sửa lỗi...`
   - `refactor: tái cấu trúc mã nguồn...`
   - `test: bổ sung kiểm thử tự động...`
   - `chore: cập nhật cấu hình gradle, dependencies...`
3. **Kiểm Thử Tự Động**:
   - Viết hoặc cập nhật Unit Tests cho mọi thay đổi logic nghiệp vụ.
   - Chạy `./gradlew test` để đảm bảo **100% tests PASS** trước khi tạo Pull Request (PR).
4. **Bảo Mật**:
   - Tuyệt đối không commit API keys, token hoặc thông tin bí mật vào git. Luôn sử dụng cấu hình môi trường hoặc bộ nhớ mã hóa an toàn.

---

## 📦 Tải Bản Cài Đặt Sẵn (Pre-built APK)

Nếu bạn chỉ muốn cài đặt và trải nghiệm trên điện thoại Android mà không cần biên dịch từ mã nguồn:
- Tải file APK mới nhất tại thư mục: `D:\apk\agy-remote-debug.apk` (hoặc trong mục **Releases** của repository).

---

## 📄 Bản Quyền (License)

Dự án được phân phối dưới giấy phép [MIT License](LICENSE).
