package com.panda.update.analytics

interface AppUpdateAnalytics {
    fun logOptionalUpdateShow(versionCode: Int)
    fun logOptionalUpdateAgree(versionCode: Int)
    fun logOptionalUpdateCancel(versionCode: Int)
}
