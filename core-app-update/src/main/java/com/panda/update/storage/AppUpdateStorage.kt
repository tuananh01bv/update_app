package com.panda.update.storage

import android.content.Context
import android.content.SharedPreferences

class AppUpdateStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)

    fun getShownCount(versionCode: Int): Int {
        return prefs.getInt("shown_count_$versionCode", 0)
    }

    fun incrementShownCount(versionCode: Int): Int {
        val newCount = getShownCount(versionCode) + 1
        prefs.edit().putInt("shown_count_$versionCode", newCount).apply()
        return newCount
    }

    fun setCancelled(versionCode: Int, isCancelled: Boolean = true) {
        prefs.edit().putBoolean("cancelled_$versionCode", isCancelled).apply()
    }

    fun isCancelled(versionCode: Int): Boolean {
        return prefs.getBoolean("cancelled_$versionCode", false)
    }
}
