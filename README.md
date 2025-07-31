# 📄 Android PDF Loader Library

Một thư viện hiện đại, hiệu năng cao để load và hiển thị PDF trong Android bằng Kotlin.

---

## 🎯 Thiết kế API

### 1. Builder Pattern - Fluent API
- Dạng chain method, dễ đọc & dễ sử dụng.
- Mọi method cấu hình đều trả về `Configurator`.
- `into()` để bind vào `PDFView` có sẵn, `load()` để tự khởi tạo.

### 2. Đa nguồn dữ liệu
- Hỗ trợ: file, assets, uri, network, content provider,...
- Load bất đồng bộ, có callback.
- Xử lý lỗi tự động.

### 3. Quản lý vòng đời
- Hỗ trợ `onResume()`, `onPause()`, `recycle()`.
- Tự cleanup khi `Fragment`/`Activity` bị destroy.

### 4. Sự kiện & Callback
- Lambda-based callbacks.
- Progress tracking & error reporting.

### 5. Điều khiển chương trình
- `next()`, `previous()`, `goToPage()`.
- Truy vấn thông tin trang.
- Hỗ trợ thay đổi cấu hình khi đang chạy.

---

## 🚀 Tính năng nâng cao

### 📜 Scroll & Navigation
- `pageSnap()` – Snap chính xác theo trang.
- `pageFling()` – Hiệu ứng fling mượt mà.
- `pageScrollDirection()` – Điều chỉnh hướng cuộn.
- `autoSpacing()` – Tự động căn khoảng cách.

### 🔍 Zoom Control
- `doubleTapZoomScale()`, `maxZoom()`, `minZoom()`
- `zoomCentered()`, `pinchZoom()`

### 🖼️ Rendering
- `renderQuality()`, `bestQuality()`, `renderDuringScale()`
- `offscreenPageLimit()`, `prerenderPages()`, `backgroundThreads()`

### 🎨 UI & Visual
- `nightMode()`, `backgroundColor()`
- `showPageNumber()`, `pageNumberPosition()`
- `animationType()`, `animationDuration()`

### 🔐 Security & Text
- `enableTextSelection()`
- `enableCopy()`, `enablePrint()`

### 🧭 UI Components
- `scrollHandle()`
- `minimapPosition()` – hỗ trợ nhiều vị trí

### ⚙️ Callbacks
- `onPageScroll()`, `onZoom()`, `onLinkTap()`
- `onDoubleTap()`, `onLongPress()`
- `onProgress()`, `onRenderProgress()`

### 🚦 Performance
- `lowMemoryMode()`
- `cacheSize()`, `prerenderPages()`

### 📐 Page Layout
- `pageFitPolicy()`, `fitEachPage()`, `spacing()`

---

## 🧱 Kiến trúc & tổ chức mã nguồn

### 1. Modular Design
- Tách theo chức năng: engine, UI, callbacks,...
- Dễ bảo trì, mở rộng, và tái sử dụng.

### 2. Scalable Architecture
- Core engine tách riêng khỏi UI.
- Plugin-based cho scroll handle, minimap,...
- Sử dụng Factory pattern để dễ mở rộng.

### 3. Hiệu năng là trọng tâm
- Packages riêng cho: memory, cache, threading.
- Benchmark module để đo hiệu suất.
- Hệ thống quản lý chất lượng render.

### 4. Developer Experience
- App demo chi tiết.
- Tài liệu rõ ràng.
- Xử lý lỗi có hướng dẫn cụ thể.

### 5. Production Ready
- Có ProGuard rules.
- Script build/publish tự động.
- Tích hợp kiểm tra chất lượng code.

### 6. Testing Infrastructure
- Unit test theo module.
- Integration test theo flow.
- Kiểm thử hiệu năng & memory leaks.

---

## ✅ Ưu điểm tổng thể

- **Thân thiện & trực quan**: API dễ dùng.
- **Hiệu năng cao**: Tối ưu bộ nhớ và render.
- **Bền vững với lỗi**: Hệ thống callback & exception rõ ràng.
- **Tương thích vòng đời Android**: Tích hợp lifecycle.
- **Mở rộng linh hoạt**: Cấu hình đa dạng, plugin hỗ trợ.
- **Sẵn sàng production**: Tích hợp CI/CD, publish, minify,...

---

## 📦 License

MIT License © 2025 - Cloudxanh

