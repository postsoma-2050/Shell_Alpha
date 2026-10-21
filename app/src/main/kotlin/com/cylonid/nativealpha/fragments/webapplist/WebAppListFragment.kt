package com.cylonid.nativealpha.fragments.webapplist

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.ShortcutIconUtils
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.ernestoyaquello.dragdropswiperecyclerview.listener.OnItemDragListener
import com.google.android.material.snackbar.Snackbar

class WebAppListFragment : Fragment(R.layout.fragment_web_app_list) {
    private lateinit var adapter: WebAppListAdapter
    private lateinit var list: DragDropSwipeRecyclerView
    private lateinit var emptyState: View
    private lateinit var emptyStateTitle: TextView
    private lateinit var emptyStateMessage: TextView
    private lateinit var emptyStateAction: View
    private var currentQuery: String = ""
    private lateinit var homePreferences: SharedPreferences
    private var homeViewMode = HomeViewMode.GRID

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WebAppListAdapter(
            DataManager.getInstance().activeWebsites,
            requiredActivity(),
            ::handleDeleteRequest
        )

        list = view.findViewById(R.id.web_app_list)
        emptyState = view.findViewById(R.id.emptyState)
        emptyStateTitle = view.findViewById(R.id.emptyStateTitle)
        emptyStateMessage = view.findViewById(R.id.emptyStateMessage)
        emptyStateAction = view.findViewById(R.id.emptyStateAction)
        emptyStateAction.setOnClickListener { clearSearch() }
        homePreferences =
            requiredActivity().getSharedPreferences(HOME_PREFERENCES, android.content.Context.MODE_PRIVATE)
        homeViewMode = savedHomeViewMode()
        list.dividerDrawableId = null
        list.adapter = adapter
        list.longPressToStartDragging = false
        list.orientation =
            DragDropSwipeRecyclerView.ListOrientation.GRID_LIST_WITH_HORIZONTAL_SWIPING
        list.disableSwipeDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.LEFT)
        list.disableSwipeDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.RIGHT)
        list.disableDragDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.UP)
        list.disableDragDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.DOWN)
        list.disableDragDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.LEFT)
        list.disableDragDirection(DragDropSwipeRecyclerView.ListOrientation.DirectionFlag.RIGHT)
        list.dragListener = onItemDragListener
        applyHomeViewMode(homeViewMode)
        updateEmptyState()
        updateFabClearance()

        val search = requiredActivity().findViewById<EditText>(R.id.searchWebApps)
        currentQuery = search.text?.toString().orEmpty()
        adapter.filter(currentQuery)
        updateEmptyState()
    }

    fun updateWebAppList() {
        adapter.updateWebAppList()
        updateEmptyState()
        updateHomeViewModeAction()
    }

    fun filter(query: String) {
        currentQuery = query
        adapter.filter(query)
        updateEmptyState()
    }

    fun toggleHomeViewMode() {
        val next = if (homeViewMode == HomeViewMode.GRID) {
            HomeViewMode.LIST
        } else {
            HomeViewMode.GRID
        }
        homePreferences.edit().putInt(KEY_GRID_SPAN_COUNT, next.spanCount).apply()
        applyHomeViewMode(next)
        val message = if (next == HomeViewMode.GRID) {
            R.string.home_mode_grid_active
        } else {
            R.string.home_mode_list_active
        }
        Snackbar.make(list, getString(message), Snackbar.LENGTH_SHORT).show()
    }

    private fun savedHomeViewMode(): HomeViewMode {
        if (!homePreferences.contains(KEY_GRID_SPAN_COUNT)) {
            return HomeViewMode.GRID
        }
        val savedSpanCount = homePreferences.getInt(KEY_GRID_SPAN_COUNT, HomeViewMode.GRID.spanCount)
        val mode = HomeViewMode.fromSavedSpanCount(savedSpanCount)
        if (savedSpanCount != mode.spanCount) {
            homePreferences.edit().putInt(KEY_GRID_SPAN_COUNT, mode.spanCount).apply()
        }
        return mode
    }

    private fun updateHomeViewModeAction() {
        val next = if (homeViewMode == HomeViewMode.GRID) HomeViewMode.LIST else HomeViewMode.GRID
        requireActivity().findViewById<ImageButton>(R.id.btnGridColumnsToggle)?.apply {
            val actionText = getString(
                if (next == HomeViewMode.GRID) {
                    R.string.home_mode_switch_to_grid
                } else {
                    R.string.home_mode_switch_to_list
                }
            )
            setImageResource(
                if (next == HomeViewMode.GRID) {
                    R.drawable.ic_grid_view_24
                } else {
                    R.drawable.ic_view_list_24
                }
            )
            contentDescription = actionText
            tooltipText = actionText
        }
    }

    private fun applyHomeViewMode(mode: HomeViewMode) {
        homeViewMode = mode
        adapter.isListMode = mode == HomeViewMode.LIST

        val firstVisiblePosition = (list.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()
            ?.coerceAtLeast(0)
            ?: 0
        val layoutManager: RecyclerView.LayoutManager
        if (mode == HomeViewMode.LIST) {
            list.orientation =
                DragDropSwipeRecyclerView.ListOrientation.VERTICAL_LIST_WITH_VERTICAL_DRAGGING
            layoutManager = LinearLayoutManager(requiredActivity())
        } else {
            list.orientation =
                DragDropSwipeRecyclerView.ListOrientation.GRID_LIST_WITH_HORIZONTAL_SWIPING
            layoutManager = GridLayoutManager(requiredActivity(), HomeViewMode.GRID.spanCount)
        }
        list.layoutManager = layoutManager
        layoutManager.scrollToPosition(firstVisiblePosition)
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        updateHomeViewModeAction()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        updateHomeViewModeAction()
    }

    private fun clearSearch() {
        requiredActivity().findViewById<EditText>(R.id.searchWebApps).text.clear()
    }

    private fun handleDeleteRequest(webapp: WebApp) {
        AlertDialog.Builder(requiredActivity())
            .setTitle(R.string.delete_lite_app)
            .setMessage(getString(R.string.home_delete_confirmation, webapp.title))
            .setPositiveButton(R.string.delete_lite_app) { _, _ ->
                val currentWebApp = DataManager.getInstance().websites
                    .firstOrNull { it.ID == webapp.ID }
                    ?: return@setPositiveButton
                currentWebApp.markInactive()
                ShortcutIconUtils.deleteShortcuts(
                    listOf(currentWebApp.ID),
                    requiredActivity()
                )
                DataManager.getInstance().saveWebAppData()
                updateWebAppList()

                Snackbar.make(
                    list,
                    getString(R.string.x_was_removed, currentWebApp.title),
                    Snackbar.LENGTH_LONG
                ).setAction(getString(R.string.undo)) {
                    DataManager.getInstance().websites
                        .firstOrNull { it.ID == currentWebApp.ID }
                        ?.isActiveEntry = true
                    DataManager.getInstance().saveWebAppData()
                    updateWebAppList()
                }.show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateFabClearance() {
        val fab = requiredActivity().findViewById<View>(R.id.fab) ?: return
        fun applyClearance() {
            val margin = (FAB_MARGIN_DP * resources.displayMetrics.density).toInt()
            val clearance = maxOf(
                (DEFAULT_LIST_BOTTOM_INSET_DP * resources.displayMetrics.density).toInt(),
                fab.height + margin + margin / 2
            )
            list.setPadding(
                list.paddingStart,
                list.paddingTop,
                list.paddingEnd,
                clearance
            )
            emptyState.setPadding(
                emptyState.paddingStart,
                emptyState.paddingTop,
                emptyState.paddingEnd,
                clearance
            )
        }
        applyClearance()
        fab.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyClearance() }
    }

    private fun updateEmptyState() {
        val hasResults = adapter.itemCount > 0
        emptyState.visibility = if (hasResults) View.GONE else View.VISIBLE
        if (hasResults) return

        val hasApps = DataManager.getInstance().activeWebsites.isNotEmpty()
        if (currentQuery.isNotBlank() && hasApps) {
            emptyStateTitle.setText(R.string.home_no_search_results_title)
            emptyStateMessage.setText(R.string.home_no_search_results_message)
            emptyStateAction.visibility = View.VISIBLE
        } else {
            emptyStateTitle.setText(R.string.home_no_apps_title)
            emptyStateMessage.setText(R.string.home_no_apps_message)
            emptyStateAction.visibility = View.GONE
        }
    }

    private fun requiredActivity(): FragmentActivity {
        return requireNotNull(activity) { "WebAppListFragment is not attached to an activity." }
    }

    private val onItemDragListener = object : OnItemDragListener<WebApp> {
        override fun onItemDropped(initialPosition: Int, finalPosition: Int, item: WebApp) {
            saveCurrentDisplayedOrderOfWebAppsToDisk()
        }

        override fun onItemDragged(previousPosition: Int, newPosition: Int, item: WebApp) {
        }
    }

    private fun saveCurrentDisplayedOrderOfWebAppsToDisk() {
        for ((i, webapp) in adapter.dataSet.withIndex()) {
            webapp.order = i
        }
        DataManager.getInstance().saveWebAppData()
    }

    companion object {
        private const val HOME_PREFERENCES = "home_refinement"
        private const val KEY_GRID_SPAN_COUNT = "grid_span_count"
        private const val DEFAULT_LIST_BOTTOM_INSET_DP = 96
        private const val FAB_MARGIN_DP = 20

        fun newInstance() = WebAppListFragment()
    }
}

private enum class HomeViewMode(val spanCount: Int) {
    GRID(4),
    LIST(1);

    companion object {
        fun fromSavedSpanCount(spanCount: Int): HomeViewMode {
            return if (spanCount == LIST.spanCount) LIST else GRID
        }
    }
}
