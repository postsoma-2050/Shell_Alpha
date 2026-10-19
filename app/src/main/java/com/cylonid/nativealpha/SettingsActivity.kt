package com.cylonid.nativealpha

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.cylonid.nativealpha.activities.AdblockConfigActivity
import com.cylonid.nativealpha.activities.ToolbarBaseActivity
import com.cylonid.nativealpha.databinding.GlobalSettingsBinding
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.monitors.WebMonitorWorker
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.NotificationUtils
import com.cylonid.nativealpha.util.SearchEngineUrl
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : ToolbarBaseActivity<GlobalSettingsBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val settings = DataManager.getInstance().settings
        binding.settings = settings

        // Back button
        binding.btnBackGlobalSettings.setOnClickListener {
            DataManager.getInstance().saveGlobalSettings()
            finish()
        }

        // 1. Set as default browser
        binding.itemDefaultBrowser.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        // 2. Lite Apps
        binding.itemLiteApps.setOnClickListener {
            showLiteAppsDialog()
        }

        // 3. Sandboxes
        binding.itemSandboxes.setOnClickListener {
            startActivity(Intent(this@SettingsActivity, SandboxActivity::class.java))
        }

        // 4. Privacy
        binding.itemPrivacy.setOnClickListener {
            showPrivacyDialog()
        }

        // 5. User Scripts
        binding.itemUserScripts.setOnClickListener {
            showUserScriptsDialog()
        }

        // 6. Web Monitors
        binding.itemWebMonitors.setOnClickListener {
            showWebMonitorsDialog()
        }

        // 7. Content Blocker
        binding.itemContentBlocker.setOnClickListener {
            val intent = Intent(this@SettingsActivity, AdblockConfigActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            startActivity(intent)
        }

        // 8. Tracking Protection
        val globalApp = settings.globalWebApp
        binding.switchTrackingProtection.isChecked = globalApp.isStripTrackingParams
        binding.switchTrackingProtection.setOnCheckedChangeListener { _, isChecked ->
            globalApp.isStripTrackingParams = isChecked
            DataManager.getInstance().saveGlobalSettings()
            DataManager.getInstance().saveWebAppData()
        }

    }

    private fun showLiteAppsDialog() {
        val options = arrayOf(
            getString(R.string.global_web_app_settings),
            getString(R.string.search_engine_url),
            getString(R.string.export_settings_web_apps),
            getString(R.string.import_settings_web_apps)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_lite_apps_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, WebAppSettingsActivity::class.java)
                        intent.putExtra(Const.INTENT_WEBAPPID, DataManager.getInstance().settings.globalWebApp.ID)
                        intent.action = Intent.ACTION_VIEW
                        startActivity(intent)
                    }
                    1 -> showSearchEngineDialog()
                    2 -> triggerExport()
                    3 -> triggerImport()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSearchEngineDialog() {
        val settings = DataManager.getInstance().settings
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.search_engine_url)
            helperText = getString(R.string.search_engine_validation_help)
            isHelperTextEnabled = true
            setPadding(
                resources.getDimensionPixelSize(R.dimen.margin_dialog),
                0,
                resources.getDimensionPixelSize(R.dimen.margin_dialog),
                0
            )
        }
        val input = EditText(this).apply {
            setText(settings.searchEngineUrl)
            setSelection(text.length)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            minHeight = (48 * resources.displayMetrics.density).toInt()
        }
        inputLayout.addView(input)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.search_engine_url))
            .setView(inputLayout)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    inputLayout.error = null
                }
            })
            positiveButton.setOnClickListener {
                val newUrl = input.text.toString().trim()
                if (!SearchEngineUrl.isValidTemplate(newUrl)) {
                    inputLayout.error = getString(R.string.search_engine_invalid)
                    input.requestFocus()
                    return@setOnClickListener
                }
                inputLayout.error = null
                settings.searchEngineUrl = newUrl
                DataManager.getInstance().saveGlobalSettings()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showPrivacyDialog() {
        val options = arrayOf(
            getString(R.string.clear_cache_after_usage),
            getString(R.string.clear_cookies_after_usage)
        )
        val settings = DataManager.getInstance().settings
        val checked = booleanArrayOf(settings.isClearCache, settings.clear_cookies)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_privacy_title))
            .setMultiChoiceItems(options, checked) { _, which, isChecked ->
                when (which) {
                    0 -> settings.isClearCache = isChecked
                    1 -> settings.setClearCookies(isChecked)
                }
                DataManager.getInstance().saveGlobalSettings()
            }
            .setPositiveButton(R.string.privacy_done) { _, _ ->
                DataManager.getInstance().saveGlobalSettings()
            }
            .setNeutralButton(R.string.privacy_clear_now_action) { _, _ ->
                showClearWebDataConfirmation()
            }
            .show()
    }

    private fun showClearWebDataConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_clear_confirm_title)
            .setMessage(R.string.privacy_clear_confirm_message)
            .setPositiveButton(R.string.privacy_clear_confirm_action) { _, _ ->
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    NotificationUtils.showInfoSnackbar(
                        this,
                        getString(R.string.privacy_cleared_snackbar),
                        Snackbar.LENGTH_LONG
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showUserScriptsDialog() {
        val userScriptSites = DataManager.getInstance().activeWebsites.filter { it.isUserscriptEnabled }
        val msg = if (userScriptSites.isEmpty()) {
            getString(R.string.userscripts_empty_message)
        } else {
            getString(R.string.userscripts_list_header) + userScriptSites.joinToString("\n") { "• " + it.title }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_userscripts_title))
            .setMessage(msg)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showWebMonitorsDialog() {
        val monitorSites = DataManager.getInstance().activeWebsites.filter { it.isMonitorEnabled }
        val msg = if (monitorSites.isEmpty()) {
            getString(R.string.webmonitors_empty_message)
        } else {
            getString(R.string.webmonitors_list_header) + monitorSites.joinToString("\n") { "• " + it.title }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_webmonitors_title))
            .setMessage(msg)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.webmonitors_run_check_now) { _, _ ->
                WebMonitorWorker.syncPeriodicWork(this)
                NotificationUtils.showInfoSnackbar(this, getString(R.string.webmonitors_triggered_check), Snackbar.LENGTH_SHORT)
            }
            .show()
    }

    private fun triggerExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val currentDateTime = sdf.format(Date())
        intent.putExtra(Intent.EXTRA_TITLE, "NativeAlpha_$currentDateTime")
        try {
            startActivityForResult(intent, Const.CODE_WRITE_FILE)
        } catch (e: ActivityNotFoundException) {
            NotificationUtils.showInfoSnackbar(this, getString(R.string.no_filemanager), Snackbar.LENGTH_LONG)
        }
    }

    private fun triggerImport() {
        val intent = Intent().setType("*/*").setAction(Intent.ACTION_GET_CONTENT)
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_a_file)), Const.CODE_OPEN_FILE)
        } catch (e: ActivityNotFoundException) {
            NotificationUtils.showInfoSnackbar(this, getString(R.string.no_filemanager), Snackbar.LENGTH_LONG)
        }
    }

    override fun onPause() {
        super.onPause()
        DataManager.getInstance().saveGlobalSettings()
    }

    override fun inflateBinding(layoutInflater: LayoutInflater): GlobalSettingsBinding {
        return GlobalSettingsBinding.inflate(layoutInflater)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Const.CODE_WRITE_FILE && resultCode == RESULT_OK) {
            val uri = data?.data
            DataManager.getInstance().saveGlobalSettings()
            if (uri == null || !DataManager.getInstance().saveSharedPreferencesToJsonFile(uri)) {
                NotificationUtils.showInfoSnackbar(this, getString(R.string.export_failed), Snackbar.LENGTH_LONG)
            } else {
                NotificationUtils.showInfoSnackbar(this, getString(R.string.export_success), Snackbar.LENGTH_SHORT)
            }
        }
        if (requestCode == Const.CODE_OPEN_FILE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (!DataManager.getInstance().loadSharedPreferencesFromFile(uri)) {
                NotificationUtils.showInfoSnackbar(this, getString(R.string.import_failed), Snackbar.LENGTH_LONG)
            } else {
                val i = Intent(this@SettingsActivity, MainActivity::class.java)
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                DataManager.getInstance().loadAppData()
                WebMonitorWorker.syncPeriodicWork(this)
                i.putExtra(Const.INTENT_BACKUP_RESTORED, true)
                finish()
                startActivity(i)
            }
        }
    }
}
