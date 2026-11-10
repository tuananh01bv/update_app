package com.panda.update

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.panda.update.analytics.AppUpdateAnalytics
import com.panda.update.model.AppUpdateConfig
import com.panda.update.model.UpdateType
import com.panda.update.storage.AppUpdateStorage
import com.panda.update.ui.AppUpdateDialog

/**
 * Callback kết quả của luồng update. Tất cả đều có default nên chỉ override cái cần.
 * Mọi callback chạy trên main thread.
 */
interface AppUpdateResultListener {
    /** Play đã nhận yêu cầu và bắt đầu luồng update (user đã đồng ý trên màn Play). */
    fun onUpdateAccepted(type: UpdateType) {}

    /** User huỷ trên màn Play (hoặc bấm back). Force: dialog vẫn hiện để user bấm lại. */
    fun onUpdateCanceled(type: UpdateType) {}

    /** Luồng update lỗi (Play báo lỗi, tải/cài lỗi, hoặc loại update không được phép). */
    fun onUpdateFailed(type: UpdateType) {}

    /**
     * Flexible: đã tải xong. Gọi [completeUpdate] để cài và khởi động lại app.
     * Mặc định gọi luôn; override để hiện snackbar "Restart" rồi mới gọi.
     */
    fun onFlexibleDownloaded(completeUpdate: () -> Unit) {
        completeUpdate()
    }
}

/**
 * Lưu ý: phải gọi [create] trong `onCreate` (trước khi activity STARTED) vì bên trong
 * đăng ký ActivityResultLauncher.
 */
class AppUpdateHelper private constructor(
    private val activity: ComponentActivity,
    private val analytics: AppUpdateAnalytics?,
    private val storage: AppUpdateStorage,
    private val resultListener: AppUpdateResultListener?,
    appUpdateManagerOverride: AppUpdateManager?
) {
    private val appUpdateManager: AppUpdateManager by lazy {
        appUpdateManagerOverride ?: AppUpdateManagerFactory.create(activity)
    }

    private var currentDialog: AppUpdateDialog? = null
    private var listenerRegistered = false

    /** true từ lúc bấm Update tới khi luồng Play được mở (hoặc lỗi); chặn bấm đúp. */
    private var isStartingUpdate = false

    /** Loại update đang chạy; null nếu chưa bắt đầu. */
    private var currentType: UpdateType? = null

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val type = currentType ?: return@registerForActivityResult
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    resultListener?.onUpdateAccepted(type)
                    dismissDialog()
                }

                Activity.RESULT_CANCELED -> {
                    // Force: giữ dialog để user thử lại. Optional: dialog đã tắt từ trước.
                    resultListener?.onUpdateCanceled(type)
                }

                else -> {
                    // ActivityResult.RESULT_IN_APP_UPDATE_FAILED hoặc mã lạ.
                    Log.w(TAG, "Update flow failed, resultCode=${result.resultCode}")
                    resultListener?.onUpdateFailed(type)
                }
            }
        }

    private val installListener: InstallStateUpdatedListener = InstallStateUpdatedListener { state ->
        val type = currentType ?: return@InstallStateUpdatedListener
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING, InstallStatus.INSTALLING -> dismissDialog()
            InstallStatus.DOWNLOADED -> {
                dismissDialog()
                notifyDownloaded()
            }

            InstallStatus.INSTALLED -> {
                dismissDialog()
                appUpdateManager.unregisterListener(installListener)
            }

            InstallStatus.FAILED -> resultListener?.onUpdateFailed(type)
            InstallStatus.CANCELED -> resultListener?.onUpdateCanceled(type)
            else -> Unit
        }
    }

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                resumeIfNeeded()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                dismissDialog()
                runCatching { appUpdateManager.unregisterListener(installListener) }
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    fun checkAndPromptUpdate(config: AppUpdateConfig) {
        if (config.updateType == UpdateType.OFF) return

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (!isActivityAlive()) return@addOnSuccessListener

            val isUpdateAvailable =
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            if (!isUpdateAvailable) return@addOnSuccessListener

            val targetVersionCode = appUpdateInfo.availableVersionCode()

            when (config.updateType) {
                UpdateType.FORCE_UPDATE -> showForceUpdateDialog(appUpdateInfo)
                UpdateType.OPTIONAL_UPDATE ->
                    handleOptionalUpdate(appUpdateInfo, targetVersionCode, config.optionalUpdateTimesShow)

                UpdateType.OFF -> Unit
            }
        }.addOnFailureListener {
            Log.w(TAG, "Failed to get appUpdateInfo", it)
        }
    }

    private fun showForceUpdateDialog(appUpdateInfo: AppUpdateInfo) {
        showDialog(
            AppUpdateDialog(
                activity = activity,
                updateType = UpdateType.FORCE_UPDATE,
                onUpdateClick = {
                    startUpdate(appUpdateInfo, UpdateType.FORCE_UPDATE, AppUpdateType.IMMEDIATE)
                }
            )
        )
    }

    private fun handleOptionalUpdate(
        appUpdateInfo: AppUpdateInfo,
        targetVersionCode: Int,
        maxTimesShow: Int
    ) {
        val shownCount = storage.getShownCount(targetVersionCode)
        if (shownCount >= maxTimesShow) return

        storage.incrementShownCount(targetVersionCode)
        analytics?.logOptionalUpdateShow(targetVersionCode)

        showDialog(
            AppUpdateDialog(
                activity = activity,
                updateType = UpdateType.OPTIONAL_UPDATE,
                onUpdateClick = {
                    analytics?.logOptionalUpdateAgree(targetVersionCode)
                    startUpdate(appUpdateInfo, UpdateType.OPTIONAL_UPDATE, AppUpdateType.FLEXIBLE)
                },
                onNoThanksClick = {
                    analytics?.logOptionalUpdateCancel(targetVersionCode)
                }
            )
        )
    }

    private fun showDialog(dialog: AppUpdateDialog) {
        if (!isActivityAlive()) return
        dismissDialog()
        currentDialog = dialog
        dialog.setOnDismissListener { if (currentDialog === dialog) currentDialog = null }
        dialog.show()
    }

    /**
     * Mỗi AppUpdateInfo chỉ start được 1 luồng update -> luôn lấy info mới cho mỗi lần bấm,
     * nếu không lần bấm lại sau khi cancel/fail sẽ không có phản hồi.
     */
    private fun startUpdate(staleInfo: AppUpdateInfo, type: UpdateType, playType: Int) {
        if (!isActivityAlive()) return
        // Chặn bấm đúp khi đang lấy info / mở luồng Play.
        if (isStartingUpdate) return
        isStartingUpdate = true
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { fresh ->
                if (!isActivityAlive()) {
                    isStartingUpdate = false
                    return@addOnSuccessListener
                }
                if (fresh.updateAvailability() == UpdateAvailability.UPDATE_NOT_AVAILABLE) {
                    isStartingUpdate = false
                    resultListener?.onUpdateFailed(type)
                    return@addOnSuccessListener
                }
                try {
                    launchUpdateFlow(fresh, type, playType)
                } finally {
                    isStartingUpdate = false
                }
            }
            .addOnFailureListener {
                Log.w(TAG, "Refresh appUpdateInfo failed, fallback to old info", it)
                try {
                    if (isActivityAlive()) launchUpdateFlow(staleInfo, type, playType)
                } finally {
                    isStartingUpdate = false
                }
            }
    }

    private fun launchUpdateFlow(appUpdateInfo: AppUpdateInfo, type: UpdateType, playType: Int) {
        if (!isActivityAlive()) return
        if (!appUpdateInfo.isUpdateTypeAllowed(playType)) {
            Log.w(TAG, "Update type $playType not allowed by Play")
            resultListener?.onUpdateFailed(type)
            return
        }
        currentType = type
        // Đăng ký cho cả Immediate lẫn Flexible để tắt dialog khi trạng thái cài thay đổi.
        if (!listenerRegistered) {
            appUpdateManager.registerListener(installListener)
            listenerRegistered = true
        }
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                launcher,
                AppUpdateOptions.newBuilder(playType).build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "startUpdateFlowForResult failed", e)
            resultListener?.onUpdateFailed(type)
        }
    }

    /**
     * Gọi khi activity resume: nếu Immediate update đang dở thì mở lại luồng Play,
     * nếu Flexible đã tải xong trong lúc app ở background thì xử lý hoàn tất.
     */
    private fun resumeIfNeeded() = refreshState()

    /**
     * Truy vấn lại trạng thái từ Play và đồng bộ UI: tắt dialog nếu update đã tải/cài,
     * hoàn tất Flexible đã tải xong, hoặc mở lại Immediate đang dở.
     * Tự gọi khi activity resume; có thể gọi tay (ví dụ khi test với Fake).
     */
    fun refreshState() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (!isActivityAlive()) return@addOnSuccessListener
            when (info.installStatus()) {
                InstallStatus.DOWNLOADING, InstallStatus.INSTALLING, InstallStatus.INSTALLED ->
                    dismissDialog()
            }
            // Đã bắt đầu một luồng update mà Play báo không còn update nữa => đã cài xong.
            if (currentType != null &&
                info.updateAvailability() == UpdateAvailability.UPDATE_NOT_AVAILABLE
            ) {
                dismissDialog()
            }
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                dismissDialog()
                if (currentType == null) currentType = UpdateType.OPTIONAL_UPDATE
                notifyDownloaded()
            } else if (info.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                currentType = UpdateType.FORCE_UPDATE
                runCatching {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        launcher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                }.onFailure { Log.e(TAG, "Resume immediate update failed", it) }
            }
        }
    }

    private fun notifyDownloaded() {
        val listener = resultListener
        if (listener != null) {
            listener.onFlexibleDownloaded { appUpdateManager.completeUpdate() }
        } else {
            appUpdateManager.completeUpdate()
        }
    }

    private fun dismissDialog() {
        currentDialog?.let { if (it.isShowing) runCatching { it.dismiss() } }
        currentDialog = null
    }

    private fun isActivityAlive() = !activity.isFinishing && !activity.isDestroyed

    companion object {
        private const val TAG = "AppUpdateHelper"

        fun create(
            activity: ComponentActivity,
            analytics: AppUpdateAnalytics? = null,
            resultListener: AppUpdateResultListener? = null,
            // Truyền FakeAppUpdateManager khi test/debug; mặc định dùng Play thật.
            appUpdateManager: AppUpdateManager? = null
        ): AppUpdateHelper {
            val storage = AppUpdateStorage(activity.applicationContext)
            return AppUpdateHelper(activity, analytics, storage, resultListener, appUpdateManager)
        }
    }
}
