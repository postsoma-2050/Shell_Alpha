package com.cylonid.nativealpha.activities

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.cylonid.nativealpha.BuildConfig
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.databinding.NewsActivityBinding
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.util.EntryPointUtils

class NewsActivity : AppCompatActivity() {

    private lateinit var binding: NewsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = NewsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.onboardingVersion.text =
            getString(R.string.onboarding_version, BuildConfig.VERSION_NAME)

        val eulaAccepted = DataManager.getInstance().eulaData
        binding.onboardingEulaSection.isVisible = !eulaAccepted
        binding.btnContinue.setText(
            if (eulaAccepted) {
                R.string.onboarding_continue
            } else {
                R.string.onboarding_agree_and_continue
            }
        )
        binding.btnContinue.setOnClickListener { completeOnboarding() }

        onBackPressedDispatcher.addCallback(this) {
            if (DataManager.getInstance().eulaData) finish()
        }
    }

    private fun completeOnboarding() {
        if (isFinishing) return
        binding.btnContinue.isEnabled = false

        val dataManager = DataManager.getInstance()
        dataManager.eulaData = true
        dataManager.lastShownUpdate = BuildConfig.VERSION_CODE
        EntryPointUtils.markOnboardingCompleted(this)
        finish()
    }
}
