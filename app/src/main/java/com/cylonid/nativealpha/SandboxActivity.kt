package com.cylonid.nativealpha

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.SandboxManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.ColorPalette
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.ShortcutIconUtils
import com.google.android.material.card.MaterialCardView

class SandboxActivity : AppCompatActivity() {

    private lateinit var cardList: LinearLayout
    private var webapp: WebApp? = null
    private var selectionMode = false
    private val expandedContainers = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sandbox)
        DataManager.getInstance().loadAppData()

        val webappId = intent.getIntExtra(Const.INTENT_WEBAPPID, Const.NO_CONTAINER)
        webapp = if (webappId != Const.NO_CONTAINER) {
            DataManager.getInstance().getWebAppIgnoringGlobalOverride(webappId, true)
        } else {
            null
        }
        selectionMode = webapp != null

        cardList = findViewById(R.id.sandboxCardList)
        findViewById<View>(R.id.btnSandboxBack).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        onBackPressedDispatcher.addCallback(this) {
            setResult(RESULT_CANCELED)
            finish()
        }

        if (selectionMode) {
            expandedContainers.add(webapp!!.containerId)
        } else {
            expandedContainers.add(Const.NO_CONTAINER)
        }
        savedInstanceState?.getIntArray(STATE_EXPANDED_CONTAINERS)?.let {
            expandedContainers.clear()
            expandedContainers.addAll(it.toList())
        }
        renderCards()
    }

    override fun onResume() {
        super.onResume()
        DataManager.getInstance().loadAppData()
        if (selectionMode) {
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
        super.onSaveInstanceState(outState)
    }

    private fun renderCards() {
        cardList.removeAllViews()
        val apps = DataManager.getInstance().activeWebsites
        renderCard(Const.NO_CONTAINER, apps.filter { !SandboxManager.isValidContainerId(it.containerId) })
        repeat(SandboxManager.getSandboxCount()) { containerId ->
            renderCard(containerId, apps.filter { it.containerId == containerId })
        }
    }

    private fun renderCard(containerId: Int, apps: List<WebApp>) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_sandbox_card, cardList, false) as MaterialCardView
        val header = card.findViewById<View>(R.id.sandboxCardHeader)
        val dot = card.findViewById<View>(R.id.sandboxColorDot)
        val name = card.findViewById<TextView>(R.id.sandboxName)
        val count = card.findViewById<TextView>(R.id.sandboxCount)
        val expandIcon = card.findViewById<ImageView>(R.id.sandboxExpandIcon)
        val expandedContent = card.findViewById<View>(R.id.sandboxExpandedContent)
        val appList = card.findViewById<LinearLayout>(R.id.sandboxAppList)
        val empty = card.findViewById<TextView>(R.id.sandboxEmpty)
        val isDefault = !SandboxManager.isValidContainerId(containerId)
        val title = containerTitle(containerId)
        val expanded = expandedContainers.contains(containerId)
        val selected = webapp?.let {
            if (isDefault) !SandboxManager.isValidContainerId(it.containerId)
            else it.containerId == containerId
        } ?: false

        name.text = title
        count.text = getString(
            R.string.sandbox_ui_active_count,
            apps.size
        )
        dot.background = dotDrawable(containerId)
        expandedContent.visibility = if (expanded) View.VISIBLE else View.GONE
        expandIcon.visibility = if (selectionMode) View.GONE else View.VISIBLE
        expandIcon.rotation = if (expanded) 180f else 0f
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
                webapp!!.title
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

        if (apps.isEmpty()) {
            empty.visibility = View.VISIBLE
        } else {
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
        val current = webapp ?: return
        val normalizedId = if (SandboxManager.isValidContainerId(containerId)) {
            containerId
        } else {
            Const.NO_CONTAINER
        }
        val latest = DataManager.getInstance()
            .getWebAppIgnoringGlobalOverride(current.ID, true) ?: return
        latest.containerId = normalizedId
        latest.isUseContainer = SandboxManager.isValidContainerId(normalizedId)
        DataManager.getInstance().replaceWebApp(latest)
        DataManager.getInstance().saveWebAppData()
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(Const.INTENT_WEBAPPID, latest.ID)
                .putExtra(EXTRA_SELECTED_CONTAINER_ID, latest.containerId)
        )
        finish()
    }

    private fun containerTitle(containerId: Int): String {
        if (!SandboxManager.isValidContainerId(containerId)) {
            return getString(R.string.sandbox_ui_default_name)
        }
        val colorName = ColorPalette.containerColorNames()[containerId]
        val resourceId = when (colorName) {
            "Blue" -> R.string.sandbox_ui_color_blue
            "Green" -> R.string.sandbox_ui_color_green
            "Orange" -> R.string.sandbox_ui_color_orange
            "Purple" -> R.string.sandbox_ui_color_purple
            "Teal" -> R.string.sandbox_ui_color_teal
            "Pink" -> R.string.sandbox_ui_color_pink
            "Amber" -> R.string.sandbox_ui_color_amber
            else -> R.string.sandbox_ui_color_indigo
        }
        return getString(resourceId)
    }

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

    companion object {
        const val EXTRA_SELECTED_CONTAINER_ID = "selectedSandboxContainerId"
        private const val STATE_EXPANDED_CONTAINERS = "expanded_sandbox_containers"
    }
}
