package com.cylonid.nativealpha

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TimePicker
import androidx.activity.addCallback
import com.cylonid.nativealpha.activities.ToolbarBaseActivity
import com.cylonid.nativealpha.databinding.WebappSettingsBinding
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.SandboxManager
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
import java.util.Calendar

class WebAppSettingsActivity : ToolbarBaseActivity<WebappSettingsBinding>() {
    companion object {
        const val EXTRA_UPDATED_WEBAPP = "updatedWebAppSettings"
    }

    var webappID: Int = -1
    var webapp: WebApp? = null
    private var isGlobalWebApp: Boolean = false

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
        val modifiedWebapp = SandboxManager.getInstance().normalizeSandbox(WebApp(webapp!!))
        binding.webapp = modifiedWebapp
        binding.activity = this@WebAppSettingsActivity

        setupIdentityPreview(modifiedWebapp)
        setupSettingsTabs()
        setupSaveAndCancel(modifiedWebapp)
        setupDarkModeElements()
        setupPlusSettings()
        setupDesktopUserAgentHint()
        setupShortcutButton()
        setupSwitchListeners(webapp!!)
        setupThemeColorSpinner(modifiedWebapp)
        setupSettingsColorGrid(modifiedWebapp)

    }

    override fun inflateBinding(layoutInflater: LayoutInflater): WebappSettingsBinding {
        return WebappSettingsBinding.inflate(layoutInflater)
    }

    private fun setupSwitchListeners(webapp: WebApp) {
        webapp.onSwitchExpertSettingsChanged(
            binding.switchExpertSettings,
            webapp.isShowExpertSettings
        )
        webapp.onSwitchOverrideGlobalSettingsChanged(
            binding.switchOverrideGlobal,
            webapp.isOverrideGlobalSettings
        )
    }
    
    private fun setupSaveAndCancel(modifiedWebapp: WebApp) {
        val saveAndFinish = {
            if (isGlobalWebApp) {
                DataManager.getInstance().settings.globalWebApp = modifiedWebapp
                DataManager.getInstance().saveGlobalSettings()
            } else {
                DataManager.getInstance().replaceWebApp(modifiedWebapp)
            }

            WebMonitorWorker.syncPeriodicWork(this)
            setResult(RESULT_OK, Intent()
                .putExtra(Const.INTENT_WEBAPP_CHANGED, true)
                .putExtra(EXTRA_UPDATED_WEBAPP, Gson().toJson(modifiedWebapp)))
            finish()
        }
        binding.btnSave.setOnClickListener { saveAndFinish() }
        onBackPressedDispatcher.addCallback(this) { saveAndFinish() }
        setNavigationClickListener { saveAndFinish() }
        binding.btnCancel.setOnClickListener { finish() }
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

    private fun setupSettingsTabs() {
        val tabs = listOf(
            binding.btnTabBehavior to binding.tabContainerBehavior,
            binding.btnTabPrivacy to binding.tabContainerPrivacy,
            binding.btnTabTheme to binding.tabContainerTheme,
            binding.btnTabTools to binding.tabContainerTools
        )
        tabs.forEach { (button, container) ->
            button.setOnClickListener {
                tabs.forEach { (otherButton, otherContainer) ->
                    val selected = otherContainer === container
                    otherContainer.visibility = if (selected) View.VISIBLE else View.GONE
                    otherButton.alpha = if (selected) 1f else 0.68f
                    otherButton.isSelected = selected
                }
            }
        }
        tabs[0].first.performClick()
    }

    private fun setupIdentityPreview(modifiedWebapp: WebApp) {
        val favicon = ShortcutIconUtils.loadFavicon(this, modifiedWebapp.ID)
        binding.imgSettingsFavicon.setImageBitmap(favicon ?: fallbackFavicon(modifiedWebapp))
        binding.txtSettingsSandbox.text =
            if (!SandboxManager.isValidContainerId(modifiedWebapp.containerId)) {
                getString(R.string.no_sandbox_badge)
            } else {
                getString(R.string.sandbox_badge, modifiedWebapp.containerId + 1)
            }
        val color = ColorPalette.colorForContainer(modifiedWebapp.containerId)
        binding.txtSettingsSandbox.background = GradientDrawable().apply {
            cornerRadius = resources.displayMetrics.density * 12f
            setColor(color)
        }
        binding.txtSettingsSandbox.setTextColor(ColorPalette.contrastingTextColor(color))
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
        binding.switchOverrideGlobal.visibility = View.GONE
        binding.sectionSSL.visibility = View.GONE
        binding.sectionSandbox.visibility = View.GONE
        binding.labelTitle.visibility = View.GONE
        binding.labelEditableBaseUrl.visibility = View.GONE
        binding.textBaseUrl.visibility = View.GONE

        binding.globalSettingsInfoText.visibility = View.VISIBLE

        setToolbarTitle(getString(R.string.global_web_app_settings))
    }
}


