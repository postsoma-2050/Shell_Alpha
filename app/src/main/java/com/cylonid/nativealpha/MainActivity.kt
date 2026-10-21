package com.cylonid.nativealpha

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.cylonid.nativealpha.databinding.AddWebsiteDialogueBinding
import com.cylonid.nativealpha.fragments.webapplist.WebAppListFragment
import com.cylonid.nativealpha.helper.AdblockLifecycleHelper
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.SandboxManager
import com.cylonid.nativealpha.model.SandboxPreferences
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.monitors.WebMonitorWorker
import com.cylonid.nativealpha.adapter.SandboxChoiceAdapter
import com.cylonid.nativealpha.util.AppUpdateChecker
import com.cylonid.nativealpha.util.ColorPalette
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.EntryPointUtils.entryPointReached
import com.cylonid.nativealpha.util.FaviconFetcher
import com.cylonid.nativealpha.util.SearchEngineUrl
import com.cylonid.nativealpha.util.WebViewLauncher
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import io.github.edsuns.adfilter.AdFilter
import java.net.URI

class MainActivity : AppCompatActivity() {
    private lateinit var webAppListFragment: WebAppListFragment
    private lateinit var homeDrawerLayout: DrawerLayout
    private lateinit var drawerBackCallback: OnBackPressedCallback
    private var addWebsiteBinding: AddWebsiteDialogueBinding? = null
    private var addWebsiteTitle: String? = null
    private var selectedContainerId = Const.NO_CONTAINER

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        homeDrawerLayout = findViewById(R.id.homeDrawerLayout)
        setUpDrawerBackHandling()
        constrainNavigationWidth()
        webAppListFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container_view) as WebAppListFragment
        entryPointReached(this)

        AppUpdateChecker.checkSilentlyOnStartup(this)

        if (savedInstanceState?.containsKey(STATE_ADD_TITLE) == true) {
            buildAddWebsiteDialog(
                savedInstanceState.getString(STATE_ADD_TITLE).orEmpty(),
                savedInstanceState.getString(STATE_ADD_URL).orEmpty(),
                savedInstanceState.getInt(STATE_ADD_CONTAINER, Const.NO_CONTAINER),
                savedInstanceState.getBoolean(STATE_ADD_SHORTCUT, true)
            )
        } else if (!handleViewIntent(intent) && DataManager.getInstance().websites.size == 0) {
            buildAddWebsiteDialog(getString(R.string.welcome_msg))
        }

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab)
        fab.setOnClickListener { buildAddWebsiteDialog(getString(R.string.add_webapp)) }

        personalizeToolbar()
        setUpTopBar()
        setUpSearch()

        AdblockLifecycleHelper(this).trySyncOperation({ AdFilter.create(applicationContext) })
        clearSearchFocus()
    }

    override fun onResume() {
        super.onResume()
        clearSearchFocus()
        DataManager.getInstance().loadAppData()
        WebMonitorWorker.syncPeriodicWork(this)
        updateWebAppList()
    }

    override fun onPause() {
        super.onPause()
        clearSearchFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        clearSearchFocus()
        setIntent(intent)
        handleViewIntent(intent)
        if (intent.getBooleanExtra(Const.INTENT_BACKUP_RESTORED, false)) {
            updateWebAppList()
            buildImportSuccessDialog()
            intent.putExtra(Const.INTENT_BACKUP_RESTORED, false)
            intent.putExtra(Const.INTENT_REFRESH_NEW_THEME, false)
        }
        if (intent.getBooleanExtra(Const.INTENT_WEBAPP_CHANGED, false)) {
            updateWebAppList()
            intent.putExtra(Const.INTENT_WEBAPP_CHANGED, false)
        }
    }

    private fun handleViewIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val uri: Uri = intent.data ?: return false
        if (!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) return false
        val url = normalizedWebsiteUrl(uri.toString()) ?: return false
        intent.data = null
        openOrCreateUrl(url)
        return true
    }

    private fun openOrCreateUrl(url: String) {
        clearSearchFocus()
        DataManager.getInstance().loadAppData()
        val matches = matchingWebApps(url, DataManager.getInstance().activeWebsites)
        when (matches.size) {
            0 -> buildAddWebsiteDialog(getString(R.string.add_webapp), url)
            1 -> openExistingWebApp(matches.single(), url)
            else -> AlertDialog.Builder(this)
                .setTitle(R.string.entry_choose_existing_app)
                .setItems(matches.map {
                    getString(R.string.entry_app_sandbox, it.title, sandboxLabel(it.containerId))
                }.toTypedArray()) { _, index -> openExistingWebApp(matches[index], url) }
                .setNeutralButton(R.string.entry_create_another) { _, _ ->
                    buildAddWebsiteDialog(getString(R.string.add_webapp), url)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun openExistingWebApp(webApp: WebApp, url: String) {
        clearSearchFocus()
        val launchIntent = WebViewLauncher.createWebViewIntent(webApp, this) ?: return
        launchIntent.putExtra(WebViewLauncher.EXTRA_TARGET_URL, url)
        startActivity(launchIntent)
    }

    private fun sandboxLabel(containerId: Int): String {
        return if (SandboxManager.isValidContainerId(containerId)) {
            getString(
                R.string.entry_sandbox_named,
                SandboxPreferences.getName(containerId),
                containerId + 1
            )
        } else {
            getString(R.string.entry_sandbox_default, SandboxPreferences.getName(Const.NO_CONTAINER))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        addWebsiteBinding?.let {
            outState.putString(STATE_ADD_TITLE, addWebsiteTitle)
            outState.putString(STATE_ADD_URL, it.websiteUrl.text.toString())
            outState.putInt(STATE_ADD_CONTAINER, selectedContainerId)
            outState.putBoolean(STATE_ADD_SHORTCUT, it.switchCreateShortcut.isChecked)
        }
        super.onSaveInstanceState(outState)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun updateWebAppList() {
        webAppListFragment.updateWebAppList()
    }

    private fun personalizeToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        @StringRes val appName =
            if (BuildConfig.FLAVOR == "extended") R.string.app_name_plus else R.string.app_name
        toolbar.setTitle(appName)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setUpTopBar() {
        findViewById<ImageButton>(R.id.btnMainDrawer)?.setOnClickListener {
            openHomeNavigation()
        }

        findViewById<ImageButton>(R.id.btnGridColumnsToggle)?.setOnClickListener {
            webAppListFragment.toggleHomeViewMode()
        }

        findViewById<ImageButton>(R.id.btnMainSettings)?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.navLiteApps)?.setOnClickListener {
            closeHomeNavigation()
        }
        findViewById<View>(R.id.navSandboxes)?.setOnClickListener {
            openActivityFromNavigation(
                Intent().setClassName(this, SANDBOX_ACTIVITY)
            )
        }
        findViewById<View>(R.id.navGlobalSettings)?.setOnClickListener {
            openActivityFromNavigation(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.navAbout)?.setOnClickListener {
            openActivityFromNavigation(Intent(this, AboutActivity::class.java))
        }
    }

    private fun constrainNavigationWidth() {
        if (resources.configuration.screenWidthDp >= 480) return
        val availableWidth = resources.displayMetrics.widthPixels
        val maxDrawerWidth = resources.getDimensionPixelSize(R.dimen.navigation_drawer_max_width)
        val width = minOf(maxDrawerWidth, (availableWidth * 0.84f).toInt())
        findViewById<View>(R.id.homeNavigationDrawer).updateLayoutParams<ViewGroup.LayoutParams> {
            this.width = width
        }
    }

    private fun setUpDrawerBackHandling() {
        drawerBackCallback = object : OnBackPressedCallback(homeDrawerLayout.isDrawerOpen(GravityCompat.START) || homeDrawerLayout.isDrawerVisible(GravityCompat.START)) {
            override fun handleOnBackPressed() {
                if (homeDrawerLayout.isDrawerOpen(GravityCompat.START) || homeDrawerLayout.isDrawerVisible(GravityCompat.START)) {
                    closeHomeNavigation()
                } else {
                    isEnabled = false
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)

        homeDrawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawerBackCallback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerBackCallback.isEnabled = false
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                drawerBackCallback.isEnabled = slideOffset > 0f
            }

            override fun onDrawerStateChanged(newState: Int) {
                drawerBackCallback.isEnabled = homeDrawerLayout.isDrawerOpen(GravityCompat.START) || homeDrawerLayout.isDrawerVisible(GravityCompat.START)
            }
        })
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (::drawerBackCallback.isInitialized) {
            drawerBackCallback.isEnabled = homeDrawerLayout.isDrawerOpen(GravityCompat.START) || homeDrawerLayout.isDrawerVisible(GravityCompat.START)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun openHomeNavigation() {
        clearSearchFocus()
        homeDrawerLayout.openDrawer(GravityCompat.START, false)
        if (::drawerBackCallback.isInitialized) {
            drawerBackCallback.isEnabled = true
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun closeHomeNavigation() {
        homeDrawerLayout.closeDrawer(GravityCompat.START, false)
        if (::drawerBackCallback.isInitialized) {
            drawerBackCallback.isEnabled = false
        }
    }

    private fun openActivityFromNavigation(intent: Intent) {
        closeHomeNavigation()
        clearSearchFocus()
        startActivity(intent)
    }

    companion object {
        @VisibleForTesting
        const val SANDBOX_ACTIVITY = "com.cylonid.nativealpha.SandboxActivity"
        private const val STATE_ADD_TITLE = "entry.add.title"
        private const val STATE_ADD_URL = "entry.add.url"
        private const val STATE_ADD_CONTAINER = "entry.add.container"
        private const val STATE_ADD_SHORTCUT = "entry.add.shortcut"

        @JvmStatic
        @VisibleForTesting
        fun normalizedWebsiteUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return null
            val url = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            return try {
                val uri = URI(url)
                if ((!uri.scheme.equals("http", true) && !uri.scheme.equals("https", true)) ||
                    uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
                    uri.port < -1 || uri.port > 65535
                ) null else uri.toASCIIString()
            } catch (_: Exception) {
                null
            }
        }

        @JvmStatic
        @VisibleForTesting
        fun matchingWebApps(url: String, apps: List<WebApp>): List<WebApp> {
            val target = normalizedWebsiteUrl(url)?.let { URI(it) } ?: return emptyList()
            fun port(uri: URI) = if (uri.port >= 0) uri.port else
                if (uri.scheme.equals("https", true)) 443 else 80
            return apps.filter { app ->
                val base = normalizedWebsiteUrl(app.baseUrl)?.let { URI(it) }
                app.isActiveEntry && base != null &&
                    base.scheme.equals(target.scheme, true) &&
                    base.host.equals(target.host, true) && port(base) == port(target)
            }
        }
    }

    @Deprecated("Drawer back handling is managed by OnBackPressedCallback; retained for legacy callers")
    override fun onBackPressed() {
        if (homeDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeHomeNavigation()
        } else {
            super.onBackPressed()
        }
    }

    private fun setUpSearch() {
        val search = findViewById<EditText>(R.id.searchWebApps)
        val clearSearch = findViewById<ImageButton>(R.id.btnClearSearch)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                webAppListFragment.filter(s?.toString().orEmpty())
                clearSearch.isVisible = !s.isNullOrBlank()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        clearSearch.setOnClickListener {
            search.text.clear()
            search.requestFocus()
        }

        search.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val input = search.text.toString().trim()
                if (input.isNotEmpty()) {
                    val targetUrl = if (input.startsWith("http://") || input.startsWith("https://")) {
                        input
                    } else if (input.contains(".") && !input.contains(" ")) {
                        "https://$input"
                    } else {
                        SearchEngineUrl.buildSearchUrl(
                            DataManager.getInstance().settings.searchEngineUrl,
                            input
                        )
                    }
                    clearSearchFocus()
                    openOrCreateUrl(targetUrl)
                    true
                } else false
            } else false
        }

        clearSearch.isVisible = search.text.isNotBlank()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        if (id == R.id.action_about) {
            startActivity(Intent(this, AboutActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun buildImportSuccessDialog() {
        val message = """
            ${getString(R.string.import_success_dialog_txt2)}

            ${getString(R.string.import_success_dialog_txt3)}

            ${getString(R.string.entry_backup_scope)}
            """.trimIndent()

        AlertDialog.Builder(this).setMessage(message)
            .setCancelable(false)
            .setTitle(
                getString(
                    R.string.import_success,
                    DataManager.getInstance().activeWebsitesCount
                )
            )
            .setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                val webapps = DataManager.getInstance().activeWebsites
                for (i in webapps.indices.reversed()) {
                    val webapp = webapps[i]
                    val msg = Html.fromHtml(
                        getString(R.string.restore_shortcut, webapp.title),
                        Html.FROM_HTML_MODE_COMPACT
                    )
                    AlertDialog.Builder(this)
                        .setMessage(msg)
                        .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                            val frag = ShortcutDialogFragment.newInstance(webapp)
                            frag.show(supportFragmentManager, "SCFetcher-" + webapp.ID)
                        }
                        .setNegativeButton(R.string.cancel) { _: DialogInterface?, _: Int -> }
                        .create()
                        .show()
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
            .create().show()
    }

    private fun buildAddWebsiteDialog(
        title: String,
        initialUrl: String = "",
        initialContainerId: Int = Const.NO_CONTAINER,
        createShortcut: Boolean = true
    ) {
        clearSearchFocus()
        val localBinding = AddWebsiteDialogueBinding.inflate(layoutInflater)
        addWebsiteBinding = localBinding
        addWebsiteTitle = title
        selectedContainerId = if (SandboxManager.isValidContainerId(initialContainerId)) {
            initialContainerId
        } else Const.NO_CONTAINER
        localBinding.websiteUrl.setText(initialUrl)
        localBinding.switchCreateShortcut.isChecked = createShortcut
        fun updateSandboxSelection() {
            localBinding.selectSandbox.text =
                getString(R.string.entry_selected_sandbox, sandboxLabel(selectedContainerId))
        }
        updateSandboxSelection()
        localBinding.selectSandbox.setOnClickListener {
            val ids = listOf(Const.NO_CONTAINER) + (0 until SandboxManager.getSandboxCount())
            val items = ids.map { id ->
                val name = sandboxLabel(id)
                val color = ColorPalette.colorForContainer(id)
                SandboxChoiceAdapter.Item(id, name, color)
            }
            val adapter = SandboxChoiceAdapter(this, items, selectedContainerId)
            var picker: AlertDialog? = null
            picker = AlertDialog.Builder(this)
                .setTitle(R.string.entry_choose_sandbox)
                .setAdapter(adapter) { _, index ->
                    selectedContainerId = ids[index]
                    updateSandboxSelection()
                    picker?.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        data class PresetLiteApp(val name: String, val url: String, val themeColor: String)
        val presets = listOf(
            PresetLiteApp("YouTube", "https://m.youtube.com", "Red"),
            PresetLiteApp("X", "https://x.com", "Black"),
            PresetLiteApp("Reddit", "https://www.reddit.com", "Orange"),
            PresetLiteApp("Wikipedia", "https://m.wikipedia.org", "Blue"),
            PresetLiteApp("GitHub", "https://github.com", "Dark Grey"),
            PresetLiteApp("Google", "https://www.google.com", "Blue")
        )
        val density = resources.displayMetrics.density
        presets.forEach { preset ->
            val button = com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = preset.name
                cornerRadius = (16 * density).toInt()
                minHeight = (36 * density).toInt()
                textSize = 12f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
                layoutParams = params
                setOnClickListener {
                    localBinding.websiteUrl.setText(preset.url)
                    localBinding.websiteUrl.setSelection(preset.url.length)
                }
            }
            localBinding.popularAppsContainer.addView(button)
        }

        val dialog = AlertDialog.Builder(this@MainActivity)
            .setView(localBinding.root)
            .setTitle(title)
            .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                val urlWithProtocol = normalizedWebsiteUrl(localBinding.websiteUrl.text.toString())
                    ?: return@setPositiveButton
                DataManager.getInstance().loadAppData()
                val newSite = WebApp(
                    urlWithProtocol,
                    DataManager.getInstance().incrementedID,
                    DataManager.getInstance().incrementedOrder
                )
                newSite.applySettingsForNewWebApp()
                newSite.containerId = selectedContainerId
                newSite.isUseContainer = SandboxManager.isValidContainerId(selectedContainerId)
                val matchedPreset = presets.firstOrNull { preset ->
                    urlWithProtocol.contains(preset.url.replace("https://", "").replace("m.", "").replace("www.", ""))
                }
                if (matchedPreset != null) {
                    newSite.themeColor = matchedPreset.themeColor
                }
                DataManager.getInstance().addWebsite(newSite)

                updateWebAppList()

                // Automatic background icon and title fetch
                FaviconFetcher.fetchAndSaveFaviconAsync(applicationContext, newSite) {
                    updateWebAppList()
                }

                if (localBinding.switchCreateShortcut.isChecked) {
                    val frag = ShortcutDialogFragment.newInstance(newSite)
                    frag.show(supportFragmentManager, "SCFetcher-" + newSite.ID)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnDismissListener {
            if (addWebsiteBinding === localBinding) {
                addWebsiteBinding = null
                addWebsiteTitle = null
            }
        }
        dialog.show()
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.isEnabled = normalizedWebsiteUrl(initialUrl) != null
        localBinding.websiteUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                okButton.isEnabled = normalizedWebsiteUrl(s?.toString().orEmpty()) != null
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    @JvmOverloads
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun clearSearchFocus(hideKeyboard: Boolean = true) {
        val search = findViewById<EditText>(R.id.searchWebApps)
        search?.clearFocus()
        val targetRoot = findViewById<View>(R.id.homeMainContent) ?: findViewById<View>(R.id.homeDrawerLayout)
        targetRoot?.requestFocus()
        if (hideKeyboard) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val token = search?.windowToken ?: currentFocus?.windowToken ?: window?.decorView?.windowToken
            if (token != null) {
                imm?.hideSoftInputFromWindow(token, 0)
            }
        }
    }

}
