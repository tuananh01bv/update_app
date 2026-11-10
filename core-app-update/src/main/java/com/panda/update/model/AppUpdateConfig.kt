package com.panda.update.model

data class AppUpdateConfig(
    val updateType: UpdateType = UpdateType.OFF,
    val optionalUpdateTimesShow: Int = 1
) {
    companion object {
        const val KEY_UPDATE_APP = "update_app"
        const val KEY_OPTIONAL_UPDATE_TIMES_SHOW = "optional_update_times_show"

        fun create(updateTypeRaw: String?, timesShowRaw: Long = 1L): AppUpdateConfig {
            val type = UpdateType.fromValue(updateTypeRaw)
            val timesShow = if (timesShowRaw <= 0) 1 else timesShowRaw.toInt()
            return AppUpdateConfig(
                updateType = type,
                optionalUpdateTimesShow = timesShow
            )
        }
    }
}
