package com.panda.update.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.panda.update.databinding.DialogAppUpdateBinding
import com.panda.update.model.UpdateType

class AppUpdateDialog(
    activity: Activity,
    private val updateType: UpdateType,
    private val onUpdateClick: () -> Unit,
    private val onNoThanksClick: () -> Unit = {}
) : Dialog(activity) {

    private lateinit var binding: DialogAppUpdateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        binding = DialogAppUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        setupUI()
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupUI() {
        when (updateType) {
            UpdateType.FORCE_UPDATE -> {
                binding.btnNoThanks.visibility = View.GONE
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }
            UpdateType.OPTIONAL_UPDATE -> {
                binding.btnNoThanks.visibility = View.VISIBLE
                setCancelable(true)
                setCanceledOnTouchOutside(true)
            }
            UpdateType.OFF -> dismiss()
        }

        binding.btnUpdate.setOnClickListener {
            onUpdateClick()
            if (updateType == UpdateType.OPTIONAL_UPDATE) {
                dismiss()
            }
        }

        binding.btnNoThanks.setOnClickListener {
            onNoThanksClick()
            dismiss()
        }
    }
}
