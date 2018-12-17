/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package mozilla.lockbox.view

import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.view.ContextThemeWrapper
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.jakewharton.rxbinding2.support.design.widget.itemSelections
import com.jakewharton.rxbinding2.support.v7.widget.itemClicks
import com.jakewharton.rxbinding2.support.v7.widget.navigationClicks
import com.jakewharton.rxbinding2.view.clicks
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import jp.wasabeef.picasso.transformations.CropCircleTransformation
import kotlinx.android.synthetic.main.fragment_item_list.view.*
import mozilla.lockbox.R
import mozilla.lockbox.adapter.ItemListAdapter
import mozilla.lockbox.model.ItemViewModel
import mozilla.lockbox.presenter.ItemListPresenter
import mozilla.lockbox.presenter.ItemListView
import kotlinx.android.synthetic.main.fragment_item_list.*
import kotlinx.android.synthetic.main.nav_header.view.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mozilla.lockbox.action.Setting
import mozilla.lockbox.model.AccountViewModel

@ExperimentalCoroutinesApi
class ItemListFragment : CommonFragment(), ItemListView {
    private val compositeDisposable = CompositeDisposable()
    private val adapter = ItemListAdapter()

    private lateinit var sortItemsMenu: PopupMenu
//    private lateinit var sortItemsAdapter: ItemListSortAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        presenter = ItemListPresenter(this)
        val view = inflater.inflate(R.layout.fragment_item_list, container, false)
        val navController = requireActivity().findNavController(R.id.fragment_nav_host)

        setupToolbar(view.toolbar, view.appDrawer)
        setupNavigationView(navController, view.navView)
        setupListView(view.entriesView)

        val context = ContextThemeWrapper(requireContext(), R.style.CustomPopupTheme)
        sortItemsMenu = PopupMenu(context, view.sortButton)
        sortItemsMenu.menuInflater.inflate(R.menu.sort_menu, sortItemsMenu.menu)
        sortItemsMenu.gravity = Gravity.END

        view.sortButton.clicks()
            .subscribe {
                sortItemsMenu.show()
            }
            .addTo(compositeDisposable)

        return view
    }

    private fun setupNavigationView(navController: NavController, navView: NavigationView) {
        navView.setupWithNavController(navController)
    }

    private fun setupListView(listView: RecyclerView) {
        val context = requireContext()
        val layoutManager = LinearLayoutManager(context)
        val decoration = DividerItemDecoration(context, layoutManager.orientation)
        context.getDrawable(R.drawable.inset_divider)?.let {
            decoration.setDrawable(it)
            listView.addItemDecoration(decoration)
        }
        listView.layoutManager = layoutManager
        listView.adapter = adapter
    }

    private fun setupToolbar(toolbar: Toolbar, drawerLayout: DrawerLayout) {
        toolbar.navigationIcon = resources.getDrawable(R.drawable.ic_menu, null)
        toolbar.setNavigationContentDescription(R.string.menu_description)
        toolbar.navigationClicks().subscribe { drawerLayout.openDrawer(GravityCompat.START) }
            .addTo(compositeDisposable)
    }

//    private fun setupItemListSortMenu(sortButton: Button) {
//        val context = requireContext()
//        sortItemsMenu = ListPopupWindow(context)
//        sortItemsAdapter = ItemListSortAdapter(
//            context,
//            R.layout.sort_menu_item,
//            sortMenuOptions.map { context.getString(it.displayStringId) }.toTypedArray()
//        )
//        sortItemsAdapter.selectedBackgroundColor = R.color.menuItemSelected
//        sortItemsMenu.setAdapter(sortItemsAdapter)
//        sortItemsMenu.anchorView = sortButton
//        sortItemsMenu.isModal = true
//        sortItemsMenu.width = dpToPixels(context, 170f)
//        sortItemsMenu.horizontalOffset = dpToPixels(context, 42f)
//        context.getDrawable(R.drawable.sort_menu_bg)?.let { sortItemsMenu.setBackgroundDrawable(it) }
//        sortItemsMenu.animationStyle = R.style.SortItemsPopupAnimation
//    }

    private fun scrollToTop() {
        entriesView.layoutManager?.scrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
    }

    // Protocol implementations
    override val filterClicks: Observable<Unit>
        get() = view!!.filterButton.clicks()

    override val itemSelection: Observable<ItemViewModel>
        get() = adapter.clicks()

    override val menuItemSelections: Observable<Int>
        get() {
            val navView = view!!.navView
            val drawerLayout = view!!.appDrawer
            return navView.itemSelections()
                .doOnNext {
                    drawerLayout.closeDrawer(navView)
                }
                .map { it.itemId }
        }

    override val lockNowClick: Observable<Unit>
        get() = view!!.lockNow.clicks()

    override val sortItemSelection: Observable<Setting.ItemListSort>
        get() = sortItemsMenu.itemClicks().map { getSortMenuOption(it.title.toString()) }

    private fun getSortMenuOption(title: String): Setting.ItemListSort =
        Setting.ItemListSort.values().first { itemListSort -> requireContext().getString(itemListSort.displayStringId) == title }

    override fun updateItems(itemList: List<ItemViewModel>) {
        adapter.updateItems(itemList)
    }

    override fun updateAccountProfile(profile: AccountViewModel) {

        navView.menuHeader.displayName.text = profile.displayEmailName ?: resources.getString(R.string.firefox_account)
        view!!.navView.menuHeader.accountName.text = profile.accountName ?: resources.getString(R.string.app_name)

        var avatarUrl = profile.avatarFromURL
        if (avatarUrl.isNullOrEmpty() || avatarUrl == resources.getString(R.string.default_avatar_url)) {
            avatarUrl = null
        }

        Picasso.get()
            .load(avatarUrl)
            .placeholder(R.drawable.ic_default_avatar)
            .transform(CropCircleTransformation())
            .resizeDimen(R.dimen.avatar_image_size, R.dimen.avatar_image_size)
            .centerCrop()
            .into(view!!.profileImage)
    }

    override fun updateItemListSort(sort: Setting.ItemListSort) {
        // select the menu item
        sortItemsMenu.menu.getItem(Setting.ItemListSort.values().indexOf(sort)).isChecked = true

        // set sort button text
        when (sort) {
            Setting.ItemListSort.ALPHABETICALLY -> view!!.sortButton.setText(R.string.all_entries_a_z)
            Setting.ItemListSort.RECENTLY_USED -> view!!.sortButton.setText(R.string.all_entries_recent)
        }
        // scroll list view to top
        scrollToTop()
    }
}
