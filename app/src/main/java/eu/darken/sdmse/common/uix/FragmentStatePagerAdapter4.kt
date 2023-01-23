package eu.darken.sdmse.common.uix

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.collection.SparseArrayCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.viewpager.widget.PagerAdapter

abstract class FragmentStatePagerAdapter4<T> @JvmOverloads constructor(
    private val activity: FragmentActivity,
    private val fragmentManager: FragmentManager,
    private val enableSavedStates: Boolean = true
) : PagerAdapter() {

    private var currentTransaction: FragmentTransaction? = null
    private var savedStateArray = SparseArrayCompat<Fragment.SavedState?>()
    private var fragmentArray = SparseArrayCompat<Fragment>()

    var currentFragment: Fragment? = null
        private set

    private val internalData = mutableListOf<T>()

    val data: List<T>
        get() = internalData

    fun setData(newData: List<T>?) {
        internalData.clear()
        if (newData != null) internalData.addAll(newData)
    }

    abstract fun onCreateFragment(item: T): Fragment

    override fun getCount(): Int = internalData.size

    override fun startUpdate(container: ViewGroup) {}

    @SuppressLint("CommitTransaction") override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val existing = fragmentArray[position]
        if (existing != null) return existing
        if (currentTransaction == null) currentTransaction = fragmentManager.beginTransaction()
        val fragment = onCreateFragment(internalData[position])
        if (enableSavedStates) {
            val fss = savedStateArray[position]
            if (fss != null) fragment.setInitialSavedState(fss)
        }
        if (fragment !== currentFragment) setItemVisible(fragment, false)
        fragmentArray.put(position, fragment)
        currentTransaction!!.add(container.id, fragment)
        return fragment
    }

    @SuppressLint("CommitTransaction") override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val fragment = obj as Fragment
        if (currentTransaction == null) currentTransaction = fragmentManager.beginTransaction()
        val actualPosition = getItemPosition(fragment)
        if (enableSavedStates && actualPosition >= 0) {
            savedStateArray.put(actualPosition, fragmentManager.saveFragmentInstanceState(fragment))
        }
        fragmentArray.delete(position)
        currentTransaction!!.remove(fragment)
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, obj: Any) {
        val fragment = obj as Fragment
        if (fragment != currentFragment) {
            for (i in 0 until fragmentArray.size()) {
                val pos = fragmentArray.keyAt(i)
                fragmentArray[pos]?.let {
                    setItemVisible(it, false)
                }
            }
            setItemVisible(fragment, true)
            currentFragment = fragment
            activity.invalidateOptionsMenu()
        }
    }

    override fun finishUpdate(container: ViewGroup) {
        currentTransaction?.commitNowAllowingStateLoss()
        currentTransaction = null
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return (obj as Fragment).view == view
    }

    override fun saveState(): Parcelable? {
        var state: Bundle? = null
        if (enableSavedStates) {
            state = Bundle()
            for (i in 0 until savedStateArray.size()) {
                val pos = savedStateArray.keyAt(i)
                val savedState = savedStateArray.valueAt(i)
                val key = "s$pos"
                state.putParcelable(key, savedState)
            }
        }
        for (i in 0 until fragmentArray.size()) {
            if (state == null) state = Bundle()
            val pos = fragmentArray.keyAt(i)
            val f = fragmentArray.valueAt(i)
            val key = "f$pos"
            fragmentManager.putFragment(state, key, f)
        }
        return state
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        if (state == null) return

        val bundle = state as Bundle
        bundle.classLoader = loader
        fragmentArray.clear()
        savedStateArray.clear()
        val keys: Iterable<String> = bundle.keySet()
        for (key in keys) {
            if (key.startsWith("f")) {
                val index = key.substring(1).toInt()
                val f = fragmentManager.getFragment(bundle, key)
                if (f != null) {
                    setItemVisible(f, false)
                    fragmentArray.put(index, f)
                }
            } else if (enableSavedStates && key.startsWith("s")) {
                val index = key.substring(1).toInt()
                val savedState = bundle.getParcelable<Fragment.SavedState>(key)
                if (savedState != null) savedStateArray.put(index, savedState)
            }
        }
    }

    fun setItemVisible(item: Fragment, visible: Boolean) {
        item.setHasOptionsMenu(visible)
        item.setMenuVisibility(visible)
        item.userVisibleHint = visible
    }

    override fun notifyDataSetChanged() {
        val newFragments = SparseArrayCompat<Fragment>(fragmentArray.size())
        val newSavedStates = SparseArrayCompat<Fragment.SavedState?>(savedStateArray.size())
        for (i in 0 until fragmentArray.size()) {
            val oldPos = fragmentArray.keyAt(i)
            val f = fragmentArray.valueAt(i)
            val savedState = savedStateArray[oldPos]
            val newPos = getItemPosition(f)
            if (newPos != POSITION_NONE) {
                val pos = if (newPos >= 0) newPos else oldPos
                newFragments.put(pos, f)
                if (savedState != null) newSavedStates.put(pos, savedState)
            }
        }
        fragmentArray = newFragments
        savedStateArray = newSavedStates
        super.notifyDataSetChanged()
    }

    fun getFragment(position: Int): Fragment? {
        return fragmentArray[position]
    }
}