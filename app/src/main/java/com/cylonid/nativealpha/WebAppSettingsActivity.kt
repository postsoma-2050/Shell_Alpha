package com.cylonid.nativealpha

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TimePicker
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import com.google.android.material.textfield.TextInputEditText
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.cylonid.nativealpha.activities.ToolbarBaseActivity
import com.cylonid.nativealpha.databinding.WebappSettingsBinding
import com.cylonid.nativealpha.helper.BiometricPromptHelper
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.SandboxManager
import com.cylonid.nativealpha.model.SandboxPreferences
import com.cylonid.nativealpha.adapter.SandboxChoiceAdapter
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.monitors.WebMonitorWorker
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.ColorPalette
import com.cylonid.nativealpha.util.DateUtils.convertStringToCalendar
import com.cylonid.nativealpha.util.DateUtils.getHourMinFormat
import com.cylonid.nativealpha.util.EntryPointUtils
import com.cylonid.nativealpha.util.ShortcutIconUtils
import com.cylonid.nativealpha.util.Utility
import com.google.gson.Gson
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar

class WebAppSettingsActivity : ToolbarBaseActivity<WebappSettingsBinding>() {
    companion object {
        const val EXTRA_UPDATED_WEBAPP = "updatedWebAppSettings"
        private const val STATE_DRAFT = "webapp_settings_draft"
        private const val STATE_ORIGINAL = "webapp_settings_original"
        private const val STATE_TAB = "webapp_settings_tab"
    }

    var webappID: Int = -1
    var webapp: WebApp? = null
    private var isGlobalWebApp: Boolean = false
    private lateinit var draft: WebApp
    private lateinit var originalJson: String
    private var selectedTab = 0
    private var exitDialog: androidx.appcompat.app.AlertDialog? = null
    private val gson = Gson()
    private val sandboxPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK &&
            data?.hasExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID) == true
        ) {
            draft.containerId = data.getIntExtra(
                SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, Const.NO_CONTAINER
            )
            SandboxManager.getInstance().normalizeSandbox(draft)
            updateSandboxSelection()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EntryPointUtils.entryPointReached(this)
        DataManager.getInstance().loadAppData()

        setToolbarTitle(getString(R.string.web_app_settings))

        webappID = intent.getIntExtra(Const.INTENT_WEBAPPID, -1)
        Utility.Assert(webappID != -1, "WebApp ID could not be retrieved.")
        isGlobalWebApp = webappID == DataManager.getInstance().settings.globalWebApp.ID

        if (isGlobalWebApp) {
            webapp = DataManager.getInstance().settings.globalWebApp
            prepareGlobalWebAppScreen()
        } else webapp = DataManager.getInstance().getWebAppIgnoringGlobalOverride(webappID, true)

        if (webapp == null) {
            finish()
            return
        }
        val original = SandboxManager.getInstance().normalizeSandbox(WebApp(webapp!!))
        originalJson = savedInstanceState?.getString(STATE_ORIGINAL) ?: gson.toJson(original)
        val modifiedWebapp = savedInstanceState?.getString(STATE_DRAFT)?.let {
            gson.fromJson(it, WebApp::class.java)
        } ?: original
        draft = SandboxManager.getInstance().normalizeSandbox(modifiedWebapp)
        // Keep the edit baseline after recreation so concurrent field changes can merge.
        draft.persistenceSnapshot = originalJson
        binding.webapp = modifiedWebapp
        binding.activity = this@WebAppSettingsActivity
        binding.executePendingBindings()

        setupIdentityPreview(modifiedWebapp)
        setupSettingsTabs(savedInstanceState?.getInt(STATE_TAB) ?: 0)
        setupSaveAndCancel()
        setupDarkModeElements()
        setupPlusSettings()
        setupDesktopUserAgentHint()
        setupShortcutButton()
        setupSwitchListeners(modifiedWebapp)
        setupSandboxSelection()
        setupThemeColorSpinner(modifiedWebapp)
        setupSettingsColorGrid(modifiedWebapp)
        setupIdentityCardEdit()
        setupInputFocusAutoScroll()
        setupInputTwoWaySync()

    }

    override fun onResume() {
        super.onResume()
        if (::draft.isInitialized) updateSandboxSelection()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::draft.isInitialized) {
            binding.executePendingBindings()
            outState.putString(STATE_DRAFT, gson.toJson(draft))
            outState.putString(STATE_ORIGINAL, originalJson)
            outState.putInt(STATE_TAB, selectedTab)
        }
        super.onSaveInstanceState(outState)
    }

    override fun inflateBinding(layoutInflater: LayoutInflater): WebappSettingsBinding {
        return WebappSettingsBinding.inflate(layoutInflater)
    }

    private fun themeColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun setupSwitchListeners(webapp: WebApp) {
        onSwitchExpertSettingsChanged(
            binding.switchExpertSettings,
            webapp.isShowExpertSettings
        )
        onOverrideSettingsChanged(
            binding.switchOverrideGlobal,
            webapp.isOverrideGlobalSettings
        )
        binding.cardOverrideGlobal.setOnClickListener {
            binding.switchOverrideGlobal.toggle()
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                hideSoftKeyboard()
                if (!draft.isOverrideGlobalSettings && (selectedTab == 0 || selectedTab == 1)) {
                    binding.switchOverrideGlobal.isChecked = true
                    Snackbar.make(binding.root, R.string.override_global_settings_auto_unlocked, Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.btnSave)
                        .show()
                    return true
                }
                return false
            }
        })
        binding.webAppSettingsScroll.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    fun onSwitchExpertSettingsChanged(button: CompoundButton?, isChecked: Boolean) {
        binding.sectionExpertSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
    }

    fun onSwitchAutoreloadChanged(button: CompoundButton?, isChecked: Boolean) {
        binding.textReloadInterval.isEnabled = isChecked
        binding.labelReloadInterval.isEnabled = isChecked
    }

    fun onSwitchJsChanged(button: CompoundButton?, isChecked: Boolean) {
        if (isChecked) {
            binding.switchDesktopSite.isEnabled = true
            binding.switchAdblock.isEnabled = true
        } else {
            binding.switchDesktopSite.isChecked = false
            binding.switchDesktopSite.isEnabled = false
            binding.switchAdblock.isChecked = false
            binding.switchAdblock.isEnabled = false
        }
    }

    fun onSwitchForceDarkChanged(button: CompoundButton?, isChecked: Boolean) {
        if (isChecked) {
            binding.switchTimeSpanDarkMode.isEnabled = true
            binding.textDarkModeBegin.isEnabled = true
            binding.textDarkModeEnd.isEnabled = true
        } else {
            binding.switchTimeSpanDarkMode.isChecked = false
            binding.switchTimeSpanDarkMode.isEnabled = false
            binding.textDarkModeBegin.isEnabled = false
            binding.textDarkModeEnd.isEnabled = false
        }
    }

    fun onSwitchTimeSpanDarkChanged(button: CompoundButton?, isChecked: Boolean) {
        binding.lblDarkModeBegin.isEnabled = isChecked
        binding.lblDarkModeEnd.isEnabled = isChecked
        binding.textDarkModeBegin.isEnabled = isChecked
        binding.textDarkModeEnd.isEnabled = isChecked
    }

    fun onSwitchUserAgentChanged(button: CompoundButton?, isChecked: Boolean) {
        if (isChecked) {
            binding.switchDesktopSite.isChecked = false
            binding.switchDesktopSite.isEnabled = false
            binding.textUserAgent.isEnabled = true
        } else {
            binding.textUserAgent.isEnabled = false
            binding.switchDesktopSite.isEnabled = true
        }
    }

    private fun setSwitchBiometricAccessSilently(newValue: Boolean) {
        binding.switchBiometricAccess.setOnCheckedChangeListener(null)
        binding.switchBiometricAccess.isChecked = newValue
        binding.switchBiometricAccess.setOnCheckedChangeListener { switch, checked ->
            onSwitchBiometricAccessChanged(switch, checked)
        }
    }

    fun onSwitchBiometricAccessChanged(mSwitch: CompoundButton?, isChecked: Boolean) {
        val switchBiometricAccess = binding.switchBiometricAccess
        // Reset to value before user toggled, actual setting of value is done by prompt success callback
        setSwitchBiometricAccessSilently(!switchBiometricAccess.isChecked)

        if (!switchBiometricAccess.isChecked) {
            BiometricPromptHelper(this).showPrompt(
                {
                    setSwitchBiometricAccessSilently(true)
                    draft.isBiometricProtection = true
                },
                {}, getString(R.string.bioprompt_enable_restriction)
            )
        } else {
            BiometricPromptHelper(this).showPrompt(
                {
                    setSwitchBiometricAccessSilently(false)
                    draft.isBiometricProtection = false
                },
                {}, getString(R.string.bioprompt_disable_restricition)
            )
        }
    }

    fun onSwitchOverrideGlobalSettingsChanged(button: CompoundButton, isChecked: Boolean) {
        Utility.setViewAndChildrenEnabled(binding.tabContainerBehavior, isChecked)
        Utility.setViewAndChildrenEnabled(binding.tabContainerPrivacy, isChecked)
    }

    fun onOverrideSettingsChanged(button: CompoundButton, checked: Boolean) {
        if (!::draft.isInitialized) return
        onSwitchOverrideGlobalSettingsChanged(button, checked)
        updateOverrideStatusText(checked)
        // App identity (title, shortcut, theme color) and sandbox do not inherit website defaults.
        Utility.setViewAndChildrenEnabled(binding.tabContainerTheme, true)
        Utility.setViewAndChildrenEnabled(binding.tabContainerTools, true)
        Utility.setViewAndChildrenEnabled(binding.cardSettingsSandbox, true)
        Utility.setViewAndChildrenEnabled(binding.switchCookies, true)
        binding.switch3PCookies.isEnabled = checked &&
            SandboxPreferences.isCookiesAllowed(draft.containerId)
    }

    private fun updateOverrideStatusText(checked: Boolean) {
        binding.txtOverrideGlobalTitle.setText(
            if (checked) R.string.override_global_settings_active_title
            else R.string.override_global_settings_inactive_title
        )
        binding.txtOverrideGlobalStatus.setText(
            if (checked) R.string.override_global_settings_enabled_hint
            else R.string.override_global_settings_disabled_hint
        )
        val activeColor = themeColor(com.google.android.material.R.attr.colorPrimary)
        val defaultOutline = themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        binding.cardOverrideGlobal.strokeColor = if (checked) activeColor else defaultOutline
        binding.cardOverrideGlobal.strokeWidth = resources.getDimensionPixelSize(
            if (checked) R.dimen.active_stroke_width else R.dimen.hairline_stroke_width
        )
        val cardBg = if (checked) {
            themeColor(com.google.android.material.R.attr.colorSurfaceContainer)
        } else {
            themeColor(com.google.android.material.R.attr.colorSurfaceContainerLow)
        }
        binding.cardOverrideGlobal.setCardBackgroundColor(cardBg)
    }

    private fun setupSaveAndCancel() {
        binding.btnSave.setOnClickListener { saveAndFinish() }
        onBackPressedDispatcher.addCallback(this) { requestExit() }
        setNavigationClickListener { requestExit() }
        binding.btnCancel.setOnClickListener { requestExit() }
    }

    private fun saveAndFinish() {
        binding.executePendingBindings()
        SandboxManager.getInstance().normalizeSandbox(draft)
        if (isGlobalWebApp) {
            DataManager.getInstance().settings.globalWebApp = draft
            DataManager.getInstance().saveGlobalSettings()
        } else {
            DataManager.getInstance().replaceWebApp(draft)
        }
        WebMonitorWorker.syncPeriodicWork(this)
        setResult(RESULT_OK, Intent()
            .putExtra(Const.INTENT_WEBAPP_CHANGED, true)
            .putExtra(EXTRA_UPDATED_WEBAPP, gson.toJson(draft)))
        finish()
    }

    private fun requestExit() {
        binding.executePendingBindings()
        if (gson.toJsonTree(draft) == gson.fromJson(originalJson, com.google.gson.JsonElement::class.java)) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        if (exitDialog?.isShowing == true) return
        exitDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sandbox_interaction_unsaved_title)
            .setMessage(R.string.sandbox_interaction_unsaved_message)
            .setPositiveButton(R.string.save) { _, _ -> saveAndFinish() }
            .setNegativeButton(R.string.sandbox_interaction_discard) { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .setNeutralButton(R.string.sandbox_interaction_keep_editing, null)
            .show()
    }

    private fun setupSandboxSelection() {
        val showChoiceDialog = { showSandboxChoiceDialog() }
        binding.cardSettingsSandbox.setOnClickListener { showChoiceDialog() }
        binding.txtSettingsSandbox.setOnClickListener { showChoiceDialog() }
        binding.switchCookies.setOnClickListener {
            startActivity(Intent(this, SandboxActivity::class.java)
                .putExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, draft.containerId))
        }
        updateSandboxSelection()
    }

    fun showSandboxChoiceDialog() {
        if (isFinishing || isDestroyed) return
        val items = mutableListOf<SandboxChoiceAdapter.Item>()
        val defaultName = getString(
            R.string.entry_sandbox_default,
            SandboxPreferences.getName(Const.NO_CONTAINER)
        )
        val defaultColor = ColorPalette.colorForContainer(Const.NO_CONTAINER)
        items.add(SandboxChoiceAdapter.Item(Const.NO_CONTAINER, defaultName, defaultColor))

        val sandboxCount = SandboxManager.getSandboxCount()
        for (i in 0 until sandboxCount) {
            val name = SandboxPreferences.getName(i)
            val label = getString(R.string.entry_sandbox_named, name, i + 1)
            val color = ColorPalette.colorForContainer(i)
            items.add(SandboxChoiceAdapter.Item(i, label, color))
        }

        val checkedIndex = items.indexOfFirst { it.containerId == draft.containerId }.coerceAtLeast(0)
        val adapter = SandboxChoiceAdapter(this, items, draft.containerId)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sandbox_interaction_choose_title)
            .setSingleChoiceItems(adapter, checkedIndex) { dialog, which ->
                val chosen = items[which]
                draft.containerId = chosen.containerId
                draft.isUseContainer = SandboxManager.isValidContainerId(chosen.containerId)
                SandboxManager.getInstance().normalizeSandbox(draft)
                updateSandboxSelection()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.manage_sandbox_names) { dialog, _ ->
                dialog.dismiss()
                sandboxPicker.launch(
                    Intent(this, SandboxActivity::class.java)
                        .putExtra(Const.INTENT_WEBAPPID, webappID)
                        .putExtra(SandboxActivity.EXTRA_PICK_ONLY, true)
                        .putExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, draft.containerId)
                )
            }
            .show()
    }

    private fun updateSandboxSelection() {
        if (isGlobalWebApp) {
            binding.cardSettingsSandbox.visibility = View.GONE
            binding.cardOverrideGlobal.visibility = View.GONE
            binding.txtSettingsSandbox.visibility = View.GONE
            binding.switchCookies.text = getString(R.string.sandbox_interaction_cookie_manage)
        } else {
            binding.cardSettingsSandbox.visibility = View.VISIBLE
            binding.cardOverrideGlobal.visibility = View.VISIBLE
            val name = SandboxActivity.containerTitle(this, draft.containerId)
            val label = getString(R.string.sandbox_interaction_current_choose, name)
            binding.txtSettingsSandbox.text = label
            val color = ColorPalette.colorForContainer(draft.containerId)
            binding.imgSettingsSandboxDot.backgroundTintList = ColorStateList.valueOf(color)
            binding.txtSettingsSandbox.backgroundTintList = ColorStateList.valueOf(color)
            binding.txtSettingsSandbox.setTextColor(ColorPalette.contrastingTextColor(color))
            binding.switchCookies.text = getString(
                R.string.sandbox_interaction_cookie_policy_link,
                name,
                getString(if (SandboxPreferences.isCookiesAllowed(draft.containerId))
                    R.string.sandbox_interaction_cookie_allowed
                else R.string.sandbox_interaction_cookie_blocked)
            )
            setupIdentityPreview(draft)
            applyPreviewColor(draft)
        }
        binding.switch3PCookies.isEnabled = (isGlobalWebApp || draft.isOverrideGlobalSettings) &&
            (isGlobalWebApp || SandboxPreferences.isCookiesAllowed(draft.containerId))
    }

    private fun setupThemeColorSpinner(modifiedWebapp: WebApp) {
        val colorNames = ColorPalette.HERMIT_COLORS.keys.toList()
        binding.spinnerThemeColor.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            colorNames
        )
        val currentIndex = colorNames.indexOfFirst {
            it.equals(modifiedWebapp.themeColor, ignoreCase = true)
        }.coerceAtLeast(0)
        binding.spinnerThemeColor.setSelection(currentIndex, false)
        binding.spinnerThemeColor.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    modifiedWebapp.themeColor = colorNames[position]
                    setupSettingsColorGrid(modifiedWebapp)
                    applyPreviewColor(modifiedWebapp)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun setupSettingsTabs(initialTab: Int) {
        val tabs = listOf(
            binding.btnTabBehavior to binding.tabContainerBehavior,
            binding.btnTabPrivacy to binding.tabContainerPrivacy,
            binding.btnTabTheme to binding.tabContainerTheme,
            binding.btnTabTools to binding.tabContainerTools
        )
        val activeBg = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorPrimaryContainer))
        val activeText = themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
        val inactiveBg = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorSurfaceContainerLow))
        val inactiveText = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineColor = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorOutlineVariant))
        val hairline = resources.getDimensionPixelSize(R.dimen.hairline_stroke_width)

        tabs.forEachIndexed { index, (button, container) ->
            button.setOnClickListener {
                hideSoftKeyboard()
                button.requestFocus()
                selectedTab = index
                tabs.forEach { (otherButton, otherContainer) ->
                    val selected = otherContainer === container
                    otherContainer.visibility = if (selected) View.VISIBLE else View.GONE
                    otherButton.isSelected = selected
                    otherButton.alpha = 1f
                    if (selected) {
                        otherButton.backgroundTintList = activeBg
                        otherButton.setTextColor(activeText)
                        otherButton.strokeWidth = 0
                    } else {
                        otherButton.backgroundTintList = inactiveBg
                        otherButton.setTextColor(inactiveText)
                        otherButton.strokeColor = outlineColor
                        otherButton.strokeWidth = hairline
                    }
                }
            }
        }
        tabs[initialTab.coerceIn(tabs.indices)].first.performClick()
    }

    private fun setupIdentityPreview(modifiedWebapp: WebApp) {
        val favicon = ShortcutIconUtils.loadFavicon(this, modifiedWebapp.ID)
        binding.imgSettingsFavicon.setImageBitmap(favicon ?: fallbackFavicon(modifiedWebapp))
    }

    private fun setupIdentityCardEdit() {
        if (isGlobalWebApp) {
            binding.btnEditIdentity.visibility = View.GONE
            binding.appIdentityCard.isClickable = false
            return
        }
        val openEdit = View.OnClickListener { showEditIdentityDialog() }
        binding.appIdentityCard.setOnClickListener(openEdit)
        binding.btnEditIdentity.setOnClickListener(openEdit)
    }

    private fun showEditIdentityDialog() {
        if (isFinishing || isDestroyed || isGlobalWebApp) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_webapp_identity, null)
        val editTitle = dialogView.findViewById<TextInputEditText>(R.id.editTxtWebAppTitle)
        val editUrl = dialogView.findViewById<TextInputEditText>(R.id.editTxtWebAppUrl)

        editTitle.setText(draft.title)
        editUrl.setText(draft.baseUrl)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_lite_app_identity_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newTitle = editTitle.text?.toString()?.trim().orEmpty()
                val newUrl = editUrl.text?.toString()?.trim().orEmpty()
                if (newTitle.isNotEmpty()) {
                    draft.title = newTitle
                    binding.labelTitle.text = newTitle
                    binding.txtWebAppName.setText(newTitle)
                }
                if (newUrl.isNotEmpty()) {
                    draft.baseUrl = newUrl
                    binding.txtSettingsHost.text = newUrl
                    binding.textBaseUrl.setText(newUrl)
                }
                setupIdentityPreview(draft)
                hideSoftKeyboard()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                hideSoftKeyboard()
            }
            .show()
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let { view ->
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        } ?: imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun setupInputTwoWaySync() {
        binding.txtWebAppName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val newTitle = s?.toString().orEmpty()
                if (newTitle != draft.title) {
                    binding.labelTitle.text = newTitle
                    draft.title = newTitle
                    setupIdentityPreview(draft)
                }
            }
        })
        binding.textBaseUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val newUrl = s?.toString().orEmpty()
                if (newUrl != draft.baseUrl) {
                    binding.txtSettingsHost.text = newUrl
                    draft.baseUrl = newUrl
                }
            }
        })
    }

    private fun setupInputFocusAutoScroll() {
        val inputs = listOf(
            binding.txtWebAppName,
            binding.textBaseUrl,
            binding.textUserAgent,
            binding.textReloadInterval,
            binding.textDarkModeBegin,
            binding.textDarkModeEnd
        )
        val bottomMargin = (84 * resources.displayMetrics.density).toInt()
        inputs.forEach { inputView ->
            inputView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            val rect = android.graphics.Rect()
                            view.getDrawingRect(rect)
                            rect.bottom += bottomMargin
                            binding.webAppSettingsScroll.requestChildRectangleOnScreen(view, rect, true)
                        }
                    }, 200)
                }
            }
        }
    }

    private fun setupSettingsColorGrid(modifiedWebapp: WebApp) {
        val grid = binding.settingsColorGrid
        grid.removeAllViews()
        val density = resources.displayMetrics.density
        val touchSize = (48 * density).toInt()
        val gap = (4 * density).toInt()
        val gridWidth = grid.width.takeIf { it > 0 }
        val columns = calculateColorColumns(gridWidth, grid.paddingStart, grid.paddingEnd)
        grid.columnCount = columns

        ColorPalette.HERMIT_COLORS.entries.forEachIndexed { index, (name, value) ->
            val swatch = View(this).apply {
                val selected = name.equals(modifiedWebapp.themeColor, ignoreCase = true)
                contentDescription = getString(
                    if (selected) R.string.theme_color_swatch_selected_description
                    else R.string.theme_color_swatch_description,
                    name
                )
                isSelected = selected
                background = colorSwatch(value, selected)
                setOnClickListener {
                    modifiedWebapp.themeColor = name
                    binding.spinnerThemeColor.setSelection(
                        ColorPalette.HERMIT_COLORS.keys.indexOf(name),
                        false
                    )
                    setupSettingsColorGrid(modifiedWebapp)
                    applyPreviewColor(modifiedWebapp)
                }
            }
            val isLastInRow = (index + 1) % columns == 0
            val params = GridLayout.LayoutParams().apply {
                width = touchSize
                height = touchSize
                marginEnd = if (isLastInRow) 0 else gap
                bottomMargin = gap
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            grid.addView(swatch, params)
        }
        if (gridWidth == null) {
            grid.post {
                val measuredWidth = grid.width.takeIf { it > 0 } ?: return@post
                if (grid.columnCount != calculateColorColumns(measuredWidth, grid.paddingStart, grid.paddingEnd)) {
                    setupSettingsColorGrid(modifiedWebapp)
                }
            }
        }
        applyPreviewColor(modifiedWebapp)
    }

    private fun calculateColorColumns(width: Int?, paddingStart: Int, paddingEnd: Int): Int {
        val density = resources.displayMetrics.density
        val touchSize = (48 * density).toInt()
        val gap = (4 * density).toInt()
        val horizontalInset = resources.getDimensionPixelSize(R.dimen.margin_activity) * 2
        val availableWidth = width?.takeIf { it > 0 }
            ?: (resources.configuration.screenWidthDp * density).toInt() - horizontalInset
        val usableWidth = (availableWidth - paddingStart - paddingEnd).coerceAtLeast(touchSize)
        return ((usableWidth + gap) / (touchSize + gap)).coerceIn(1, 7)
    }

    private fun colorSwatch(value: String, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(value))
            val strokeColor = if (selected) {
                if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
            } else {
                Color.argb(70, 128, 128, 128)
            }
            setStroke(
                if (selected) (4 * resources.displayMetrics.density).toInt()
                else (1 * resources.displayMetrics.density).toInt(),
                strokeColor
            )
        }

    private fun applyPreviewColor(modifiedWebapp: WebApp) {
        val color = ColorPalette.webAppColor(modifiedWebapp.themeColor, modifiedWebapp.containerId)
        binding.appIdentityCard.strokeColor = color
    }

    private fun fallbackFavicon(webapp: WebApp): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPalette.webAppColor(webapp.themeColor, webapp.containerId)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = ColorPalette.contrastingTextColor(paint.color)
        paint.textSize = 56f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val label = webapp.title.trim().firstOrNull()?.uppercase() ?: "?"
        canvas.drawText(label, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        return bitmap
    }

    private fun setupDarkModeElements() {
        val txtBeginDarkMode = binding.textDarkModeBegin
        val txtEndDarkMode = binding.textDarkModeEnd

        txtBeginDarkMode.minHeight = (48 * resources.displayMetrics.density).toInt()
        txtEndDarkMode.minHeight = (48 * resources.displayMetrics.density).toInt()
        txtBeginDarkMode.setOnClickListener {
            showTimePicker(
                txtBeginDarkMode
            )
        }
        txtEndDarkMode.setOnClickListener {
            showTimePicker(
                txtEndDarkMode
            )
        }

    }

    private fun setupDesktopUserAgentHint() {
        val txt = binding.txthintUserAgent
        txt.text = Html.fromHtml(getString(R.string.hint_user_agent), Html.FROM_HTML_MODE_LEGACY)
        txt.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupShortcutButton() {
        binding.btnRecreateShortcut.setOnClickListener {
            val frag = ShortcutDialogFragment.newInstance(webapp)
            frag.show(supportFragmentManager, "SCFetcher-" + webapp!!.ID)
        }
    }

    private fun setupPlusSettings() {
        if (!BuildConfig.FLAVOR.contains("extended")) {
            binding.sectionDarkmode.visibility = View.GONE
            binding.sectionKioskMode.visibility = View.GONE
            binding.sectionAccessRestriction.visibility = View.GONE
        }
    }

    private fun showTimePicker(txtField: EditText) {
        val c = convertStringToCalendar(txtField.text.toString())
        val timePickerDialog = TimePickerDialog(
            this@WebAppSettingsActivity, R.style.AppTheme,
            { timePicker: TimePicker?, selectedHour: Int, selectedMinute: Int ->
                val datetime = Calendar.getInstance()
                datetime[Calendar.HOUR_OF_DAY] = selectedHour
                datetime[Calendar.MINUTE] = selectedMinute
                txtField.setText(getHourMinFormat().format(datetime.time))
            }, c!![Calendar.HOUR_OF_DAY], c[Calendar.MINUTE], true
        )
        timePickerDialog.show()
    }

    private fun prepareGlobalWebAppScreen() {
        binding.btnRecreateShortcut.visibility = View.GONE
        binding.labelWebAppName.visibility = View.GONE
        binding.txtWebAppName.visibility = View.GONE
        binding.appIdentityCard.visibility = View.GONE
        binding.btnEditIdentity.visibility = View.GONE
        binding.cardSettingsSandbox.visibility = View.GONE
        binding.cardOverrideGlobal.visibility = View.GONE
        binding.switchOverrideGlobal.visibility = View.GONE
        binding.sectionSSL.visibility = View.GONE
        binding.labelTitle.visibility = View.GONE
        binding.labelEditableBaseUrl.visibility = View.GONE
        binding.textBaseUrl.visibility = View.GONE

        binding.globalSettingsInfoText.visibility = View.VISIBLE

        setToolbarTitle(getString(R.string.global_web_app_settings))
    }
}


