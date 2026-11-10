# Module Core App Update (`:core-app-update`)

Module Android độc lập dùng chung cho việc quản lý **App Update (Force Update & Optional Update)** thông qua **Firebase Remote Config** và **Google Play In-App Update API**. 

Hỗ trợ 100% cho cả dự án dùng **XML Views** lẫn **Jetpack Compose**.

---

## 🌟 Tính năng chính

- 🚀 **UI Custom hiện đại**: Giao diện Card tối (`#222325`), bo góc tròn `24dp`, icon tên lửa sắc nét và nút bấm màu xanh neon nổi bật.
- 🔒 **Force Update (Cập nhật bắt buộc)**: Khóa toàn bộ tương tác app, ẩn nút *"No, Thanks"*, cấm thoát dialog (`setCancelable(false)`), tự động gọi Google Play Immediate Update.
- 💡 **Optional Update (Cập nhật gợi ý)**: Hiển thị nút *"No, Thanks"*, tự động đếm và giới hạn số lần hiển thị trên 1 `versionCode` theo cấu hình Remote Config, tự động gọi Google Play Flexible Update.
- 📊 **Log Event Analytics**: Bắn log tự động với param `version` (dạng `versionCode` Int) cho 3 sự kiện: `optional_update_times_show`, `optional_update_agree`, `optional_update_cancel`.
- 🔌 **Decoupled Architecture**: Độc lập hoàn toàn với Firebase Analytics / Remote Config SDK trực tiếp (dễ dàng gắn vào bất kỳ tracker hay remote config provider nào).

---

## 📦 Cách tích hợp vào Dự án

### 1. Copy Module & Thêm vào Gradle

Copy thư mục `core-app-update` vào thư mục gốc của dự án Android mới.

Thêm module vào `settings.gradle.kts`:
```kotlin
include(":core-app-update")
```

Thêm dependency vào `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":core-app-update"))
}
```

---

## ⚙️ Cấu hình Firebase Remote Config & Log Events

### 1. Remote Config Keys

| Key | Kiểu dữ liệu | Giá trị | Mô tả |
| :--- | :--- | :--- | :--- |
| `update_app` | `String` | `"force_update"` | Bật cập nhật bắt buộc (Force Update) |
| | | `"optional_update"` | Bật cập nhật tùy chọn (Optional Update) |
| | | `"off_pop_up_update"` | Tắt cả 2 loại popup update (Default) |
| `optional_update_times_show` | `Int` / `Long` | `1` | Hiển thị optional update tối đa 1 lần |
| | | `3` | Hiển thị optional update tối đa 3 lần |

### 2. Log Events

| Event Name | Parameter Key | Parameter Value | Trigger Khi Nào |
| :--- | :--- | :--- | :--- |
| `optional_update_times_show` | `version` | `versionCode` (Int) | Popup Optional Update vừa hiển thị |
| `optional_update_agree` | `version` | `versionCode` (Int) | Người dùng bấm nút "Update" |
| `optional_update_cancel` | `version` | `versionCode` (Int) | Người dùng bấm nút "No, Thanks" |

---

## 💻 Hướng dẫn Sử dụng (Code Examples)

### Bước 1: Triển khai Class Analytics Tracker

Tạo class chuyển tiếp log sang Firebase Analytics trong app của bạn:

```kotlin
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.panda.update.analytics.AppUpdateAnalytics

class FirebaseUpdateAnalytics(private val firebaseAnalytics: FirebaseAnalytics) : AppUpdateAnalytics {
    override fun logOptionalUpdateShow(versionCode: Int) {
        val bundle = Bundle().apply { putInt("version", versionCode) }
        firebaseAnalytics.logEvent("optional_update_times_show", bundle)
    }

    override fun logOptionalUpdateAgree(versionCode: Int) {
        val bundle = Bundle().apply { putInt("version", versionCode) }
        firebaseAnalytics.logEvent("optional_update_agree", bundle)
    }

    override fun logOptionalUpdateCancel(versionCode: Int) {
        val bundle = Bundle().apply { putInt("version", versionCode) }
        firebaseAnalytics.logEvent("optional_update_cancel", bundle)
    }
}
```

---

### Bước 2: Gọi trong Activity (Dự án XML Views)

Trong `MainActivity` hoặc `HomeActivity`:

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.panda.update.AppUpdateHelper
import com.panda.update.model.AppUpdateConfig

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAppUpdate()
    }

    private fun checkAppUpdate() {
        // 1. Đọc config từ Firebase Remote Config
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val updateConfig = AppUpdateConfig.create(
            updateTypeRaw = remoteConfig.getString(AppUpdateConfig.KEY_UPDATE_APP),
            timesShowRaw = remoteConfig.getLong(AppUpdateConfig.KEY_OPTIONAL_UPDATE_TIMES_SHOW)
        )

        // 2. Khởi tạo AppUpdateHelper
        val analytics = FirebaseUpdateAnalytics(FirebaseAnalytics.getInstance(this))
        val helper = AppUpdateHelper.create(activity = this, analytics = analytics)

        // 3. Thực hiện kiểm tra và hiển thị popup nếu thỏa mãn điều kiện
        helper.checkAndPromptUpdate(config = updateConfig)
    }
}
```

---

### Bước 3: Gọi trong Jetpack Compose App

Có thể gọi trong `onCreate()` của `ComponentActivity` hoặc trong `LaunchedEffect` ở màn Composable:

```kotlin
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check Update trước khi hoặc cùng lúc setContentView
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val updateConfig = AppUpdateConfig.create(
            updateTypeRaw = remoteConfig.getString(AppUpdateConfig.KEY_UPDATE_APP),
            timesShowRaw = remoteConfig.getLong(AppUpdateConfig.KEY_OPTIONAL_UPDATE_TIMES_SHOW)
        )

        val analytics = FirebaseUpdateAnalytics(FirebaseAnalytics.getInstance(this))
        AppUpdateHelper.create(activity = this, analytics = analytics)
            .checkAndPromptUpdate(config = updateConfig)

        setContent {
            MyApplicationTheme {
                HomeScreen()
            }
        }
    }
}
```

---

## 🛠 Thắc mắc & Lưu ý Kỹ thuật

1. **Google Play In-App Update chỉ chạy khi nào?**
   - Đã upload APK/AAB mới có `versionCode` cao hơn lên Google Play Store (Internal Test, Closed Beta, hoặc Production).
   - Ứng dụng đã được cài từ Google Play Store (App chạy từ Android Studio run direct APK thì Google API sẽ không báo Update Available).
2. **Muốn test UI Custom local thì sao?**
   - Bạn có thể trực tiếp khởi tạo `AppUpdateDialog(activity, UpdateType.OPTIONAL_UPDATE, ...).show()` để preview giao diện ngay lập tức mà không cần chờ Google Play Store.
