package com.cylonid.nativealpha.fragments.webapplist

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ImageSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.annotation.VisibleForTesting
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.SandboxActivity
import com.cylonid.nativealpha.ShortcutDialogFragment
import com.cylonid.nativealpha.WebAppSettingsActivity
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.SandboxManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.ColorPalette
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.FaviconFetcher
import com.cylonid.nativealpha.util.ShortcutIconUtils
import com.cylonid.nativealpha.util.WebViewLauncher.startWebView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WebAppListAdapter(
    dataSet: List<WebApp> = emptyList(),
    private val activityOfFragment: Activity,
    private val onDeleteRequested: (WebApp) -> Unit
)
    : DragDropSwipeAdapter<WebApp, WebAppListAdapter.ViewHolder>(dataSet) {

    class ViewHolder(webAppLayout: View) : DragDropSwipeAdapter.ViewHolder(webAppLayout) {
        val dragAnchor: View = itemView.findViewById(R.id.dragAnchor)
        val iconTile: View = itemView.findViewById(R.id.iconTile)
        val textContainer: View = itemView.findViewById(R.id.homeItemTextContainer)
        val titleView: TextView = itemView.findViewById(R.id.btnWebAppTitle)
        val urlView: TextView = itemView.findViewById(R.id.txtWebAppUrl)
        val faviconView: ImageView = itemView.findViewById(R.id.imgFavicon)
        val containerDot: View = itemView.findViewById(R.id.containerDot)
        val settingsButton: ImageButton = itemView.findViewById(R.id.btnWebAppSettings)
        val overflowButton: ImageButton = itemView.findViewById(R.id.btnWebAppMenu)
    }

    private var query: String = ""
    var isListMode: Boolean = false

    override fun getViewHolder(itemView: View) = ViewHolder(itemView)
    @VisibleForTesting
    fun getViewHolderForTest(itemView: View) = getViewHolder(itemView)

    @VisibleForTesting
    fun onBindViewHolderForTest(item: WebApp, viewHolder: ViewHolder, position: Int) {
        onBindViewHolder(item, viewHolder, position)
    }

    // The item itself owns tap and long press. Menu actions remain keyboard accessible.
    override fun canBeDragged(item: WebApp, viewHolder: ViewHolder, position: Int): Boolean = false
    override fun canBeSwiped(item: WebApp, viewHolder: ViewHolder, position: Int): Boolean = false

    override fun onBindViewHolder(item: WebApp, viewHolder: ViewHolder, position: Int) {
        bindHomeViewModeLayout(viewHolder)

        // Hermit-style inline container dot: ImageSpan embedded in the title string
        if (SandboxManager.isValidContainerId(item.containerId)) {
            val dotColor = ColorPalette.colorForContainer(item.containerId)
            val dotDrawable = ContextCompat.getDrawable(activityOfFragment, R.drawable.container_dot)?.mutate()
            if (dotDrawable != null) {
                dotDrawable.setTint(dotColor)
                val dotSize = (8 * activityOfFragment.resources.displayMetrics.density).toInt()
                dotDrawable.setBounds(0, 0, dotSize, dotSize)
                val spannable = SpannableString("   ${item.title}")
                spannable.setSpan(ImageSpan(dotDrawable, ImageSpan.ALIGN_BASELINE), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                viewHolder.titleView.text = spannable
            } else {
                viewHolder.titleView.text = item.title
            }
            viewHolder.itemView.contentDescription =
                activityOfFragment.getString(
                    R.string.open_web_app_in_sandbox,
                    item.title,
                    item.containerId + 1
                )
        } else {
            viewHolder.titleView.text = item.title
            viewHolder.itemView.contentDescription =
                activityOfFragment.getString(R.string.open_web_app, item.title)
        }
        viewHolder.urlView.text = displayUrl(item.baseUrl)
        viewHolder.containerDot.visibility = View.GONE

        // Single and reliable click / long-click routing.
        val clickAction = View.OnClickListener { openWebView(item) }
        val longClickAction = View.OnLongClickListener {
            showItemContextMenu(item)
            true
        }

        viewHolder.itemView.setOnClickListener(clickAction)
        viewHolder.itemView.setOnLongClickListener(longClickAction)

        viewHolder.settingsButton.setOnClickListener {
            openSettings(item)
        }
        viewHolder.overflowButton.setOnClickListener {
            showItemContextMenu(item)
        }

        // Favicon handling: load from memory cache / disk, and backfill title if needed
        val cachedFavicon = ShortcutIconUtils.loadFavicon(activityOfFragment, item.ID)
        val isDefaultOrUrl = isDefaultOrUrlTitle(item.title, item.baseUrl)

        if (cachedFavicon != null) {
            viewHolder.faviconView.setImageBitmap(cachedFavicon)
            if (isDefaultOrUrl && !FaviconFetcher.isFailedInCooldown(item.ID)) {
                FaviconFetcher.fetchAndSaveFaviconAsync(activityOfFragment, item) {
                    val currentPosition = dataSet.indexOfFirst { it.ID == item.ID }
                    if (currentPosition >= 0) {
                        val updatedApp = DataManager.getInstance().websites.firstOrNull { it.ID == item.ID }
                        val hasNewFavicon = ShortcutIconUtils.loadFavicon(activityOfFragment, item.ID) != null
                        val titleChanged = updatedApp != null && updatedApp.title != dataSet[currentPosition].title
                        if (hasNewFavicon || titleChanged) {
                            if (updatedApp != null) {
                                dataSet[currentPosition].title = updatedApp.title
                            }
                            notifyItemChanged(currentPosition)
                        }
                    }
                }
            }
        } else {
            viewHolder.faviconView.setImageBitmap(createFallbackIcon(item))
            if (!FaviconFetcher.isFailedInCooldown(item.ID)) {
                FaviconFetcher.fetchAndSaveFaviconAsync(activityOfFragment, item) {
                    val currentPosition = dataSet.indexOfFirst { it.ID == item.ID }
                    if (currentPosition >= 0) {
                        val updatedApp = DataManager.getInstance().websites.firstOrNull { it.ID == item.ID }
                        val hasNewFavicon = ShortcutIconUtils.loadFavicon(activityOfFragment, item.ID) != null
                        val titleChanged = updatedApp != null && updatedApp.title != dataSet[currentPosition].title
                        if (hasNewFavicon || titleChanged) {
                            if (updatedApp != null) {
                                dataSet[currentPosition].title = updatedApp.title
                            }
                            notifyItemChanged(currentPosition)
                        }
                    }
                }
            }
        }

        viewHolder.settingsButton.contentDescription =
            activityOfFragment.getString(R.string.open_settings) + ": " + item.title
        viewHolder.overflowButton.contentDescription =
            activityOfFragment.getString(R.string.home_item_overflow, item.title)
    }

    private fun bindHomeViewModeLayout(viewHolder: ViewHolder) {
        val root = viewHolder.itemView as LinearLayout
        val textContainer = viewHolder.textContainer as LinearLayout
        val density = activityOfFragment.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        if (isListMode) {
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = android.view.Gravity.CENTER_VERTICAL
            root.minimumHeight = dp(72)
            root.setPadding(dp(4), dp(4), dp(2), dp(4))

            viewHolder.iconTile.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginEnd = dp(8)
            }
            textContainer.orientation = LinearLayout.VERTICAL
            textContainer.gravity = android.view.Gravity.CENTER_VERTICAL
            textContainer.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(2)
            }
            viewHolder.titleView.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setLines(1)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1f)
            }
            viewHolder.urlView.apply {
                visibility = View.VISIBLE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            configureActionButton(viewHolder.settingsButton, ::dp)
            configureActionButton(viewHolder.overflowButton, ::dp)
            viewHolder.settingsButton.visibility = View.VISIBLE
            viewHolder.overflowButton.visibility = View.VISIBLE
        } else {
            root.orientation = LinearLayout.VERTICAL
            root.gravity = android.view.Gravity.CENTER_HORIZONTAL
            root.minimumHeight = dp(104)
            root.setPadding(dp(6), dp(10), dp(6), dp(10))

            viewHolder.iconTile.layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            textContainer.orientation = LinearLayout.VERTICAL
            textContainer.gravity = android.view.Gravity.CENTER_HORIZONTAL
            textContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
            viewHolder.titleView.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setLines(2)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            viewHolder.urlView.visibility = View.GONE
            viewHolder.settingsButton.visibility = View.GONE
            viewHolder.overflowButton.visibility = View.GONE
        }
    }

    private fun isDefaultOrUrlTitle(title: String, baseUrl: String): Boolean {
        if (title.isBlank()) return true
        val normalizedTitle = title.trim().trimEnd('/')
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val readableUrl = normalizedUrl.removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
        val host = android.net.Uri.parse(normalizedUrl).host?.removePrefix("www.")
        return normalizedTitle.equals(normalizedUrl, ignoreCase = true) ||
                normalizedTitle.equals(readableUrl, ignoreCase = true) ||
                (!host.isNullOrBlank() && normalizedTitle.equals(host, ignoreCase = true)) ||
                title.contains("://") || title.endsWith("/")
    }

    private fun configureActionButton(button: ImageButton, dp: (Int) -> Int) {
        button.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        button.minimumWidth = dp(48)
        button.minimumHeight = dp(48)
        button.setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun displayUrl(baseUrl: String): String {
        return baseUrl.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    private fun showItemContextMenu(webapp: WebApp) {
        val options = arrayOf(
            activityOfFragment.getString(R.string.lite_app_settings),
            activityOfFragment.getString(R.string.change_sandbox),
            activityOfFragment.getString(R.string.create_shortcut_on_home_screen),
            activityOfFragment.getString(R.string.delete_lite_app),
            activityOfFragment.getString(R.string.move_app_up),
            activityOfFragment.getString(R.string.move_app_down)
        )

        MaterialAlertDialogBuilder(activityOfFragment)
            .setTitle(webapp.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSettings(webapp)
                    1 -> showChangeSandboxDialog(webapp)
                    2 -> {
                        val frag = ShortcutDialogFragment.newInstance(webapp)
                        if (activityOfFragment is androidx.fragment.app.FragmentActivity) {
                            frag.show(activityOfFragment.supportFragmentManager, "SCFetcher-" + webapp.ID)
                        }
                    }
                    3 -> onDeleteRequested(webapp)
                    4 -> moveWebApp(webapp, -1)
                    5 -> moveWebApp(webapp, 1)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun moveWebApp(webapp: WebApp, offset: Int) {
        val position = dataSet.indexOfFirst { it.ID == webapp.ID }
        val targetPosition = position + offset
        if (position < 0 || targetPosition !in dataSet.indices) return

        val allItems = DataManager.getInstance().activeWebsites.toMutableList()
        val sourceIndex = allItems.indexOfFirst { it.ID == webapp.ID }
        val targetId = dataSet[targetPosition].ID
        val targetIndex = allItems.indexOfFirst { it.ID == targetId }
        if (sourceIndex < 0 || targetIndex < 0) return
        allItems.add(targetIndex, allItems.removeAt(sourceIndex))
        allItems.forEachIndexed { index, app -> app.order = index }
        DataManager.getInstance().saveWebAppData()
        adapterUpdateWebAppList()
    }

    private fun showChangeSandboxDialog(webapp: WebApp) {
        val intent =
            Intent(activityOfFragment, SandboxActivity::class.java)
                .putExtra(Const.INTENT_WEBAPPID, webapp.ID)
        activityOfFragment.startActivity(intent)
    }

    fun updateWebAppList() {
        adapterUpdateWebAppList()
    }

    fun filter(newQuery: String) {
        query = newQuery.trim()
        adapterUpdateWebAppList()
    }

    private fun adapterUpdateWebAppList() {
        val allItems = DataManager.getInstance().activeWebsites.sortedBy { it.order }
        dataSet = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.baseUrl.contains(query, ignoreCase = true)
            }
        }
    }

    private fun createFallbackIcon(webapp: WebApp): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPalette.webAppColor(webapp.themeColor, webapp.containerId)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, background)

        val label = webapp.title.trim().firstOrNull()?.uppercase() ?: "?"
        val foreground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPalette.contrastingTextColor(background.color)
            textSize = 60f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val metrics = foreground.fontMetrics
        canvas.drawText(label, size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, foreground)
        return bitmap
    }

    private fun openWebView(webapp: WebApp) {
        startWebView(webapp, activityOfFragment)
    }

    private fun openSettings(webapp: WebApp) {
        val intent = Intent(activityOfFragment, WebAppSettingsActivity::class.java)
        intent.putExtra(Const.INTENT_WEBAPPID, webapp.ID)
        activityOfFragment.startActivity(intent)
    }

    override fun getViewToTouchToStartDraggingItem(item: WebApp, viewHolder: ViewHolder, position: Int): View {
        return viewHolder.dragAnchor
    }
}
