package com.cylonid.nativealpha

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.SandboxManager
import com.cylonid.nativealpha.model.SandboxPreferences
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.ColorPalette
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.LegacySandboxStorage
import com.cylonid.nativealpha.util.ProcessUtils
import com.cylonid.nativealpha.util.ShortcutIconUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import java.io.File

class SandboxActivity : AppCompatActivity() {

    private lateinit var cardList: LinearLayout
    private var webapp: WebApp? = null
    private var selectionMode = false
    private var pickOnly = false
    private var selectedContainerId = Const.NO_CONTAINER
    private val expandedContainers = mutableSetOf<Int>()
    private val nameDrafts = mutableMapOf<Int, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sandbox)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sandboxRoot)) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        DataManager.getInstance().loadAppData()

        val webappId = intent.getIntExtra(Const.INTENT_WEBAPPID, Const.NO_CONTAINER)
        webapp = if (webappId != Const.NO_CONTAINER) {
            DataManager.getInstance().getWebAppIgnoringGlobalOverride(webappId, true)
        } else {
            null
        }
        pickOnly = intent.getBooleanExtra(EXTRA_PICK_ONLY, false)
        selectionMode = pickOnly || webapp != null
        selectedContainerId = normalizeId(savedInstanceState?.getInt(STATE_SELECTED_CONTAINER)
            ?: intent.getIntExtra(EXTRA_SELECTED_CONTAINER_ID, webapp?.containerId ?: Const.NO_CONTAINER))

        cardList = findViewById(R.id.sandboxCardList)
        findViewById<View>(R.id.btnSandboxBack).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        onBackPressedDispatcher.addCallback(this) {
            setResult(RESULT_CANCELED)
            finish()
        }

        if (!selectionMode) expandedContainers.add(selectedContainerId)
        savedInstanceState?.getIntArray(STATE_EXPANDED_CONTAINERS)?.let {
            expandedContainers.clear()
            expandedContainers.addAll(it.toList())
        }
        savedInstanceState?.getIntArray(STATE_NAME_IDS)?.forEach { id ->
            savedInstanceState?.getString("$STATE_NAME_PREFIX$id")?.let { nameDrafts[id] = it }
        }
        findViewById<TextView>(R.id.txtSandboxTitle).setText(
            if (selectionMode) R.string.sandbox_interaction_choose_title
            else R.string.sandbox_interaction_manage_title
        )
        findViewById<TextView>(R.id.txtSandboxDescription).setText(
            when {
                pickOnly -> R.string.sandbox_interaction_draft_help
                selectionMode -> R.string.sandbox_interaction_apply_help
                else -> R.string.sandbox_interaction_manage_help
            }
        )
        findViewById<MaterialButton>(R.id.btnSandboxApply).apply {
            visibility = if (selectionMode && !pickOnly) View.VISIBLE else View.GONE
            setOnClickListener { commitSelection() }
        }
        renderCards()
    }

    override fun onResume() {
        super.onResume()
        DataManager.getInstance().loadAppData()
        if (selectionMode && webapp != null) {
            webapp = DataManager.getInstance()
                .getWebAppIgnoringGlobalOverride(webapp?.ID ?: Const.NO_CONTAINER, true)
        }
        renderCards()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putIntArray(
            STATE_EXPANDED_CONTAINERS,
            expandedContainers.toIntArray()
        )
        outState.putInt(STATE_SELECTED_CONTAINER, selectedContainerId)
        outState.putIntArray(STATE_NAME_IDS, nameDrafts.keys.toIntArray())
        nameDrafts.forEach { (id, name) -> outState.putString("$STATE_NAME_PREFIX$id", name) }
        super.onSaveInstanceState(outState)
    }

    private fun renderCards() {
        cardList.removeAllViews()
        findViewById<TextView>(R.id.txtSandboxCurrent).apply {
            visibility = if (selectionMode) View.VISIBLE else View.GONE
            text = getString(
                if (pickOnly) R.string.sandbox_interaction_draft_selected
                else R.string.sandbox_interaction_current_selected,
                containerTitle(this@SandboxActivity, selectedContainerId)
            )
        }
        val apps = DataManager.getInstance().activeWebsites
        renderCard(Const.NO_CONTAINER, apps.filter { !SandboxManager.isValidContainerId(it.containerId) })
        repeat(SandboxManager.getSandboxCount()) { containerId ->
            renderCard(containerId, apps.filter { it.containerId == containerId })
        }
    }

    private fun renderCard(containerId: Int, apps: List<WebApp>) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_sandbox_card, cardList, false) as MaterialCardView
        // Repeated card IDs must not restore another sandbox's text or Cookie switch state.
        card.isSaveFromParentEnabled = false
        val header = card.findViewById<View>(R.id.sandboxCardHeader)
        val dot = card.findViewById<View>(R.id.sandboxColorDot)
        val name = card.findViewById<TextView>(R.id.sandboxName)
        val count = card.findViewById<TextView>(R.id.sandboxCount)
        val expandIcon = card.findViewById<ImageView>(R.id.sandboxExpandIcon)
        val expandedContent = card.findViewById<View>(R.id.sandboxExpandedContent)
        val appList = card.findViewById<LinearLayout>(R.id.sandboxAppList)
        val empty = card.findViewById<TextView>(R.id.sandboxEmpty)
        val title = containerTitle(this, containerId)
        val expanded = !selectionMode && expandedContainers.contains(containerId)
        val selected = selectionMode && selectedContainerId == containerId

        name.text = title
        count.text = getString(
            R.string.sandbox_interaction_card_summary,
            getString(R.string.sandbox_ui_active_count, apps.size),
            cookieLabel(containerId)
        )
        dot.background = dotDrawable(containerId)
        expandedContent.visibility = if (expanded) View.VISIBLE else View.GONE
        expandIcon.visibility = if (selectionMode) View.GONE else View.VISIBLE
        expandIcon.rotation = if (expanded) 180f else 0f
        header.isSelected = selected
        card.findViewById<TextView>(R.id.sandboxCurrent).apply {
            visibility = if (selected) View.VISIBLE else View.GONE
            setText(if (pickOnly) R.string.sandbox_interaction_draft_marker
                else R.string.sandbox_interaction_selected)
        }
        card.isSelected = selected
        card.strokeWidth = if (selected) dp(2) else dp(1)
        card.strokeColor = if (selected) {
            ColorPalette.colorForContainer(containerId)
        } else {
            themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        }

        val countDescription = getString(R.string.sandbox_ui_active_count, apps.size)
        header.contentDescription = if (selectionMode) {
            getString(
                if (selected) R.string.sandbox_ui_selected_description
                else R.string.sandbox_ui_select_description,
                title,
                webapp?.title ?: getString(R.string.web_app_settings)
            ) + ", " + countDescription
        } else {
            getString(
                if (expanded) R.string.sandbox_ui_manage_expanded_description
                else R.string.sandbox_ui_manage_description,
                title,
                apps.size
            )
        }

        header.setOnClickListener {
            if (selectionMode) {
                selectContainer(containerId)
            } else {
                toggleExpanded(containerId)
            }
        }

        val btnEditName = card.findViewById<ImageView>(R.id.btnSandboxEditName)
        if (!selectionMode) {
            btnEditName.visibility = View.VISIBLE
            btnEditName.setOnClickListener { showRenameDialog(containerId) }
            card.findViewById<View>(R.id.btnSandboxClearData).setOnClickListener {
                showClearDataConfirmDialog(containerId)
            }
            setupPolicyEditor(card, containerId)
            val legacySuffixes = if (SandboxManager.isValidContainerId(containerId)) {
                LegacySandboxStorage.getLegacySuffixes(this, containerId)
            } else emptyList()
            if (expanded && legacySuffixes.isNotEmpty()) {
                setupLegacyRecovery(card, containerId)
            } else {
                card.findViewById<View>(R.id.sandboxLegacyRecovery).visibility = View.GONE
            }
        } else {
            btnEditName.visibility = View.GONE
            card.findViewById<View>(R.id.sandboxPolicyEditor).visibility = View.GONE
            card.findViewById<View>(R.id.sandboxActionsBar).visibility = View.GONE
        }
        val appsHeading = card.findViewById<View>(R.id.sandboxAppsHeading)
        if (apps.isEmpty()) {
            appsHeading?.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            appsHeading?.visibility = View.VISIBLE
            empty.visibility = View.GONE
            apps.forEach { app -> appList.addView(appRow(app)) }
        }
        cardList.addView(card)
    }

    private fun toggleExpanded(containerId: Int) {
        if (!expandedContainers.add(containerId)) {
            expandedContainers.remove(containerId)
        }
        renderCards()
    }

    private fun selectContainer(containerId: Int) {
        selectedContainerId = normalizeId(containerId)
        if (pickOnly) {
            // A settings picker returns a draft choice and never writes configuration.
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_CONTAINER_ID, selectedContainerId))
            finish()
            return
        }
        renderCards()
    }

    private fun commitSelection() {
        if (pickOnly) return
        val current = webapp ?: return
        val latest = DataManager.getInstance()
            .getWebAppIgnoringGlobalOverride(current.ID, true) ?: return
        val updated = WebApp(latest).apply {
            containerId = selectedContainerId
            isUseContainer = SandboxManager.isValidContainerId(selectedContainerId)
        }
        DataManager.getInstance().replaceWebApp(updated)
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(Const.INTENT_WEBAPPID, latest.ID)
                .putExtra(EXTRA_SELECTED_CONTAINER_ID, updated.containerId)
        )
        finish()
    }

    private fun setupPolicyEditor(card: View, containerId: Int) {
        val nameInput = card.findViewById<EditText>(R.id.sandboxNameInput)
        val saveName = card.findViewById<MaterialButton>(R.id.btnSandboxSaveName)
        val savedName = SandboxPreferences.getName(containerId)
        nameInput.hint = containerTitle(this, containerId)
        nameInput.setText(nameDrafts[containerId] ?: savedName)
        saveName.isEnabled = nameInput.text.toString().trim() != savedName
        nameInput.doAfterTextChanged {
            nameDrafts[containerId] = it.toString()
            saveName.isEnabled = it.toString().trim() != SandboxPreferences.getName(containerId)
        }
        val commitName = {
            SandboxPreferences.setName(containerId, nameInput.text.toString().trim())
            nameDrafts.remove(containerId)
            renderCards()
        }
        saveName.setOnClickListener { commitName() }
        nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitName()
                true
            } else false
        }
        card.findViewById<MaterialSwitch>(R.id.switchSandboxCookies).apply {
            isChecked = SandboxPreferences.isCookiesAllowed(containerId)
            setOnCheckedChangeListener { _, checked ->
                SandboxPreferences.setCookiesAllowed(containerId, checked)
                card.findViewById<TextView>(R.id.sandboxCount).text = getString(
                    R.string.sandbox_interaction_card_summary,
                    getString(R.string.sandbox_ui_active_count, assignedAppCount(containerId)),
                    cookieLabel(containerId)
                )
                card.findViewById<TextView>(R.id.sandboxPolicyImpact).announceForAccessibility(
                    getString(R.string.sandbox_interaction_cookie_scope)
                )
            }
        }
    }

    private fun cookieLabel(containerId: Int): String = getString(
        if (SandboxPreferences.isCookiesAllowed(containerId))
            R.string.sandbox_interaction_cookie_allowed
        else R.string.sandbox_interaction_cookie_blocked
    )

    private fun setupLegacyRecovery(card: View, containerId: Int) {
        card.findViewById<View>(R.id.sandboxLegacyRecovery).visibility = View.VISIBLE
        val spinner = card.findViewById<Spinner>(R.id.spinnerSandboxLegacySession)
        val apply = card.findViewById<MaterialButton>(R.id.btnSandboxRecoverSession)
        val status = card.findViewById<TextView>(R.id.txtSandboxRecoveryStatus)
        val sourceHint = card.findViewById<TextView>(R.id.txtSandboxRecoverySource)
        val suffixes = LegacySandboxStorage.getLegacySuffixes(this, containerId)
        val current = LegacySandboxStorage.getSelectedSuffix(this, containerId)
        if (suffixes.isEmpty()) {
            spinner.visibility = View.GONE
            apply.visibility = View.GONE
            status.setText(R.string.sandbox_interaction_recover_empty)
            return
        }
        val appsById = DataManager.getInstance().websites.associateBy { it.ID }
        var unknownSession = 0
        val options = suffixes.map { suffix ->
            val appId = suffix.substringAfterLast('_', "").toIntOrNull()
            val sourceTitle = appsById[appId]?.title?.trim()?.takeIf { it.isNotEmpty() }
            LegacySessionOption(
                suffix,
                if (sourceTitle != null) {
                    getString(R.string.sandbox_interaction_recover_from_app, sourceTitle)
                } else {
                    getString(R.string.sandbox_interaction_recover_numbered, ++unknownSession)
                },
                sourceTitle != null
            )
        }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.setSelection(suffixes.indexOf(current).coerceAtLeast(0))
        status.text = getString(R.string.sandbox_interaction_recover_current,
            options.firstOrNull { it.suffix == current }?.label
                ?: getString(R.string.sandbox_interaction_recover_default))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sourceHint.visibility = if (options[position].sourceKnown) View.GONE else View.VISIBLE
                apply.isEnabled = options[position].suffix !=
                    LegacySandboxStorage.getSelectedSuffix(this@SandboxActivity, containerId)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        sourceHint.visibility = if (options[spinner.selectedItemPosition].sourceKnown) View.GONE else View.VISIBLE
        apply.isEnabled = options[spinner.selectedItemPosition].suffix != current
        apply.setOnClickListener {
            val selected = options.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
            val accepted = LegacySandboxStorage.selectLegacySuffixForNextStart(this, containerId, selected.suffix)
            val saved = LegacySandboxStorage.getSelectedSuffix(this, containerId)
            status.text = if (accepted && saved == selected.suffix) {
                getString(R.string.sandbox_interaction_recover_pending, selected.label)
            } else {
                getString(R.string.sandbox_interaction_recover_failed)
            }
            apply.isEnabled = saved != selected.suffix
        }
    }

    private data class LegacySessionOption(
        val suffix: String,
        val label: String,
        val sourceKnown: Boolean
    ) {
        override fun toString(): String = label
    }

    private fun assignedAppCount(containerId: Int): Int =
        DataManager.getInstance().activeWebsites.count { normalizeId(it.containerId) == containerId }

    private fun normalizeId(containerId: Int): Int =
        if (SandboxManager.isValidContainerId(containerId)) containerId else Const.NO_CONTAINER

    private fun appRow(app: WebApp): View {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_sandbox_app, cardList, false)
        val icon = row.findViewById<ImageView>(R.id.sandboxAppIcon)
        val title = row.findViewById<TextView>(R.id.sandboxAppTitle)
        title.text = app.title
        val favicon = ShortcutIconUtils.loadFavicon(this, app.ID)
        icon.setImageBitmap(favicon ?: fallbackIcon(app))
        row.contentDescription = getString(
            R.string.sandbox_ui_open_app_settings
        ) + ": " + app.title
        row.setOnClickListener {
            startActivity(
                Intent(this, WebAppSettingsActivity::class.java)
                    .putExtra(Const.INTENT_WEBAPPID, app.ID)
            )
        }
        return row
    }

    private fun dotDrawable(containerId: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorPalette.colorForContainer(containerId))
            setStroke(
                dp(2),
                if (isNightMode()) Color.WHITE else Color.BLACK
            )
        }

    private fun fallbackIcon(app: WebApp): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPalette.webAppColor(app.themeColor, app.containerId)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, background)
        val foreground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPalette.contrastingTextColor(background.color)
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val label = app.title.trim().firstOrNull()?.uppercase() ?: "?"
        val metrics = foreground.fontMetrics
        canvas.drawText(
            label,
            size / 2f,
            size / 2f - (metrics.ascent + metrics.descent) / 2f,
            foreground
        )
        return bitmap
    }

    private fun themeColor(attr: Int): Int {
        val value = android.util.TypedValue()
        if (!theme.resolveAttribute(attr, value, true)) return Color.GRAY
        return if (value.resourceId != 0) getColor(value.resourceId) else value.data
    }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showRenameDialog(containerId: Int) {
        val currentName = SandboxPreferences.getName(containerId).ifBlank {
            containerTitle(this, containerId)
        }
        val padding = dp(20)
        val input = EditText(this).apply {
            setText(currentName)
            selectAll()
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(padding, padding, padding, padding)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sandbox_action_rename)
            .setView(input)
            .setPositiveButton(R.string.sandbox_interaction_save_name) { _, _ ->
                val newName = input.text.toString().trim()
                SandboxPreferences.setName(containerId, newName)
                nameDrafts.remove(containerId)
                renderCards()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showClearDataConfirmDialog(containerId: Int) {
        val title = containerTitle(this, containerId)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sandbox_clear_data_confirm_title)
            .setMessage(getString(R.string.sandbox_clear_data_confirm_message, title))
            .setPositiveButton(R.string.sandbox_action_clear_data) { _, _ ->
                clearSandboxData(this, containerId)
                Snackbar.make(
                    findViewById(R.id.sandboxRoot),
                    getString(R.string.sandbox_clear_data_success, title),
                    Snackbar.LENGTH_SHORT
                ).show()
                renderCards()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_PICK_ONLY = "sandboxPickOnly"
        const val EXTRA_SELECTED_CONTAINER_ID = "selectedSandboxContainerId"
        private const val STATE_EXPANDED_CONTAINERS = "expanded_sandbox_containers"
        private const val STATE_SELECTED_CONTAINER = "selected_sandbox_container"
        private const val STATE_NAME_IDS = "sandbox_name_ids"
        private const val STATE_NAME_PREFIX = "sandbox_name_"

        @JvmStatic
        fun clearSandboxData(context: Context, containerId: Int) {
            if (SandboxManager.isValidContainerId(containerId)) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (am != null) {
                    ProcessUtils.killWebSandbox(containerId, am)
                }
                try {
                    val dataDir = context.dataDir
                    val webviewDir = File(dataDir, "app_webview_sandbox_$containerId")
                    if (webviewDir.exists()) {
                        webviewDir.deleteRecursively()
                    }
                    val cacheDir = File(context.cacheDir, "app_webview_sandbox_$containerId")
                    if (cacheDir.exists()) {
                        cacheDir.deleteRecursively()
                    }
                } catch (_: Exception) {}
            } else {
                try {
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.WebStorage.getInstance().deleteAllData()
                } catch (_: Exception) {}
            }
        }

        @JvmStatic
        fun containerTitle(context: Context, containerId: Int): String {
            SandboxPreferences.getName(containerId).takeIf { it.isNotBlank() }?.let { return it }
            val resourceId = when (containerId) {
                0 -> R.string.sandbox_ui_color_blue
                1 -> R.string.sandbox_ui_color_green
                2 -> R.string.sandbox_ui_color_orange
                3 -> R.string.sandbox_ui_color_purple
                4 -> R.string.sandbox_ui_color_teal
                5 -> R.string.sandbox_ui_color_pink
                6 -> R.string.sandbox_ui_color_amber
                7 -> R.string.sandbox_ui_color_indigo
                else -> R.string.sandbox_ui_default_name
            }
            return context.getString(resourceId)
        }
    }
}
