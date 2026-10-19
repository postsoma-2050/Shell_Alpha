package com.cylonid.nativealpha

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
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
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
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.monitors.WebMonitorWorker
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.EntryPointUtils.entryPointReached
import com.cylonid.nativealpha.util.FaviconFetcher
import com.cylonid.nativealpha.util.SearchEngineUrl
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import io.github.edsuns.adfilter.AdFilter

class MainActivity : AppCompatActivity() {
    private lateinit var webAppListFragment: WebAppListFragment
    private lateinit var homeDrawerLayout: DrawerLayout
    private lateinit var drawerBackCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        homeDrawerLayout = findViewById(R.id.homeDrawerLayout)
        setUpDrawerBackHandling()
        constrainNavigationWidth()
        webAppListFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container_view) as WebAppListFragment
        entryPointReached(this)

        if (DataManager.getInstance().websites.size == 0) {
            buildAddWebsiteDialog(getString(R.string.welcome_msg))
        }
        handleViewIntent(intent)

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fab)
        fab.setOnClickListener { buildAddWebsiteDialog(getString(R.string.add_webapp)) }

        personalizeToolbar()
        setUpTopBar()
        setUpSearch()

        AdblockLifecycleHelper(this).trySyncOperation({ AdFilter.create(applicationContext) })
    }

    override fun onResume() {
        super.onResume()
        DataManager.getInstance().loadAppData()
        WebMonitorWorker.syncPeriodicWork(this)
        updateWebAppList()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return
        intent.data = null
        buildAddWebsiteDialog(getString(R.string.add_webapp), uri.toString())
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
        startActivity(intent)
    }

    companion object {
        @VisibleForTesting
        const val SANDBOX_ACTIVITY = "com.cylonid.nativealpha.SandboxActivity"
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
                    buildAddWebsiteDialog(getString(R.string.open_or_create_url), targetUrl)
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

    private fun buildAddWebsiteDialog(title: String, initialUrl: String = "") {
        val localBinding = AddWebsiteDialogueBinding.inflate(layoutInflater)
        localBinding.websiteUrl.setText(initialUrl)
        val dialog = AlertDialog.Builder(this@MainActivity)
            .setView(localBinding.root)
            .setTitle(title)
            .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                val url = localBinding.websiteUrl.text.toString().trim()
                val urlWithProtocol =
                    if (url.startsWith("https://") || url.startsWith("http://")) url else "https://$url"
                val newSite = WebApp(
                    urlWithProtocol,
                    DataManager.getInstance().incrementedID,
                    DataManager.getInstance().incrementedOrder
                )
                newSite.applySettingsForNewWebApp()
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

        dialog.show()
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.isEnabled = initialUrl.isNotBlank()
        localBinding.websiteUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                okButton.isEnabled = !s.isNullOrBlank()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

}
