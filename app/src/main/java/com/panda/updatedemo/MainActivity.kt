package com.panda.updatedemo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.panda.update.AppUpdateHelper
import com.panda.update.analytics.AppUpdateAnalytics
import com.panda.update.model.AppUpdateConfig
import com.panda.updatedemo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var helper: AppUpdateHelper

    private val analytics = object : AppUpdateAnalytics {
        override fun logOptionalUpdateShow(versionCode: Int) { Log.d(TAG, "show $versionCode") }
        override fun logOptionalUpdateAgree(versionCode: Int) { Log.d(TAG, "agree $versionCode") }
        override fun logOptionalUpdateCancel(versionCode: Int) { Log.d(TAG, "cancel $versionCode") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Phải tạo trong onCreate (đăng ký ActivityResultLauncher).
        helper = AppUpdateHelper.create(activity = this, analytics = analytics)

        binding.btnForce.setOnClickListener { check("force_update", 1) }
        binding.btnOptional.setOnClickListener { check("optional_update", 3) }
        binding.btnOff.setOnClickListener { check("off_pop_up_update", 0) }
    }

    // Config thật lấy từ Remote Config; ở demo giả lập bằng giá trị cứng.
    private fun check(type: String, times: Long) {
        helper.checkAndPromptUpdate(config = AppUpdateConfig.create(type, times))
    }

    companion object { private const val TAG = "UpdateDemo" }
}
